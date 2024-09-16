package org.thoughtcrime.securesms.trustedIntroductions.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.camera.core.processing.SurfaceProcessorNode;

import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseTable;
import org.thoughtcrime.securesms.database.SQLiteDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.MissingIdentityException;
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue;
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue;
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
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
    Preconditions.checkArgument(state == State.PENDING || state == State.PENDING_CONFLICTING);
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
    // Find stale state
    State newState = switch (introduction.getState()) {
      case PENDING -> State.STALE_PENDING;
      case ACCEPTED -> State.STALE_ACCEPTED;
      case REJECTED -> State.STALE_REJECTED;
      case PENDING_CONFLICTING -> State.STALE_PENDING_CONFLICTING;
      case ACCEPTED_CONFLICTING -> State.STALE_ACCEPTED_CONFLICTING;
      case REJECTED_CONFLICTING -> State.STALE_REJECTED_CONFLICTING;
      default -> throw new AssertionError("State: " + introduction.getState() + " was illegal or already stale.");
    };

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
    Optional<RecipientId> introduceeOpt =  SignalDatabase.recipients().getByServiceId(ServiceId.parseOrThrow(data.getIntroduceeServiceId()));
    RecipientId introduceeId = introduceeOpt.orElse(null);
    if(introduceeId != null) {
      // The recipient already exists, check if the identity key matches what we already have in the database
      String identityKey;
      try {
        identityKey = TI_Utils.getEncodedIdentityKey(introduceeId);
        if(!data.getIntroduceeIdentityKey().equals(identityKey)){
          return insertIntroduction(data, State.PENDING_CONFLICTING);
        }
      } catch (MissingIdentityException e){
        // Continue to end condition, recipient is unknown.
      }
      return insertIntroduction(data, State.PENDING);
    }
    throw new AssertionError(TAG + "This code should be unreachable!");
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
   * Modify the state of an introduction and call to modify the verified state of the introducee if appropriate
   * @param introduction the introduction to be modified. Their state cannot be any of the PENDING states as they are final. ID =! null.
   * @param newState the new state for the introduction. Cannot be PENDING
   * @param logMessage what should be written on the logcat for the modification.
   * @return  if the insertion succeeded or failed
   */
  @WorkerThread
  private boolean changeIntroductionState(@NonNull TI_Data introduction, @NonNull State newState, @NonNull String logMessage) {
    // We are setting the pending states directly when the introduction is first received. There is no other transition to this state.
    Preconditions.checkArgument(newState != State.PENDING);
    Preconditions.checkArgument(introduction.getId() != null);

    // Modify introduction
    ContentValues newValues = buildContentValuesForStateUpdate(introduction, newState);
    SQLiteDatabase writeableDatabase = getSignalWritableDatabase();
    long result = writeableDatabase.update(TABLE_NAME, newValues, ID + " = ?", SqlUtil.buildArgs(introduction.getId()));

    if ( result > 0 ){
      // Log message on success
      Log.i(TAG, logMessage);
      // Check if a recipient may change verification status as a result of this operation
      RecipientId introduceeID = TI_Utils.getRecipientIdOrUnknown(introduction.getIntroduceeServiceId());
      if(!introduceeID.isUnknown()){
        TI_IdentityTable.VerifiedStatus previousIntroduceeVerification = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(introduceeID);
        if (previousIntroduceeVerification == null){
          throw new AssertionError("Unexpected missing verification status for " + introduction.getIntroduceeName());
        }
        SignalDatabase.tiIdentityDatabase().modifyIntroduceeVerification(introduction.getIntroduceeServiceId(), previousIntroduceeVerification, newState, logMessage);
      } // if introduceeID is unknnown we do not have the recipient as a conversation partner yet and can skip any verification modification
      return true;
    }
    // don't touch the verification state of the introducee if the modification failed
    Log.e(TAG, "State modification of introduction: " + introduction.getId() + " failed!");
    return false;
  }

  /**
   * @param state which state to query for
   * @param introduceeServiceId The serviceID of the recipient whose verification status may change
   */
  @WorkerThread
  public boolean atLeastOneIntroductionIs(State state, String introduceeServiceId){
    final String selection = String.format("%s=?", INTRODUCEE_SERVICE_ID)
                                    + String.format(" AND %s=?", STATE);
    String[] args = SqlUtil.buildArgs(introduceeServiceId,
                                      state.toInt());
    SQLiteDatabase writeableDatabase = getSignalWritableDatabase();
    Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selection, args, null, null, null);

    return c.getCount() >= 1;
   }

  /**
   * Check database for any preexisting introduction and modify verification state of the introducee if appropriate.
   * @param serviceId the service ID of the new contact
   */
  @WorkerThread
  @Override public void handleDanglingIntroductions(String serviceId, String encodedIdentityKey) {
    final String selection = String.format("%s=?", INTRODUCEE_SERVICE_ID);
    String[] args = SqlUtil.buildArgs(serviceId);
    SQLiteDatabase writeableDatabase = getSignalWritableDatabase();
    Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selection, args, null, null, null);
    ArrayList<TI_Data> staleIntroductions = new ArrayList<>();
    if (c.getCount() >= 1) {
      IntroductionReader reader = new IntroductionReader(c);
      TI_Data current;
      do {
        current = reader.getNext();
        if(!encodedIdentityKey.equals(current.getIntroduceeIdentityKey())){
          // Add this datapoint to the introductions that must be turned stale
          staleIntroductions.add(current);
        }
      } while (reader.hasNext());
      try {
        reader.close();
      } catch (IOException e) {
        e.printStackTrace();
        throw new AssertionError("Error occured while trying to close the cursor to dangling Introductions for " + current.getIntroduceeName());
      }
      // Turn all introductions stale that had the incorrect identity key
      for (TI_Data staleIntro: staleIntroductions) {
        ContentValues cv = buildContentValuesForStale(staleIntro.getIntroduction());
        long result = writeableDatabase.update(TABLE_NAME, cv, ID + " = ?", SqlUtil.buildArgs(staleIntro.getId()));
        if (result < 0){
          throw new AssertionError(TAG + " Could not turn introduction for " + staleIntro.getIntroduceeName() + " stale!");
        }
      }
    }
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
    return changeIntroductionState(introduction, State.ACCEPTED, "Accepted introduction for: " + introduction.getIntroduceeName());
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
    return changeIntroductionState(introduction, State.REJECTED, "Rejected introduction for: " + introduction.getIntroduceeName());
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
   * If this succeeds attempts to update the verification status of the introducee
   * @param serviceId the introducee whose security nr. changed.
   * @return true if all updates succeeded, false otherwise
   */
  public boolean turnAllIntroductionsStale(String serviceId){
    boolean success = turnAllIntroductionsStaleInternal(serviceId);
    Recipient recipient =  Recipient.live(RecipientId.fromSidOrE164(serviceId)).get();
    if(!success){
      // This should hopefully never happen... if it does we need to investigate how to handle this inconsistent state.
      // Maybe turn it into a job and try again?
      throw new AssertionError("At least one introduction for: " + recipient.getDisplayName(context) + " could not be turned stale! Verification state will not be updated!");
    }
    IdentityTableGlue tiIdentityDB = SignalDatabase.tiIdentityDatabase();
    // Any stale state will result in the same unverified new verification state
    tiIdentityDB.modifyIntroduceeVerification(serviceId, tiIdentityDB.getVerifiedStatus(recipient.getId()), State.STALE_PENDING, "Marked " + recipient.getDisplayName(context) + " unverified"
                                                                                                                                 + "after successfully turning all introductions for them stale.");
    return true;
  }

  private boolean turnAllIntroductionsStaleInternal(String serviceId){
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
