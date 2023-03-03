package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetreiveIdentityJob;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsWaitForIdentityJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 *
 * Database holding received trusted Introductions.
 * We are consciously trying to have the sending Introduction ephemeral since we want to maximize privacy,
 * (think an Informant that forwards someone to a Journalist, you don't want that information hanging around)
 *
 * This implementation currently does not support multidevice.
 *
 */
public class TrustedIntroductionsDatabase extends DatabaseTable {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionsDatabase.class));

  public static final String TABLE_NAME = "trusted_introductions";

  private static final String ID                      = "_id";
  private static final String INTRODUCER_SERVICE_ID   = "introducer_service_id";
  private static final String INTRODUCEE_SERVICE_ID          = "introducee_service_id";
  private static final String INTRODUCEE_PUBLIC_IDENTITY_KEY = "introducee_identity_key"; // The one contained in the Introduction
  private static final String INTRODUCEE_NAME                = "introducee_name"; // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
  private static final String INTRODUCEE_NUMBER     = "introducee_number"; // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
  private static final String PREDICTED_FINGERPRINT = "predicted_fingerprint";
  private static final String TIMESTAMP             = "timestamp";
  private static final String STATE                          = "state";
  public static final long UNKNOWN_INTRODUCEE_RECIPIENT_ID = -1; //TODO: need to search through database for serviceID when new recipient is added in order to initialize.

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
      INTRODUCER_SERVICE_ID + " INTEGER NOT NULL, " +
      INTRODUCEE_SERVICE_ID + " TEXT NOT NULL, " +
      INTRODUCEE_PUBLIC_IDENTITY_KEY + " TEXT NOT NULL, " +
      INTRODUCEE_NAME + " TEXT NOT NULL, " +
      INTRODUCEE_NUMBER + " TEXT NOT NULL, " +
      PREDICTED_FINGERPRINT + " TEXT NOT NULL, " +
      TIMESTAMP + " INTEGER NOT NULL, " +
      STATE + " INTEGER NOT NULL);";

  private static final String CLEAR_TABLE = "DELETE FROM " + TABLE_NAME + ";";

  @VisibleForTesting
  public void clearTable(){
    // Debugging
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    int res = db.delete(TABLE_NAME, "", new String[]{});
  }

  private static final String[] TI_ALL_PROJECTION = new String[]{
      ID,
      INTRODUCER_SERVICE_ID,
      INTRODUCEE_SERVICE_ID,
      INTRODUCEE_PUBLIC_IDENTITY_KEY,
      INTRODUCEE_NAME,
      INTRODUCEE_NUMBER,
      PREDICTED_FINGERPRINT,
      TIMESTAMP,
      STATE
  };


  /**
   * An Introduction can either be waiting for a decision from the user (PENDING),
   * been accepted or rejected by the user, or conflict with the Identity Key that the
   * Signal server provided for this recipient. If the identity key of the introducee
   * changes, the entries become stale.
   * => For now, leaving STALE. In fact, I need 4 Stale states if I want to be able to go back to a non-stale state from there,
   * see comment between @see clearIntroducer and @see setState, or FSM drawn on the 09.08.2022.
   */
  public enum State {
    PENDING, ACCEPTED, REJECTED, CONFLICTING, STALE_PENDING, STALE_ACCEPTED, STALE_REJECTED, STALE_CONFLICTING;

    public int toInt() {
      switch (this) {
        case PENDING:
          return 0;
        case ACCEPTED:
          return 1;
        case REJECTED:
          return 2;
        case CONFLICTING:
          return 3;
        case STALE_PENDING:
          return 4;
        case STALE_ACCEPTED:
          return 5;
        case STALE_REJECTED:
          return 6;
        case STALE_CONFLICTING:
          return 7;
        default:
          throw new AssertionError("No such state " + this);
      }
    }

    public static State forState(int state) {
      switch (state) {
        case 0:
          return PENDING;
        case 1:
          return ACCEPTED;
        case 2:
          return REJECTED;
        case 3:
          return CONFLICTING;
        case 4:
          return STALE_PENDING;
        case 5:
          return STALE_ACCEPTED;
        case 6:
          return STALE_REJECTED;
        case 7:
          return STALE_CONFLICTING;
        default:
          throw new AssertionError("No such state: " + state);
      }
    }

    public boolean isStale(){
      switch (this) {
        case PENDING:
        case ACCEPTED:
        case REJECTED:
        case CONFLICTING:
          return false;
        case STALE_PENDING:
        case STALE_ACCEPTED:
        case STALE_REJECTED:
        case STALE_CONFLICTING:
          return true;
        default:
          throw new AssertionError("No such state: " + this);
      }
    }

    // Convenience for prototype, would obv. not fly in prod.
    public String toVerbIng(){
      switch(this){
        case PENDING:
          throw new AssertionError("Starting state cannot be reached: " + this);
        case ACCEPTED:
          return "accepting";
        case REJECTED:
          return "rejecting";
        case CONFLICTING:
        case STALE_PENDING:
        case STALE_ACCEPTED:
        case STALE_REJECTED:
        case STALE_CONFLICTING:
          throw new AssertionError("No user action can reach " + this);
        default:
          throw new AssertionError("No such state: " + this);
      }
    }
  }

  public TrustedIntroductionsDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  /**
   * id not yet known, state either pending or conflicting
   * @param state
   * @param introducerServiceId
   * @param serviceId
   * @param name
   * @param number
   * @param identityKey
   * @param predictedFingerprint
   * @param timestamp
   * @return populated content values ready for insertion
   */
  private @NonNull ContentValues buildContentValuesForInsert(@NonNull State state,
                                                             @NonNull String introducerServiceId,
                                                             @NonNull String serviceId,
                                                             @NonNull String name,
                                                             @NonNull String number,
                                                             @NonNull String identityKey,
                                                             @NonNull String predictedFingerprint,
                                                             @NonNull Long timestamp){
    Preconditions.checkArgument(state == State.PENDING || state == State.CONFLICTING);
    ContentValues cv = new ContentValues();
    cv.put(STATE, state.toInt());
    cv.put(INTRODUCER_SERVICE_ID, introducerServiceId);
    cv.put(INTRODUCEE_SERVICE_ID, serviceId);
    cv.put(INTRODUCEE_NAME, name);
    cv.put(INTRODUCEE_NUMBER, number);
    cv.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, identityKey);
    cv.put(PREDICTED_FINGERPRINT, predictedFingerprint);
    cv.put(TIMESTAMP, timestamp);
    return cv;
  }

  /**
   * Used to update a database entry. Pass all the data that should stay the same and change what needs to be updated.
   * @return Content Values for the updated entry
   */
  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull Long introductionId,
                                                    @NonNull State state,
                                                    @NonNull String introducerServiceId,
                                                    @NonNull String serviceId,
                                                    @NonNull String name,
                                                    @NonNull String number,
                                                    @NonNull String identityKey,
                                                    @NonNull String predictedFingerprint,
                                                    @NonNull Long timestamp){
    ContentValues cv = new ContentValues();
    cv.put(ID, introductionId);
    cv.put(STATE, state.toInt());
    cv.put(INTRODUCER_SERVICE_ID, introducerServiceId);
    cv.put(INTRODUCEE_SERVICE_ID, serviceId);
    cv.put(INTRODUCEE_NAME, name);
    cv.put(INTRODUCEE_NUMBER, number);
    cv.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, identityKey);
    cv.put(PREDICTED_FINGERPRINT, predictedFingerprint);
    cv.put(TIMESTAMP, timestamp);
    return cv;
  }

  /**
   * @param c a cursor pointing to a fully populated query result in the database.
   * @param timestamp the new timestamp to insert.
   */
  @SuppressLint("Range") private @NonNull ContentValues buildContentValuesForTimestampUpdate(Cursor c, long timestamp){
    return buildContentValuesForUpdate(c.getString(c.getColumnIndex(ID)),
                                       c.getString(c.getColumnIndex(STATE)),
                                       c.getString(c.getColumnIndex(INTRODUCER_SERVICE_ID)),
                                       c.getString(c.getColumnIndex(INTRODUCEE_SERVICE_ID)),
                                       c.getString(c.getColumnIndex(INTRODUCEE_NAME)),
                                       c.getString(c.getColumnIndex(INTRODUCEE_NUMBER)),
                                       c.getString(c.getColumnIndex(INTRODUCEE_PUBLIC_IDENTITY_KEY)),
                                       c.getString(c.getColumnIndex(PREDICTED_FINGERPRINT)),
                                       String.valueOf(timestamp));
  }

  /**
   * Convenience function when changing state of an introduction
   * @param introduction the introduction to change the state of
   * @param s new state
   * @return Correctly populated ContentValues
   */
  @SuppressLint("Range") private @NonNull ContentValues buildContentValuesForStateUpdate(TI_Data introduction, State s){
    ContentValues values = buildContentValuesForUpdate(introduction);
    values.remove(STATE);
    values.put(STATE, s.toInt());
    return values;
  }


  /**
   * Meant for values pulled directly from the Database through a Query.
   * PRE: None of the Strings may be empty or Null.
   *
   * @param introductionId Expected to represent a Long > 0.
   * @param state Expected to represent an Int between 0 and 7 (inclusive).
   * @param introducerServiceId
   * @param introduceeServiceId
   * @param name
   * @param number
   * @param identityKey
   * @param predictedFingerprint
   * @param timestamp Expected to represent a Long.
   * @return Propperly populated content values, NumberFormatException/AssertionError if a value was invalid.
   */
  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull String introductionId,
                                                             @NonNull String state,
                                                             @NonNull String introducerServiceId,
                                                             @NonNull String introduceeServiceId,
                                                             @NonNull String name,
                                                             @NonNull String number,
                                                             @NonNull String identityKey,
                                                             @NonNull String predictedFingerprint,
                                                             @NonNull String timestamp) throws NumberFormatException{
    Preconditions.checkArgument(!introductionId.isEmpty() &&
                                !state.isEmpty() &&
                                !introducerServiceId.isEmpty() &&
                                !introduceeServiceId.isEmpty() &&
                                !name.isEmpty() &&
                                !number.isEmpty() &&
                                !identityKey.isEmpty() &&
                                !predictedFingerprint.isEmpty() &&
                                !timestamp.isEmpty());
    long introId = Long.parseLong(introductionId);
    Preconditions.checkArgument(introId > 0);
    int s = Integer.parseInt(state);
    Preconditions.checkArgument(s >= 0 && s <= 7);
    long timestampLong = Long.parseLong(timestamp);
    Preconditions.checkArgument(timestampLong > 0);
    return buildContentValuesForUpdate(introId,
                                       State.forState(s),
                                       introducerServiceId,
                                       introduceeServiceId,
                                       name,
                                       number,
                                       identityKey,
                                       predictedFingerprint,
                                       timestampLong);
  }

  /**
   *
   * @param introduction PRE: none of it's fields may be null.
   * @return A populated contentValues object, to use for updates.
   */
  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull TI_Data introduction){
    Preconditions.checkNotNull(introduction.getId());
    Preconditions.checkNotNull(introduction.getState());
    Preconditions.checkNotNull(introduction.getIntroducerServiceId());
    Preconditions.checkNotNull(introduction.getPredictedSecurityNumber());
    return buildContentValuesForUpdate(introduction.getId(),
                                       introduction.getState(),
                                       introduction.getIntroducerServiceId(),
                                       introduction.getIntroduceeServiceId(),
                                       introduction.getIntroduceeName(),
                                       introduction.getIntroduceeNumber(),
                                       introduction.getIntroduceeIdentityKey(),
                                       introduction.getPredictedSecurityNumber(),
                                       introduction.getTimestamp());
  }

  /**
   *
   * @param introduction PRE: none of it's fields may be null, state != stale.
   * @return A populated contentValues object, to use when turning introductions stale.
   */
  private @NonNull ContentValues buildContentValuesForStale(@NonNull TI_Data introduction){
    Preconditions.checkNotNull(introduction.getId());
    Preconditions.checkNotNull(introduction.getState());
    Preconditions.checkNotNull(introduction.getIntroducerServiceId());
    Preconditions.checkNotNull(introduction.getPredictedSecurityNumber());
    Preconditions.checkArgument(!introduction.getState().isStale());
    State newState;
    // Find stale state
    switch(introduction.getState()){
      case PENDING:
        newState = State.STALE_PENDING;
        break;
      case ACCEPTED:
        newState = State.STALE_ACCEPTED;
        break;
      case REJECTED:
        newState = State.STALE_REJECTED;
        break;
      case CONFLICTING:
        newState = State.STALE_CONFLICTING;
      default:
          throw new AssertionError();
    }

    return buildContentValuesForUpdate(introduction.getId(),
                                       newState,
                                       introduction.getIntroducerServiceId(),
                                       introduction.getIntroduceeServiceId(),
                                       introduction.getIntroduceeName(),
                                       introduction.getIntroduceeNumber(),
                                       introduction.getIntroduceeIdentityKey(),
                                       introduction.getPredictedSecurityNumber(),
                                       introduction.getTimestamp());
  }


  /**
   *
   * @return -1 -> conflict occured on insert, 0 -> Profile Fetch Job started, else id of introduction.
   */
  @SuppressLint("Range")
  @WorkerThread
  public long incomingIntroduction(@NonNull TI_Data data){

    // Fetch Data out of database where everything is identical but timestamp & maybe state.
    StringBuilder selectionBuilder = new StringBuilder();
    selectionBuilder.append(String.format("%s=?", INTRODUCER_SERVICE_ID)); // if ID was purged, duplicate detection no longer possible // TODO: issue for, e.g., count if pure distance-1 case (future problem)
    String andAppend = " AND %s=?";
    // does not work iff the recipient to be introduced does not yet have an ID and then gets added.
    //selectionBuilder.append(String.format(andAppend, INTRODUCEE_RECIPIENT_ID));
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_SERVICE_ID));
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_PUBLIC_IDENTITY_KEY));

    // TODO: if this works well, use in other dbs where you build queries
    String[] args = SqlUtil.buildArgs(data.getIntroducerServiceId(),
                                      data.getIntroduceeServiceId(),
                                      data.getIntroduceeIdentityKey());

    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selectionBuilder.toString(), args, null, null, null);
    if (c.getCount() == 1){
      c.moveToFirst();
      long result = writeableDatabase.update(TABLE_NAME, buildContentValuesForTimestampUpdate(c, data.getTimestamp()), ID + " = ?", SqlUtil.buildArgs(c.getInt(c.getColumnIndex(ID))));
      Log.i(TAG, "Updated timestamp of introduction " + result + " to: " + TI_Utils.INTRODUCTION_DATE_PATTERN.format(data.getTimestamp()));
      c.close();
      return result;
    }
    if(c.getCount() != 0)
      throw new AssertionError(TAG + " Either there is one entry or none, nothing else valid.");
    c.close();

    Optional<RecipientId> introduceeOpt =  SignalDatabase.recipients().getByServiceId(ServiceId.parseOrThrow(data.getIntroduceeServiceId()));
    RecipientId introduceeId = introduceeOpt.orElse(null);
    if(introduceeId == null){
      ApplicationDependencies.getJobManager().add(new TrustedIntroductionsRetreiveIdentityJob(data));
      Log.i(TAG, "Unknown recipient, deferred insertion of Introduction into database for: " + data.getIntroduceeName());
      // This is expected and not an error.
      return 0;
    } else {
      // The recipient already exists and must not be fetched
      ServiceId introduceeServiceId;
      try {
        introduceeServiceId = RecipientUtil.getOrFetchServiceId(context, Recipient.resolved(introduceeId));
      } catch (IOException e) {
        Log.e(TAG, "Failed to fetch service ID for: " + data.getIntroduceeName() + ", with RecipientId: " + introduceeId);
        return -1;
      }
      try {
        return insertIntroductionCallback(data, TI_Utils.getEncodedIdentityKey(introduceeId), introduceeServiceId.toString());
      } catch (TI_Utils.TI_MissingIdentityException e){
        e.printStackTrace();
      }
      // if it still didn't work, this is a recipient without an identity record (no messages exchanged yet)
      // schedule retreive identity job as above
      ApplicationDependencies.getJobManager().add(new TrustedIntroductionsRetreiveIdentityJob(data));
      return 0;
    }
  }

  // Callback for profile retreive Identity job
  // TODO: annoying that this needs to be public. Should be private and just passed as function pointer..
  // But java is annoying when it comes to function serialization so I won't do that for now
  // Only meant to be called by Job & @incomingIntroduction
  /**
   * @param data the introduction to be inserted, predicted security nr. may not be null
   * @param base64KeyResult the public key fetched from the server for the introducee
   * @param aciResult the aci fetched from the server for the introducee
   * @return id of introduction or -1 if fail.
   */
  @WorkerThread
  public long insertIntroductionCallback(TI_Data data, String base64KeyResult, String aciResult){
    Preconditions.checkArgument(aciResult != null && data != null &&
                                aciResult.equals(data.getIntroduceeServiceId()));
    Preconditions.checkArgument(data.getPredictedSecurityNumber() != null);

    ContentValues values = buildContentValuesForInsert(base64KeyResult.equals(data.getIntroduceeIdentityKey()) ? State.PENDING : State.CONFLICTING,
                                                       data.getIntroducerServiceId(),
                                                       data.getIntroduceeServiceId(),
                                                       data.getIntroduceeName(),
                                                       data.getIntroduceeNumber(),
                                                       data.getIntroduceeIdentityKey(),
                                                       data.getPredictedSecurityNumber(),
                                                       data.getTimestamp());
    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    long id = writeableDatabase.insert(TABLE_NAME, null, values);
    Log.i(TAG, "Inserted new introduction for: " + data.getIntroduceeName() + ", with id: " + id);
    return id;
  }

  /**
   * @param introduction the introduction to be modified.
   * @param newState the new state for the introduction.
   * @param logMessage what should be written on the logcat for the modification.
   * @return  if the insertion succeeded or failed
   * PRE: State is not Pending or Conflicting, introductionId != null
   * TODO: currently can't distinguish between total failure or having to wait for a profilefetch.
   * => would only be necessary if we bubble this state up to the user... We could have a Toast stating that the verification state may take a while to update
   * if recipient was not yet in the database.
   */
  @WorkerThread
  private boolean setState(@NonNull TI_Data introduction, @NonNull State newState, @NonNull String logMessage) {
    // We are setting conflicting and pending states directly when the introduction comes in. Should not change afterwards.
    Preconditions.checkArgument(newState != State.PENDING && newState != State.CONFLICTING);
    Preconditions.checkArgument(introduction.getId() != null);

    // Recipient not yet in database, must insert it first and update the introducee ID
    if (TI_Utils.getRecipientIdOrUnknown(introduction.getIntroduceeServiceId()).equals(RecipientId.UNKNOWN)){
      RecipientTable db = SignalDatabase.recipients();
      db.getAndPossiblyMerge(ServiceId.parseOrThrow(introduction.getIntroduceeServiceId()), introduction.getIntroduceeNumber());
      ApplicationDependencies.getJobManager().add(new TrustedIntroductionsWaitForIdentityJob(introduction, newState, logMessage));
      return false; // TODO: simply postponed, do we need ternary state here?
    }
    return setStateCallback(introduction, newState, logMessage);
  }

  /**
   * Callback for modifying introductions state,
   * PRE: assumes that recipient equivalent to introducee exists in the recipient table as well as their identity in the identity table.
   * @param introduction the introduction to be modified.
   * @param newState the new state for the introduction.
   * @param logMessage what should be written on the logcat for the modification.
   * @return  if the insertion succeeded or failed
   * TODO: currently can't distinguish between total failure or having to wait for a profilefetch. (Important?)
   * => would only be necessary if we bubble this state up to the user... We could have a Toast stating that the verification state may take a while to update
   * if recipient was not yet in the database.
   * PRE: Introducee must be present in the RecipientTable & have a verificationStatus in the IdentityTable
   */
  @WorkerThread
  public boolean setStateCallback(@NonNull TI_Data introduction, @NonNull State newState, @NonNull String logMessage){
    RecipientId introduceeID = TI_Utils.getRecipientIdOrUnknown(introduction.getIntroduceeServiceId());
    try (Cursor rdc = fetchRecipientDBCursor(introduceeID)) {
      if (rdc.getCount() <= 0) {
        // Programming error in setState codepath if this occurs.
        throw new AssertionError("Unexpected missing recipient " + introduction.getIntroduceeName() + " in database while trying to change introduction state...");
      }
    }

    IdentityTable.VerifiedStatus previousIntroduceeVerification = SignalDatabase.identities().getVerifiedStatus(introduceeID);
    if (previousIntroduceeVerification == null){
      throw new AssertionError("Unexpected missing verification status for " + introduction.getIntroduceeName());
    }

    // If the state turned stale we only change the introduction, not the verification status. Changing security nr. already has a hook for that.
    // TODO: Potential Race condition, mutex?
    if(!newState.isStale()){
      modifyIntroduceeVerification(introduction.getIntroduceeServiceId(),
                                   previousIntroduceeVerification,
                                   newState,
                                   String.format("Updated %s's verification state to: %s", introduction.getIntroduceeName(), newState));
    }

    // Modify introduction
    ContentValues newValues = buildContentValuesForStateUpdate(introduction, newState);
    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    long result = writeableDatabase.update(TABLE_NAME, newValues, ID + " = ?", SqlUtil.buildArgs(introduction.getId()));

    if ( result > 0 ){
      // Log message on success
      Log.i(TAG, logMessage);
      return true;
    }
    Log.e(TAG, "State modification of introduction: " + introduction.getId() + " failed!");
    return false;
  }

  /**
   * FSMs for verification status implemented here.
   * PRE: introducee exists in recipient and identity table
   * @param introduceeServiceId The service ID of the recipient whose verification status may change
   * @param previousIntroduceeVerification the previous verification status of the introducee
   * @param newState PRE: !STALE (security number changes are handled in a seperate codepath)
   * @param logmessage what to print to logcat iff status was modified
   */
  @WorkerThread
  private void modifyIntroduceeVerification(@NonNull String introduceeServiceId, @NonNull IdentityTable.VerifiedStatus previousIntroduceeVerification, @NonNull State newState, @NonNull String logmessage){
    Preconditions.checkArgument(!newState.isStale());
    // Initialize with what it was
    IdentityTable.VerifiedStatus newIntroduceeVerification = previousIntroduceeVerification;
    switch (previousIntroduceeVerification){
      case DEFAULT:
      case UNVERIFIED:
      case MANUALLY_VERIFIED:
        if (newState == State.ACCEPTED){
          newIntroduceeVerification = IdentityTable.VerifiedStatus.INTRODUCED;
        }
        break;
      case DUPLEX_VERIFIED:
        if (newState == State.REJECTED){
          // Stay "duplex verified" iff only more than 1 accepted introduction for this contact exist else "directly verified"
          newIntroduceeVerification = multipleAcceptedIntroductions(introduceeServiceId) ? IdentityTable.VerifiedStatus.DUPLEX_VERIFIED :
                                      IdentityTable.VerifiedStatus.DIRECTLY_VERIFIED;
        }
        break;
      case DIRECTLY_VERIFIED:
        if (newState == State.ACCEPTED){
          newIntroduceeVerification = IdentityTable.VerifiedStatus.DUPLEX_VERIFIED;
        }
        break;
      case INTRODUCED:
        if (newState == State.REJECTED){
          // Stay "introduced" iff more than 1 accepted introduction for this contact exist else "unverified"
          newIntroduceeVerification = multipleAcceptedIntroductions(introduceeServiceId) ? IdentityTable.VerifiedStatus.INTRODUCED :
                                      IdentityTable.VerifiedStatus.UNVERIFIED;
        }
        break;
      default:
        throw new AssertionError("Invalid verification status: " + previousIntroduceeVerification.toInt());
    }
    if (newIntroduceeVerification != previousIntroduceeVerification) {
      // Something changed
      RecipientId rId = TI_Utils.getRecipientIdOrUnknown(introduceeServiceId);
      try {
        TI_Utils.updateContactsVerifiedStatus(rId, TI_Utils.getIdentityKey(rId), newIntroduceeVerification);
      } catch (TI_Utils.TI_MissingIdentityException e){
        e.printStackTrace();
        throw new AssertionError(TAG + " Precondidion violated, recipient " + rId + "'s verification status cannot be updated!");
      }
      Log.i(TAG, logmessage);
    }
  }

  /**
   * PRE: at least one accepted introduction for introduceeID
   * @param introduceeServiceId The serviceID of the recipient whose verification status may change
   */
  @WorkerThread
  private boolean multipleAcceptedIntroductions(String introduceeServiceId){
    final String selection = String.format("%s=?", INTRODUCEE_SERVICE_ID)
                                    + String.format(" AND %s=?", STATE);

    String[] args = SqlUtil.buildArgs(introduceeServiceId,
                                      State.ACCEPTED.toInt());

    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selection, args, null, null, null);

    // check precondition
    Preconditions.checkArgument(c.getCount() > 0);

    return c.getCount() > 1;
   }



  /**
   * Expects the introducee to have been fetched.
   * Expects introduction to already be present in database
   * @param introduction PRE: introduction.id cannot be null
   * @return true if success, false otherwise
   */
  @WorkerThread
  public boolean acceptIntroduction(TI_Data introduction){
    Preconditions.checkArgument(introduction.getId() != null);
    return setState(introduction, State.ACCEPTED,"Accepted introduction for: " + introduction.getIntroduceeName());
  }

  /**
   * Expects the introducee to have been fetched.
   * Expects introduction to already be present in database.
   * @param introduction PRE: introduction.id cannot be null
   * @return true if success, false otherwise
   */
  @WorkerThread
  public boolean rejectIntroduction(TI_Data introduction){
    Preconditions.checkArgument(introduction.getId() != null);
    return setState(introduction, State.REJECTED,"Rejected introduction for: " + introduction.getIntroduceeName());
  }

  @WorkerThread
  /**
   * Fetches Introduction data by Introducer. Pass null to get all the data.
   * @return IntroductionReader which can be used to iterate through the rows.
   */
  public IntroductionReader getIntroductions(@Nullable String introducerServiceId){
    String query;
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    if(introducerServiceId == null){
      query = "SELECT  * FROM " + TABLE_NAME;
      return new IntroductionReader(db.rawQuery(query, null));
    } else {
      // query only the Introductions made by introducerServiceId
      query = INTRODUCER_SERVICE_ID + " = ?";
      String[] arg = SqlUtil.buildArgs(introducerServiceId);
      return new IntroductionReader(db.query(TABLE_NAME, TI_ALL_PROJECTION, query, arg, null, null, null));
    }
  }

 @WorkerThread
 /**
  * PRE: introductionId may not be null, IntroducerServiceId must be null
  * Updates the entry in the database accordingly.
  * Effectively "forget" who did this introduction.
  *
  * @return true if success, false otherwise
  */
 public boolean clearIntroducer(TI_Data introduction){
   Preconditions.checkArgument(introduction.getIntroducerServiceId() == null);
   Preconditions.checkArgument(introduction.getId() != null);
   SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
   String query = ID + " = ?";
   String[] args = SqlUtil.buildArgs(introduction.getId());

   ContentValues values = buildContentValuesForUpdate(introduction);

   int update = database.update(TABLE_NAME, values, query, args);
   Log.i(TAG, "Forgot introducer for introduction with id: " + introduction.getId());
   if( update > 0 ){
     // TODO: For multidevice, syncing would be handled here
     return true;
   }
   return false;
 }

  @WorkerThread
  /**
   * Turns all introductions for the introducee named by id stale.
   * @param serviceId the introducee whose security nr. changed.
   * @return true if all updates succeeded, false otherwise
   */
  public boolean turnAllIntroductionsStale(String serviceId){
     boolean updateSucceeded = true;
     Preconditions.checkArgument(!TI_Utils.getRecipientIdOrUnknown(serviceId).equals(RecipientId.UNKNOWN));
     String query = INTRODUCEE_SERVICE_ID + " = ?";
     String[] args = SqlUtil.buildArgs(serviceId);

     SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
     Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, query, args, null, null, null);
     IntroductionReader reader = new IntroductionReader(c);
     TI_Data introduction;
     while((introduction = reader.getNext()) != null){
       ContentValues cv = buildContentValuesForStale(introduction);
       int res = writeableDatabase.update(TABLE_NAME, cv, ID + " = ?", SqlUtil.buildArgs(introduction.getId()));
       if (res < 0){
         Log.e(TAG, "Introduction " + introduction.getId() + " for " + introduction.getIntroduceeName() + " with state " + introduction.getState().toString() + " could not be turned stale!");
         updateSucceeded = false;
       } else {
         Log.i(TAG, "Introduction " + introduction.getId() + " for " + introduction.getIntroduceeName() + " with state " + introduction.getState().toString() + " was turned stale!");
         // TODO: For multidevice, syncing would be handled here
       }
     }
     return updateSucceeded;
  }

  @WorkerThread
  /**
   * PRE: introductionId must be > 0
   * Deletes an introduction out of the database.
   *
   * @return true if success, false otherwise
   */
  public boolean deleteIntroduction(long introductionId){
    Preconditions.checkArgument(introductionId > 0);
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    String query = ID + " = ?";
    String[] args = SqlUtil.buildArgs(introductionId);

    int count = database.delete(TABLE_NAME, query, args);

    if(count == 1){
      Log.i(TAG, String.format("Deleted introduction with id: %d from the database.", introductionId));
      return true;
    } else if(count > 1){
      // matching with id, which must be unique
      throw new AssertionError();
    } else {
      return false;
    }
  }

  /*
    General Utilities
   */

  /**
   * @param introduceeId Which recipient to look for in the recipient database
   * @return Cursor pointing to query result.
   */
  @WorkerThread
  private Cursor fetchRecipientDBCursor(RecipientId introduceeId){
    RecipientTable rdb = SignalDatabase.recipients();
    // TODO: Simplify if you see that you finally never query this cursor with more than 1 recipient...
    Set<RecipientId> s = new HashSet<>();
    s.add(introduceeId);
    return rdb.getCursorForSendingTI(s);
  }

  // TODO: For now I'm keeping the history of introductions, but only showing the newest valid one. Might be beneficial to have them eventually go away if they become stale?
  // Maybe keep around one level of previous introduction? Apparently there is a situation in the app, whereas if someone does not follow the instructions of setting up
  // their new device exactly, they may be set as unverified, while they eventually still end up with the same identity key (TODO: find Github issue or discussion?)
  // For this case, having a record of stale introductions could be used to restore the verification state without having to reverify.

  // TODO: all state transition methods can be public => FSM Logic adhered to this way.

  public static class IntroductionReader implements Closeable{
    private final Cursor cursor;

    // TODO: Make it slightly more flexible in terms of which data you pass around.
    // A cursor pointing to the result of a query using TI_DATA_PROJECTION
    IntroductionReader(Cursor c){
      cursor = c;
      cursor.moveToFirst();
    }

    // This is now has a guarantee w.r.t. calling the constructor
    @SuppressLint("Range")
    private @Nullable TI_Data getCurrent(){
      if(cursor.isAfterLast() || cursor.isBeforeFirst()){
        return null;
      }
      Long introductionId = cursor.getLong(cursor.getColumnIndex(ID));
      int s = cursor.getInt(cursor.getColumnIndex(STATE));
      State       state = State.forState(s);
      String   introducerServiceId = (cursor.getString(cursor.getColumnIndex(INTRODUCER_SERVICE_ID)));
      String introduceeServiceId = (cursor.getString(cursor.getColumnIndex(INTRODUCEE_SERVICE_ID)));
      // Do I need to hit the Recipient Database to check the name?
      // TODO: Name changes in introducees should get reflected in database (needs to happen when the name changes, not on query)
      String introduceeName = cursor.getString(cursor.getColumnIndex(INTRODUCEE_NAME));
      String introduceeNumber = cursor.getString(cursor.getColumnIndex(INTRODUCEE_NUMBER));
      String introduceeIdentityKey = cursor.getString(cursor.getColumnIndex(INTRODUCEE_PUBLIC_IDENTITY_KEY));
      String           securityNr = cursor.getString(cursor.getColumnIndex(PREDICTED_FINGERPRINT));
      long timestamp = cursor.getLong(cursor.getColumnIndex(TIMESTAMP));
      return new TI_Data(introductionId, state, introducerServiceId, introduceeServiceId, introduceeName, introduceeNumber, introduceeIdentityKey, securityNr, timestamp);
    }

    /**
     * advances one row and returns it, null if empty, or cursor after last.
     */
    public @Nullable TI_Data getNext(){
      TI_Data current = getCurrent();
      cursor.moveToNext();
      return current;
    }

    public boolean hasNext(){
      return !cursor.isAfterLast();
    }

    @Override public void close() throws IOException {
      cursor.close();
    }
  }

}
