package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.mobilecoin.lib.exceptions.SerializationException;

import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.UUID;

/**
 *
 * Database holding received trusted Introductions.
 * We are consciously trying to have the sending Introduction ephemeral since we want to maximize privacy,
 * (think an Informant that forwards someone to a Journalist, you don't want that information hanging around)
 *
 * This implementation currently does not support multidevice.
 *
 */
public class TrustedIntroductionsDatabase extends Database{

  private final String TAG = Log.tag(TrustedIntroductionsDatabase.class);

  public static final String TABLE_NAME = "trusted_introductions";

  // TODO: Should the phone number be in there?
  private static final String ID                       = "_id";
  private static final String INTRODUCTION_UUID        = "introduction_uuid";
  private static final String INTRODUCING_RECIPIENT_ID = "introducer";
  private static final String INTRODUCEE_RECIPIENT_ID = "introducer";
  private static final String INTRODUCEE_PUBLIC_IDENTITY_KEY = "introducee_identity_key"; // The one contained in the Introduction
  private static final String TIMESTAMP    = "timestamp";
  private static final String STATE        = "state";

  public static final int CLEARED_INTRODUCING_RECIPIENT_ID = -1;

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY, " +
      INTRODUCTION_UUID + " TEXT, " +
      INTRODUCING_RECIPIENT_ID + " INTEGER, " +
      INTRODUCEE_RECIPIENT_ID + " INTEGER, " +
      INTRODUCEE_PUBLIC_IDENTITY_KEY + " TEXT, " +
      TIMESTAMP + " INTEGER, " +
      STATE + " INTEGER, " +
      "UNIQUE(" + INTRODUCTION_UUID + ") ON CONFLICT ABORT)";

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

  @WorkerThread
  private void createIncomingIntroduction(@NonNull UUID introductionUUID,
                                          @NonNull RecipientId introducerId,
                                          @NonNull RecipientId introduceeId,
                                          @NonNull String serializedIdentityKey, // @See IdentityDatabase.java, Base64 encoded
                                          @NonNull long timestamp)
      throws SerializationException
  {

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    ContentValues values = new ContentValues(6);

    values.put(INTRODUCTION_UUID, introductionUUID.toString());
    values.put(INTRODUCING_RECIPIENT_ID, introducerId.serialize());
    values.put(INTRODUCEE_RECIPIENT_ID, introduceeId.serialize());
    values.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, serializedIdentityKey);
    values.put(TIMESTAMP, timestamp);
    values.put(STATE, State.PENDING.toInt()); //Intro always starts with PENDING state

    long inserted = database.insert(TABLE_NAME, null, values);

    // TODO: Any observers needed? (probably to increase the "count" of 1-hop introductions in the identity database at some point => future issue)

    // TODO: Yes: Observer for checking the local identity key which may change the state to conflicting if the keys don't match.
  }

 @WorkerThread
 /**
  * Replaces the introducer Recipient Id with a placeholder value.
  * Effectively "forget" who did this introduction.
  *
  * @return true if success, false otherwise
  */
 public boolean clearIntroducer(@NonNull UUID introductionUUID){
   SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
   String query = INTRODUCTION_UUID + " = ?";
   String[] args = SqlUtil.buildArgs(introductionUUID.toString());

   ContentValues values = new ContentValues(1);
   values.put(INTRODUCING_RECIPIENT_ID, CLEARED_INTRODUCING_RECIPIENT_ID);

   int update = database.update(TABLE_NAME, values, query, args);

   if ( update > 0 ){
     // TODO: For multidevice, syncing would be handled here
     return true;
   }
   return false;
 }

  // TODO: For now I'm keeping the history of introductions, but only showing the newest valid one. Might be beneficial to have them eventually go away if they become stale?
  // Maybe keep around one level of previous introduction? Apparently there is a situation in the app, whereas if someone does not follow the instructions of setting up
  // their new device exactly, they may be set as unverified, while they eventually still end up with the same identity key (TODO: find Github issue or discussion?)
  // For this case, having a record of stale introductions could be used to restore the verification state without having to reverify.

  /**
   *
   * @param introductionUUID which introduction to modify
   * @param state what state to set, cannot be PENDING
   * @return true if this was successfull, false otherwise (most likely missing entry or invalid UUID)
   * @throws AssertionError if state was PENDING
   */
  public boolean setState(@NonNull UUID introductionUUID, @NonNull State state) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    String query = INTRODUCTION_UUID + " = ?";
    String[] args = SqlUtil.buildArgs(introductionUUID.toString());

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

}
