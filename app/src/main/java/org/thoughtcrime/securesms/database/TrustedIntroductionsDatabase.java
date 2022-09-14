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
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
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

  public static final long CLEARED_INTRODUCING_RECIPIENT_ID = -1; // See RecipientId.UNKNOWN
  public static final long UNINITIALIZED_INTRODUCEE_RECIPIENT_ID = -1;

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
      INTRODUCER_RECIPIENT_ID + " INTEGER NOT NULL, " + // TODO: Do I need to mark RecipientId as UNIQUE?
      INTRODUCEE_RECIPIENT_ID + " INTEGER DEFAULT " + UNINITIALIZED_INTRODUCEE_RECIPIENT_ID + ", " +
      INTRODUCEE_SERVICE_ID + " TEXT UNIQUE NOT NULL, " +
      INTRODUCEE_PUBLIC_IDENTITY_KEY + " TEXT NOT NULL, " +
      INTRODUCEE_NAME + " TEXT NOT NULL, " +
      INTRODUCEE_NUMBER + " TEXT UNIQUE NOT NULL, " +
      PREDICTED_FINGERPRINT + " TEXT UNIQUE NOT NULL, " +
      TIMESTAMP + " INTEGER NOT NULL, " +
      STATE + " INTEGER NOT NULL);";

  private static final String CLEAR_TABLE = "DELETE FROM " + TABLE_NAME + ";";

  @VisibleForTesting
  public void clearTable(){
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    int res = db.delete(TABLE_NAME, "", new String[]{});
  }

  // TODO: eventually, make a few different projections to save some ressources
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

  private static final String[] DUPLICATE_SEARCH_PROJECTION = new String[]{
      INTRODUCER_RECIPIENT_ID,
      INTRODUCEE_RECIPIENT_ID,
      INTRODUCEE_SERVICE_ID,
      INTRODUCEE_PUBLIC_IDENTITY_KEY,
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
          throw new AssertionError("No such state: " + state);
      }
    }
  }

  public TrustedIntroductionsDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
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
    Cursor c = writeableDatabase.query(TABLE_NAME, DUPLICATE_SEARCH_PROJECTION, selectionBuilder.toString(), args, null, null, null);
    if (c.getCount() == 1){
      c.moveToFirst();
      ContentValues value = new ContentValues(1);
      value.put(TIMESTAMP, data.getTimestamp());
      long result = writeableDatabase.update(TABLE_NAME, value, ID + " = ?", SqlUtil.buildArgs(c.getInt(c.getColumnIndex(ID))));
      // TODO testing
      Log.e(TAG, "Updated timestamp of introduction " + result + " to: " + data.getTimestamp());
      c.close();
      return result;
    }
    assert c.getCount() == 0: TAG + " Either there is one entry or none, nothing else valid.";
    c.close();

    ContentValues values;
    RecipientId introduceeId = data.getIntroduceeId();
    if(introduceeId == null){
      values = new ContentValues(8);
      values.put(STATE, State.PENDING.toInt()); // if recipient does not exist, we have nothing to compare against.
      // TODO: How to fetch a recipient based on ServiceID? Should compare in any case...
    } else {
      values = new ContentValues(9);
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

  // TODO: Just pass a TI_Data object instead?
  @WorkerThread
  public boolean acceptIntroduction(int id, RecipientId introduceeId){
    Cursor rdc = fetchRecipientDBCursor(introduceeId);
    if(rdc.getCount() <= 0){
      // TODO: Add introducee if not present in the database
    }
    // TODO: Statechange, pending -> accepted
    return true;
  }

  @WorkerThread
  /**
   *
   * Fetches Introduction data by Introducer. Pass null or unknown to get all the data.
   *
   * @introducerId If an Id != UNKNOWN is specified, selects only introductions made by this contact. Fetches all Introductions otherwise.
   * @return true if success, false otherwise
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
  * Replaces the introducer Recipient Id with a placeholder value.
  * Effectively "forget" who did this introduction.
  *
  * @return true if success, false otherwise
  */
 public boolean clearIntroducer(int introductionId){
   SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
   String query = ID + " = ?";
   String[] args = SqlUtil.buildArgs(introductionId);

   ContentValues values = new ContentValues(1);
   values.put(INTRODUCER_RECIPIENT_ID, CLEARED_INTRODUCING_RECIPIENT_ID);

   int update = database.update(TABLE_NAME, values, query, args);

   if ( update > 0 ){
     // TODO: For multidevice, syncing would be handled here
     return true;
   }
   return false;
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

    private boolean checkProjection(Cursor c){
      String[] projection = TI_ALL_PROJECTION;
      String[] columns = c.getColumnNames();
      Arrays.sort(projection);
      Arrays.sort(columns);
      for (int i = 0; i < projection.length; i++){
        if(!projection[i].equals(columns[i])){
          return false;
        }
      }
      return true;
    }

    // TODO: Make it slightly more flexible in terms of which data you pass around.
    // A cursor pointing to the result of a query using TI_DATA_PROJECTION
    IntroductionReader(Cursor c){
      Preconditions.checkArgument(checkProjection(c));
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
      RecipientId   introducerId = RecipientId.from(cursor.getLong(cursor.getColumnIndex(INTRODUCEE_RECIPIENT_ID)));
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
