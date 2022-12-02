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
import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionSendJob;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsReceiveJob;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetreiveIdentityJob;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
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
public class TrustedIntroductionsDatabase extends Database {

  private final String TAG = Log.tag(TrustedIntroductionsDatabase.class);

  public static final String TABLE_NAME = "trusted_introductions";

  private static final String ID                      = "_id";
  private static final String INTRODUCER_RECIPIENT_ID = "introducer_id";
  private static final String INTRODUCEE_RECIPIENT_ID = "introducee_id"; // TODO: Is this really as immutable as I think?...
  private static final String INTRODUCEE_SERVICE_ID          = "introducee_service_id";
  private static final String INTRODUCEE_PUBLIC_IDENTITY_KEY = "introducee_identity_key"; // The one contained in the Introduction
  private static final String INTRODUCEE_NAME                = "introducee_name"; // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
  private static final String INTRODUCEE_NUMBER     = "introducee_number"; // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
  private static final String PREDICTED_FINGERPRINT = "predicted_fingerprint";
  private static final String TIMESTAMP             = "timestamp";
  private static final String STATE                          = "state";

  public static final long CLEARED_INTRODUCER_RECIPIENT_ID = -1; // See RecipientId.UNKNOWN
  public static final long UNKNOWN_INTRODUCEE_RECIPIENT_ID = -1; //TODO: need to search through database for serviceID when new recipient is added in order to initialize.

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
      INTRODUCER_RECIPIENT_ID + " INTEGER NOT NULL, " +
      INTRODUCEE_RECIPIENT_ID + " INTEGER DEFAULT " + UNKNOWN_INTRODUCEE_RECIPIENT_ID + ", " +
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
    // TODO: remove call once debugging done
    //SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    //int res = db.delete(TABLE_NAME, "", new String[]{});
  }

  // TODO: (optional) eventually, make a few different projections to save some ressources
  // for now just having one universal one is fine.
  private static final String[] TI_ALL_PROJECTION = new String[]{
      ID,
      INTRODUCER_RECIPIENT_ID,
      INTRODUCEE_RECIPIENT_ID,
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
   * changes, the entries become stale. // TODO: Should stale state eve be a thing? Or just remove the entry?
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
          throw new AssertionError();
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
          // TODO: add back when you know what's up
          //throw new AssertionError("No such state: " + state);
          return CONFLICTING;
      }
    }
  }

  public TrustedIntroductionsDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }


  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull Long introductionId,
                                                    @NonNull State state,
                                                    @NonNull Long introducerId,
                                                    @NonNull Long introduceeId,
                                                    @NonNull String serviceId,
                                                    @NonNull String name,
                                                    @NonNull String number,
                                                    @NonNull String identityKey,
                                                    @NonNull String predictedFingerprint,
                                                    @NonNull Long timestamp){
    ContentValues cv = new ContentValues();
    cv.put(ID, introductionId);
    cv.put(STATE, state.toInt());
    cv.put(INTRODUCER_RECIPIENT_ID, introducerId);
    cv.put(INTRODUCEE_RECIPIENT_ID, introduceeId);
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
                                       c.getString(c.getColumnIndex(INTRODUCER_RECIPIENT_ID)),
                                       c.getString(c.getColumnIndex(INTRODUCEE_RECIPIENT_ID)),
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
   * @param introducerId Expected to represent a Long > 0.
   * @param introduceeId Expected to represent a Long > 0.
   * @param serviceId
   * @param name
   * @param number
   * @param identityKey
   * @param predictedFingerprint
   * @param timestamp Expected to represent a Long.
   * @return Propperly populated content values, NumberFormatException/AssertionError if a value was invalid.
   */
  private @NonNull ContentValues buildContentValuesForUpdate(@NonNull String introductionId,
                                                             @NonNull String state,
                                                             @NonNull String introducerId,
                                                             @NonNull String introduceeId,
                                                             @NonNull String serviceId,
                                                             @NonNull String name,
                                                             @NonNull String number,
                                                             @NonNull String identityKey,
                                                             @NonNull String predictedFingerprint,
                                                             @NonNull String timestamp) throws NumberFormatException{
    Preconditions.checkArgument(!introductionId.isEmpty() &&
                                !state.isEmpty() &&
                                !introducerId.isEmpty() &&
                                !introduceeId.isEmpty() &&
                                !serviceId.isEmpty() &&
                                !name.isEmpty() &&
                                !number.isEmpty() &&
                                !identityKey.isEmpty() &&
                                !predictedFingerprint.isEmpty() &&
                                !timestamp.isEmpty());
    long introId = Long.parseLong(introductionId);
    Preconditions.checkArgument(introId > 0);
    int s = Integer.parseInt(state);
    Preconditions.checkArgument(s >= 0 && s <= 7);
    long introducerIdLong = Long.parseLong(introducerId);
    Preconditions.checkArgument(introducerIdLong > 0);
    long introduceeIdLong = Long.parseLong(introduceeId);
    Preconditions.checkArgument(introduceeIdLong > 0);
    long timestampLong = Long.parseLong(timestamp);
    Preconditions.checkArgument(timestampLong > 0);
    return buildContentValuesForUpdate(introId,
                                       State.forState(s),
                                       introducerIdLong,
                                       introduceeIdLong,
                                       serviceId,
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
    Preconditions.checkNotNull(introduction.getIntroduceeId());
    Preconditions.checkNotNull(introduction.getIntroducerId());
    Preconditions.checkNotNull(introduction.getPredictedSecurityNumber());
    return buildContentValuesForUpdate(introduction.getId(),
                                       introduction.getState(),
                                       introduction.getIntroducerId().toLong(),
                                       introduction.getIntroduceeId().toLong(),
                                       introduction.getIntroduceeServiceId(),
                                       introduction.getIntroduceeName(),
                                       introduction.getIntroduceeNumber(),
                                       introduction.getIntroduceeIdentityKey(),
                                       introduction.getPredictedSecurityNumber(),
                                       introduction.getTimestamp());
  }


  /**
   *
   * @return -1 -> conflict occured on insert, else id of introduction.
   */
  @SuppressLint("Range") @WorkerThread
  public long incomingIntroduction(@NonNull TI_Data data){

    // Fetch Data out of database where everything is identical but timestamp.
    StringBuilder selectionBuilder = new StringBuilder();
    selectionBuilder.append(String.format("%s=?", INTRODUCER_RECIPIENT_ID)); // if ID was purged, duplicate detection no longer possible // TODO: issue for, e.g., count if pure distance-1 case (future problem)
    String andAppend = " AND %s=?";
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_RECIPIENT_ID));
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_SERVICE_ID));
    selectionBuilder.append(String.format(andAppend, INTRODUCEE_PUBLIC_IDENTITY_KEY));
    selectionBuilder.append(String.format(andAppend, STATE));

    int s = State.PENDING.toInt();

    // TODO: if this works well, use in other dbs where you build queries
    String[] args = SqlUtil.buildArgs(data.getIntroducerId().serialize(),
                                      data.getIntroduceeId() == null ? "NULL" : data.getIntroduceeId().serialize(),
                                      data.getIntroduceeServiceId(),
                                      data.getIntroduceeIdentityKey(),
                                      s); // for now checking for pending state TODO: does that make sense?

    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    Cursor c = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selectionBuilder.toString(), args, null, null, null);
    if (c.getCount() == 1){
      c.moveToFirst();
      if(c.getString(c.getColumnIndex(INTRODUCEE_PUBLIC_IDENTITY_KEY)).equals(data.getIntroduceeIdentityKey())) {
        long result = writeableDatabase.update(TABLE_NAME, buildContentValuesForTimestampUpdate(c, data.getTimestamp()), ID + " = ?", SqlUtil.buildArgs(c.getInt(c.getColumnIndex(ID))));
        Log.e(TAG, "Updated timestamp of introduction " + result + " to: " + TI_Utils.INTRODUCTION_DATE_PATTERN.format(data.getTimestamp()));
        c.close();
        return result;
      }
    }
    if(c.getCount() != 0) throw new AssertionError(TAG + " Either there is one entry or none, nothing else valid.");
    c.close();

    ContentValues values = new ContentValues(9);
    RecipientId introduceeId = data.getIntroduceeId();
    if(introduceeId == null){
      values.put(STATE, State.PENDING.toInt()); // if recipient does not exist, we have nothing to compare against.
      // TODO: How to fetch a recipient based on ServiceID? Should compare in any case...
      // TODO: Have a look at Retrieve Profile Job! => Add a callback in the database?
      // TODO: After doing this, the recipient Id will no longer be null. Is this generally a problem because of leaking state?
      // TODO: After this is devd, simplify this function by calling helpers to build content values.
      // for now, adding unknown recipient id
      //values.put(INTRODUCEE_RECIPIENT_ID, UNKNOWN_INTRODUCEE_RECIPIENT_ID);
      ApplicationDependencies.getJobManager().add(new TrustedIntroductionsRetreiveIdentityJob(data));
      // TODO: testing
      Log.e(TAG, "Unknown recipient, deferred insertion of Introduction into database for: " + data.getIntroduceeName());
      return -1;
    } else {
      values.put(INTRODUCEE_RECIPIENT_ID, introduceeId.toLong());
      if (TI_Utils.encodedIdentityKeysEqual(introduceeId, data.getIntroduceeIdentityKey())){
        values.put(STATE, State.PENDING.toInt());
      } else {
        values.put(STATE, State.CONFLICTING.toInt());
      }
    }

    values.put(INTRODUCER_RECIPIENT_ID, data.getIntroducerId().toLong());
    values.put(INTRODUCEE_SERVICE_ID, data.getIntroduceeServiceId());
    values.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, data.getIntroduceeIdentityKey());
    values.put(INTRODUCEE_NAME, data.getIntroduceeName());
    values.put(INTRODUCEE_NUMBER, data.getIntroduceeNumber());
    values.put(PREDICTED_FINGERPRINT, data.getPredictedSecurityNumber());
    values.put(TIMESTAMP, data.getTimestamp());

    long id = writeableDatabase.insert(TABLE_NAME, null, values);
    // TODO: testing
    Log.e(TAG, "Inserted new introduction for: " + data.getIntroduceeName() + ", with id: " + id);
    return id;
  }

  // Callback for profile retreive Identity job
  // TODO: annoying that this needs to be public. Should be private and just passed as function pointer..
  // But java is annoying when it comes to function serialization so I won't do that for now
  public long insertIntroductionCallback(TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult result){
    Preconditions.checkArgument(result.aci.equals(result.data.getIntroduceeServiceId()));
    ContentValues values = new ContentValues(9);
    // This is a recipient we do not have yet.
    values.put(INTRODUCEE_RECIPIENT_ID, UNKNOWN_INTRODUCEE_RECIPIENT_ID);
    if(result.key.equals(result.data.getIntroduceeIdentityKey())){
      values.put(STATE, State.PENDING.toInt());
    } else {
      values.put(STATE, State.CONFLICTING.toInt());
    }
    values.put(INTRODUCER_RECIPIENT_ID, result.data.getIntroducerId().toLong());
    values.put(INTRODUCEE_SERVICE_ID, result.data.getIntroduceeServiceId());
    values.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, result.data.getIntroduceeIdentityKey());
    values.put(INTRODUCEE_NAME, result.data.getIntroduceeName());
    values.put(INTRODUCEE_NUMBER, result.data.getIntroduceeNumber());
    values.put(PREDICTED_FINGERPRINT, result.data.getPredictedSecurityNumber());
    values.put(TIMESTAMP, result.data.getTimestamp());
    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    long id = writeableDatabase.insert(TABLE_NAME, null, values);
    Log.e(TAG, "Inserted new introduction for: " + result.data.getIntroduceeName() + ", with id: " + id);
    return id;
  }

  /**
   * Expects the introducee to have been fetched.
   * Expects introduction to already be present in database
   * @param introduction PRE: introduction.introduceeId && introduction.id cannot be null
   * @return
   */
  @WorkerThread
  public boolean acceptIntroduction(TI_Data introduction){
    Preconditions.checkArgument(introduction.getIntroduceeId() != null);
    Preconditions.checkArgument(introduction.getId() != null);
    Cursor rdc = fetchRecipientDBCursor(introduction.getIntroduceeId());
    if(rdc.getCount() <= 0){
      // TODO: Add introducee if not present in the database
      // I think it should always be there now since I retreived the profile in any case...
      // Investigate if errors occur
      throw new AssertionError("Unexpected missing recipient in database while accepting introduction...");
    }
    // Set the appropriate verification status
    RecipientId introduceeID = introduction.getIntroduceeId();
    TI_Utils.updateContactsVerifiedStatus(introduceeID, TI_Utils.getIdentityKey(introduceeID), IdentityDatabase.VerifiedStatus.INTRODUCED);
    // Statechange, pending -> accepted
    ContentValues newValues = buildContentValuesForStateUpdate(introduction, State.ACCEPTED);
    SQLiteDatabase writeableDatabase = databaseHelper.getSignalWritableDatabase();
    long result = writeableDatabase.update(TABLE_NAME, newValues, ID + " = ?", SqlUtil.buildArgs(introduction.getId()));
    Log.e(TAG, "Accepted introduction for: " + introduction.getIntroduceeName());
    return result > -1;
  }

  @WorkerThread
  /**
   *
   * Fetches Introduction data by Introducer. Pass null or unknown to get all the data.
   *
   * @introducerId If an Id != UNKNOWN is specified, selects only introductions made by this contact. Fetches all Introductions otherwise.
   * @return IntroductionReader which can be used to iterate through the rows.
   */
  public IntroductionReader getIntroductions(@Nullable RecipientId introducerId){
    String query;
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    if(introducerId == null || introducerId.isUnknown()){
      query = "SELECT  * FROM " + TABLE_NAME;
      return new IntroductionReader(db.rawQuery(query, null));
    } else {
      // query only the Introductions made by introducerId
      query = INTRODUCER_RECIPIENT_ID + " = ?";
      String[] arg = SqlUtil.buildArgs(introducerId.toLong());
      return new IntroductionReader(db.query(TABLE_NAME, TI_ALL_PROJECTION, query, arg, null, null, null));
    }
  }

 @WorkerThread
 /**
  *
  * PRE: introductionId may not be null, introducerId must be UNKNOWN
  * Updates the entry in the database accordingly.
  * Effectively "forget" who did this introduction.
  *
  * @return true if success, false otherwise
  */
 public boolean clearIntroducer(TI_Data introduction){
   Preconditions.checkArgument(introduction.getIntroducerId().equals(RecipientId.UNKNOWN));
   Preconditions.checkArgument(introduction.getId() != null);
   SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
   String query = ID + " = ?";
   String[] args = SqlUtil.buildArgs(introduction.getId());

   ContentValues values = buildContentValuesForUpdate(introduction);

   int update = database.update(TABLE_NAME, values, query, args);
   Log.d(TAG, "Forgot introducer for introduction with id: " + introduction.getId());
   if ( update > 0 ){
     // TODO: For multidevice, syncing would be handled here
     return true;
   }
   return false;
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
      Log.e(TAG, String.format("Deleted introduction with id: %d from the database.", introductionId));
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
    RecipientDatabase rdb = SignalDatabase.recipients();
    // TODO: Simplify if you see that you finally never query this cursor with more than 1 recipient...
    Set<RecipientId> s = new HashSet<>();
    s.add(introduceeId);
    return rdb.getCursorForSendingTI(s);
  }

  // TODO: For now I'm keeping the history of introductions, but only showing the newest valid one. Might be beneficial to have them eventually go away if they become stale?
  // Maybe keep around one level of previous introduction? Apparently there is a situation in the app, whereas if someone does not follow the instructions of setting up
  // their new device exactly, they may be set as unverified, while they eventually still end up with the same identity key (TODO: find Github issue or discussion?)
  // For this case, having a record of stale introductions could be used to restore the verification state without having to reverify.

  /**
   * @param id which introduction to modify
   * @param state what state to set, cannot be PENDING
   * @return true if this was successfull, false otherwise (UUID did not exist in the database)
   */
  private boolean setState(int id, @NonNull State state) {
    Preconditions.checkArgument(state != State.PENDING);
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    String query = ID + " = ?";
    String[] args = SqlUtil.buildArgs(id);

    ContentValues values = new ContentValues(1);
    values.put(STATE, state.toInt());

    // TODO: Should there be update checks here to make sure no illegal state transition occurs?
    // @see FSM in your notes drawn on the 09.08.2022.

    int update = database.update(TABLE_NAME, values, query, args);

    if ( update > 0 ){
      // TODO: multidevice add here
      return true;
    }
    return false;
  }

  // TODO: all state transition methods can be public => FSM Logic adhered to this way.

  // TODO: Method which returns all non-stale introductions for a given recipient ID

  public class IntroductionReader implements Closeable{
    private final Cursor cursor;

    // TODO: Make it slightly more flexible in terms of which data you pass around.
    // A cursor pointing to the result of a query using TI_DATA_PROJECTION
    IntroductionReader(Cursor c){
      cursor = c;
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
      RecipientId   introducerId = RecipientId.from(cursor.getLong(cursor.getColumnIndex(INTRODUCER_RECIPIENT_ID)));
      RecipientId introduceeId = RecipientId.from(cursor.getLong(cursor.getColumnIndex(INTRODUCEE_RECIPIENT_ID)));
      String serviceId = cursor.getString(cursor.getColumnIndex(INTRODUCEE_SERVICE_ID));
      // Do I need to hit the Recipient Database to check the name?
      // TODO: Name changes in introducees should get reflected in database (needs to happen when the name changes, not on query)
      String introduceeName = cursor.getString(cursor.getColumnIndex(INTRODUCEE_NAME));
      String introduceeNumber = cursor.getString(cursor.getColumnIndex(INTRODUCEE_NUMBER));
      String introduceeIdentityKey = cursor.getString(cursor.getColumnIndex(INTRODUCEE_PUBLIC_IDENTITY_KEY));
      String           securityNr = cursor.getString(cursor.getColumnIndex(PREDICTED_FINGERPRINT));
      long timestamp = cursor.getLong(cursor.getColumnIndex(TIMESTAMP));
      return new TI_Data(introductionId, state, introducerId, introduceeId, serviceId, introduceeName, introduceeNumber, introduceeIdentityKey, securityNr, timestamp);
    }

    /**
     * advances one row and returns it, null if empty, or cursor after last.
     */
    public @Nullable TI_Data getNext(){
      cursor.moveToNext();
      return getCurrent();
    }

    public boolean hasNext(){
      return !cursor.isAfterLast() && !cursor.isLast();
    }

    @Override public void close() throws IOException {
      cursor.close();
    }
  }

}
