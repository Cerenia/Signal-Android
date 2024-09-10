package org.thoughtcrime.securesms.trustedIntroductions.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SQLiteDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue;
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue;
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
import java.util.Map;
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
public class TI_Database extends DatabaseTable implements TI_DatabaseGlue {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TI_Database.class));

  private static TI_DatabaseGlue instance = null;

  public static void setInstance(TI_DatabaseGlue inst) throws Exception {
    if (instance != null){
      throw new Exception("Attempted to reassign Singleton instance of TI_Database");
    }
    instance = inst;
  }

  public static TI_DatabaseGlue getInstance() {
    if (instance == null){
      throw new AssertionError("Attempted to fetch Singleton TI_Database before initializing it.");
    }
    return instance;
  }

  public static final String TABLE_NAME = "trusted_introductions";

  private static final String ID                      = "_id";
  public static final String INTRODUCER_SERVICE_ID   = "introducer_service_id";
  private static final String INTRODUCEE_SERVICE_ID          = "introducee_service_id";
  private static final String INTRODUCEE_PUBLIC_IDENTITY_KEY = "introducee_identity_key"; // The one contained in the Introduction
  private static final String INTRODUCEE_NAME                = "introducee_name"; // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
  private static final String INTRODUCEE_NUMBER     = "introducee_number"; // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
  private static final String PREDICTED_FINGERPRINT = "predicted_fingerprint";
  private static final String TIMESTAMP             = "timestamp";
  private static final String STATE                          = "state";
  public static final long UNKNOWN_INTRODUCEE_RECIPIENT_ID = -1; //TODO: need to search through database for serviceID when new recipient is added in order to initialize.
  public static final String UNKNOWN_INTRODUCER_SERVICE_ID = "-1";

  // Service ID was an Integer mistakenly + had a nonnull constraint, ignore and execute correct statement instead
  public static final String PREVIOUS_PARTIAL_CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                     INTRODUCER_SERVICE_ID;

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
      INTRODUCER_SERVICE_ID + " TEXT, " +
      INTRODUCEE_SERVICE_ID + " TEXT NOT NULL, " +
      INTRODUCEE_PUBLIC_IDENTITY_KEY + " TEXT NOT NULL, " +
      INTRODUCEE_NAME + " TEXT NOT NULL, " +
      INTRODUCEE_NUMBER + " TEXT, " +
      PREDICTED_FINGERPRINT + " TEXT NOT NULL, " +
      TIMESTAMP + " INTEGER NOT NULL, " +
      STATE + " INTEGER NOT NULL);";

  private static final String CLEAR_TABLE = "DELETE FROM " + TABLE_NAME + ";";

  @VisibleForTesting
  public void clearTable(){
    // Debugging
    SQLiteDatabase db  = databaseHelper.getSignalWritableDatabase();
    int            res = db.delete(TABLE_NAME, "", new String[]{});
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
   * All states in the FSM for Introductions.
   */
  public enum State {
    PENDING, ACCEPTED, REJECTED, PENDING_CONFLICTING, ACCEPTED_CONFLICTING, REJECTED_CONFLICTING, STALE_PENDING, STALE_ACCEPTED,
    STALE_REJECTED, STALE_PENDING_CONFLICTING, STALE_ACCEPTED_CONFLICTING, STALE_REJECTED_CONFLICTING;

    public int toInt() {
      switch (this) {
        case PENDING:
          return 0;
        case ACCEPTED:
          return 1;
        case REJECTED:
          return 2;
        case PENDING_CONFLICTING:
          return 3;
        case ACCEPTED_CONFLICTING:
          return 4;
        case REJECTED_CONFLICTING:
          return 5;
        case STALE_PENDING:
          return 6;
        case STALE_ACCEPTED:
          return 7;
        case STALE_REJECTED:
          return 8;
        case STALE_PENDING_CONFLICTING:
          return 9;
        case STALE_ACCEPTED_CONFLICTING:
          return 10;
        case STALE_REJECTED_CONFLICTING:
          return 11;
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
          return PENDING_CONFLICTING;
        case 4:
          return ACCEPTED_CONFLICTING;
        case 5:
          return REJECTED_CONFLICTING;
        case 6:
          return STALE_PENDING;
        case 7:
          return STALE_ACCEPTED;
        case 8:
          return STALE_REJECTED;
        case 9:
          return STALE_PENDING_CONFLICTING;
        case 10:
          return STALE_ACCEPTED_CONFLICTING;
        case 11:
          return STALE_REJECTED_CONFLICTING;
        default:
          throw new AssertionError("No such state: " + state);
      }
    }

    public boolean isStale(){
      switch (this) {
        case PENDING:
        case ACCEPTED:
        case REJECTED:
        case PENDING_CONFLICTING:
        case ACCEPTED_CONFLICTING:
        case REJECTED_CONFLICTING:
          return false;
        case STALE_PENDING:
        case STALE_ACCEPTED:
        case STALE_REJECTED:
        case STALE_PENDING_CONFLICTING:
        case STALE_ACCEPTED_CONFLICTING:
        case STALE_REJECTED_CONFLICTING:
          return true;
        default:
          throw new AssertionError("No such state: " + this);
      }
    }
  }

  public TI_Database(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }




  /**
   * Used to update a database entry. Pass all the data that should stay the same and change what needs to be updated.
   * @return Content Values for the updated entry
   */
  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull Long introductionId,
                                                    @NonNull State state,
                                                    @Nullable String introducerServiceId,
                                                    @NonNull String serviceId,
                                                    @NonNull String name,
                                                    @Nullable String number,
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
  @SuppressLint("Range") public @NonNull ContentValues buildContentValuesForStateUpdate(TI_Data introduction, State s){
    ContentValues values = buildContentValuesForUpdate(introduction);
    values.remove(STATE);
    values.put(STATE, s.toInt());
    return values;
  }

  @Override public SQLiteDatabase getSignalWritableDatabase() {
    return this.databaseHelper.getSignalWritableDatabase();
  }

  /**
   * id not yet known, state either pending or conflicting
   * @param state
   * @param introducerServiceId
   * @param introduceeServiceId
   * @param introduceeName
   * @param introduceeNumber
   * @param introduceeIdentityKey
   * @param predictedSecurityNumber
   * @param timestamp
   * @return populated content values ready for insertion
   */
  @Override public ContentValues buildContentValuesForInsert(@NonNull State state,
                                                             @NonNull String introducerServiceId,
                                                             @NonNull String introduceeServiceId,
                                                             @NonNull String introduceeName,
                                                             @Nullable String introduceeNumber,
                                                             @NonNull String introduceeIdentityKey,
                                                             @NonNull String predictedSecurityNumber,
                                                             long timestamp) {
    Preconditions.checkArgument(state == State.PENDING || state == State.CONFLICTING);
    ContentValues cv = new ContentValues();
    cv.put(STATE, state.toInt());
    cv.put(INTRODUCER_SERVICE_ID, introducerServiceId);
    cv.put(INTRODUCEE_SERVICE_ID, introduceeServiceId);
    cv.put(INTRODUCEE_NAME, introduceeName);
    cv.put(INTRODUCEE_NUMBER, introduceeNumber);
    cv.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, introduceeIdentityKey);
    cv.put(PREDICTED_FINGERPRINT, predictedSecurityNumber);
    cv.put(TIMESTAMP, timestamp);
    return cv;
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
                                                             @Nullable String number,
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
   * @param introduction PRE: none of it's fields may be null, except introducerServiceId (forgotten introducer)
   * @return A populated contentValues object, to use for updates.
   */
  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull TI_Data introduction){
    Preconditions.checkNotNull(introduction.getId());
    Preconditions.checkNotNull(introduction.getState());
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
   * @param introduction PRE: none of it's fields (except nr.) may be null, state != stale.
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
      case PENDING_CONFLICTING:
        newState = State.STALE_PENDING_CONFLICTING;
        break;
      case ACCEPTED_CONFLICTING:
        newState = State.STALE_ACCEPTED_CONFLICTING;
        break;
      case REJECTED_CONFLICTING:
        newState = State.STALE_REJECTED_CONFLICTING;
        break;
      default:
          throw new AssertionError("State: " + introduction.getState() + " was illegal or already stale.");
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

  // TODO: Given a contact that cannot be contacted (hidden, no username/phone nr.) we cannot determine from the pending introduction, if there was a conflict
  // or the thing turned stale in the meantime when a session is initiated. Thus we must turn it stale immediately from whatever state it was in...

  private long insertIntroduction(TI_Data data, State state){
    Preconditions.checkArgument(state == State.PENDING || state == State.PENDING_CONFLICTING);
    TI_DatabaseGlue db = SignalDatabase.tiDatabase();
    ContentValues values = db.buildContentValuesForInsert(state,
                                                          data.getIntroducerServiceId(),
                                                          data.getIntroduceeServiceId(),
                                                          data.getIntroduceeName(),
                                                          data.getIntroduceeNumber(),
                                                          data.getIntroduceeIdentityKey(),
                                                          data.getPredictedSecurityNumber(),
                                                          data.getTimestamp());
    SQLiteDatabase writeableDatabase = db.getSignalWritableDatabase();
    long id = writeableDatabase.insert(TABLE_NAME, null, values);
    Log.i(TAG, "Inserted new introduction for: " + data.getIntroduceeName() + ", with id: " + id);
    return id;
  }

  /**
   * This is the START state of the introduction FSM.
   * Check if there is a detectable conflict (only possible if the service ID maps to a recipient ID)
   * and set the state accordingly for the insert
   *
   * @param data the new introduction to insert.
   * @return insertion id of introduction.
   */
  private long insertKnownNewIntroduction(TI_Data data){
    //TODO: Implement
    /**
     * 1. check if the recipient exists
     * yes: check for conflict
     *    yes: insert pending conflict
     *    no: insert pending
     * no: insert pending
     */


    Optional<RecipientId> introduceeOpt =  SignalDatabase.recipients().getByServiceId(ServiceId.parseOrThrow(data.getIntroduceeServiceId()));
    RecipientId introduceeId = introduceeOpt.orElse(null);
    if(introduceeId != null){
      // The recipient already exists, check if the identity key matches what we already have in the database
      // TODO: implement
      ServiceId introduceeServiceId;
      try {
        introduceeServiceId = RecipientUtil.getOrFetchServiceId(context, Recipient.resolved(introduceeId));
      } catch (IOException e) {
        Log.e(TAG, "Failed to fetch service ID for: " + data.getIntroduceeName() + ", with RecipientId: " + introduceeId);
        return -1;
      }
      TI_Utils.getEncodedIdentityKey(introduceeId);
      /**
       // Do not save identity when you are simply checking for conflict. We do not want persistent data that the user did not consciously decide to add.
       InsertCallback cb = new InsertCallback(data, null, null);
       TrustedIntroductionsRetreiveIdentityJob job = new TrustedIntroductionsRetreiveIdentityJob(data, false, cb);
       ApplicationDependencies.getJobManager().add(job);
       Log.i(TAG, "Unknown recipient, deferred insertion of Introduction into database for: " + data.getIntroduceeName());
       // This is expected and not an error.
       **/
      return 0; // TODO: replace with an insert
    } else {
      // The recipient is known

      try {
        InsertCallback cb = new InsertCallback(data, TI_Utils.getEncodedIdentityKey(introduceeId), introduceeServiceId.toString());
        cb.callback();
        return cb.getResult();
      } catch (TI_Utils.TI_MissingIdentityException e){
        e.printStackTrace();
        // Fetch identity key from infrastructure for conflict detection. The user still has not interacted so identity is not saved.
        ApplicationDependencies.getJobManager().add(new TrustedIntroductionsRetreiveIdentityJob(data, false, new InsertCallback(data, null, null)));
        Log.i(TAG, "Unknown identity, deferred insertion of Introduction into database for: " + data.getIntroduceeName());
        // This is expected and not an error.
        return 0;
      }
    }
    return 0;
  }

  /**
   *
   *  We first check if an introduction with the same introducer service id, introducee service id, and identity key
   *  already exists in the database to avoid duplication.
   *  If we find a duplicate, we simply update the timestamp to the most recent one.
   *  Otherwise the start of the introduction FSM is reached.
   *
   *  @param data the incoming introduction
   * @return insertion id of introduction.
   */
  @SuppressLint("Range")
  @WorkerThread
  @Override
  public long incomingIntroduction(@NonNull TI_Data data){
    // Fetch Data to compare if present
    // TODO: Adapt when we are more clear about what the data will be...
    // TODO: reimplment...
    StringBuilder selectionBuilder = new StringBuilder();
    selectionBuilder.append(String.format("%s=?", INTRODUCER_SERVICE_ID));
    String andAppend = " AND %s=?";
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_SERVICE_ID));
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_PUBLIC_IDENTITY_KEY));

    String[] args = SqlUtil.buildArgs(data.getIntroducerServiceId(),
                                      data.getIntroduceeServiceId(),
                                      data.getIntroduceeIdentityKey());

    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selectionBuilder.toString(), args, null, null, null);
    // We found a matching introduction, we will update it and not insert a new one.
    if (c.getCount() == 1){
      c.moveToFirst();
      long result = writeableDatabase.update(TABLE_NAME, buildContentValuesForTimestampUpdate(c, data.getTimestamp()), ID + " = ?", SqlUtil.buildArgs(c.getInt(c.getColumnIndex(ID))));
      Log.i(TAG, "Updated timestamp of introduction " + result + " to: " + TI_Utils.INTRODUCTION_DATE_PATTERN.format(data.getTimestamp()));
      c.close();
      return result;
    }
    if(c.getCount() != 0)
      throw new AssertionError(TAG + " When checking for existing Introductions, there is one entry or none, nothing else is valid.");
    c.close();
    return insertKnownNewIntroduction(data);
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
    RecipientId recipientId = TI_Utils.getRecipientIdOrUnknown(introduction.getIntroduceeServiceId());
    TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult res  = new TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult(introduction, null, null);
    SetStateCallback.SetStateData                                  data = new SetStateCallback.SetStateData(res, newState, logMessage);
    SetStateCallback                                               cb   = new SetStateCallback(data);
    if (recipientId.equals(RecipientId.UNKNOWN) ||
        !ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(recipientId).isPresent()){
      RecipientTable db = SignalDatabase.recipients();
      db.getAndPossiblyMerge(ServiceId.parseOrThrow(introduction.getIntroduceeServiceId()), introduction.getIntroduceeNumber());
      // Save identity, the user specifically decided to interfere with the introduction (accept/reject) so saving this state is ok.
      Log.d(TAG, "Saving identity for: " + recipientId);
      ApplicationDependencies.getJobManager().add(new TrustedIntroductionsRetreiveIdentityJob(introduction, true, cb));
      return false; // TODO: simply postponed, do we need ternary state here?
    }
    cb.callback();
    return cb.getResult();
  }

  /**
   * FSMs for verification status implemented here.
   * PRE: introducee exists in recipient and identity table
   * @param introduceeServiceId The service ID of the recipient whose verification status may change
   * @param previousIntroduceeVerification the previous verification status of the introducee
   * @param newState PRE: !STALE (security number changes are handled in a seperate codepath)
   * @param logmessage what to print to logcat iff status was modified
   */
  @Override
  @WorkerThread public void modifyIntroduceeVerification(@NonNull String introduceeServiceId, @NonNull TI_IdentityTable.VerifiedStatus previousIntroduceeVerification, @NonNull State newState, @NonNull String logmessage){
    Preconditions.checkArgument(!newState.isStale());
    // Initialize with what it was
    TI_IdentityTable.VerifiedStatus newIntroduceeVerification = previousIntroduceeVerification;
    switch (previousIntroduceeVerification){
      case DEFAULT:
      case UNVERIFIED:
      case MANUALLY_VERIFIED:
        if (newState == State.ACCEPTED){
          newIntroduceeVerification = TI_IdentityTable.VerifiedStatus.INTRODUCED;
        }
        break;
      case DUPLEX_VERIFIED:
        if (newState == State.REJECTED){
          // Stay "duplex verified" iff only more than 1 accepted introduction for this contact exist else "directly verified"
          newIntroduceeVerification = multipleAcceptedIntroductions(introduceeServiceId) ? TI_IdentityTable.VerifiedStatus.DUPLEX_VERIFIED :
                                      TI_IdentityTable.VerifiedStatus.DIRECTLY_VERIFIED;
        }
        break;
      case DIRECTLY_VERIFIED:
        if (newState == State.ACCEPTED){
          newIntroduceeVerification = TI_IdentityTable.VerifiedStatus.DUPLEX_VERIFIED;
        }
        break;
      case INTRODUCED:
        if (newState == State.REJECTED){
          // Stay "introduced" iff more than 1 accepted introduction for this contact exist else "unverified"
          newIntroduceeVerification = multipleAcceptedIntroductions(introduceeServiceId) ? TI_IdentityTable.VerifiedStatus.INTRODUCED :
                                      TI_IdentityTable.VerifiedStatus.UNVERIFIED;
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
        throw new AssertionError(TAG + " Precondition violated, recipient " + rId + "'s verification status cannot be updated!");
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
  @Override
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
  @Override
  public boolean rejectIntroduction(TI_Data introduction){
    Preconditions.checkArgument(introduction.getId() != null);
    return setState(introduction, State.REJECTED,"Rejected introduction for: " + introduction.getIntroduceeName());
  }

  @WorkerThread
  /**
   * Fetches All displayable Introduction data.
   * Introductions with null introducerServiceId are omitted
   * @return IntroductionReader which can be used as an iterator.
   */
  @Override
  public IntroductionReader getAllDisplayableIntroductions() {
    String query = "SELECT * FROM " + TABLE_NAME + " WHERE " + INTRODUCER_SERVICE_ID + " IS NOT NULL";
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    return new IntroductionReader(db.rawQuery(query, null));
  }

 @WorkerThread
 /**
  * PRE: introductionId may not be null, IntroducerServiceId must be null
  * Updates the entry in the database accordingly.
  * Effectively "forget" who did this introduction.
  *
  * @return true if success, false otherwise
  */
 @Override
 public boolean clearIntroducer(TI_Data introduction){
   Preconditions.checkArgument(introduction.getIntroducerServiceId().equals(UNKNOWN_INTRODUCER_SERVICE_ID));
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
  @Override
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
       // If the intro is already stale, we don't need to do anything.
       if(!introduction.getState().isStale()){
         ContentValues cv = buildContentValuesForStale(introduction);
         int res = writeableDatabase.update(TABLE_NAME, cv, ID + " = ?", SqlUtil.buildArgs(introduction.getId()));
         if (res < 0){
           Log.e(TAG, "Introduction " + introduction.getId() + " for " + introduction.getIntroduceeName() + " with state " + introduction.getState() + " could not be turned stale!");
           updateSucceeded = false;
         } else {
           Log.i(TAG, "Introduction " + introduction.getId() + " for " + introduction.getIntroduceeName() + " with state " + introduction.getState() + " was turned stale!");
           // TODO: For multidevice, syncing would be handled here
         }
       }
     }
     // TODO: This does not really make sense here when there are multiple introductions...
     return updateSucceeded;
  }

  @WorkerThread
  /**
   * PRE: introductionId must be > 0
   * Deletes an introduction out of the database.
   *
   * @return true if success, false otherwise
   */
  @Override
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
  @Override public Map<RecipientId, RecipientRecord> fetchRecipientRecord(RecipientId introduceeId){
    // TODO: Simplify if you see that you finally never query this cursor with more than 1 recipient...
    Set<RecipientId> s = new HashSet<>();
    s.add(introduceeId);
    return RecipientTableGlue.statics.getRecordsForSendingTI(s);
  }

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
