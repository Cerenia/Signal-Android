package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.mobilecoin.lib.exceptions.SerializationException;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;

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
  private static final String ID           = "_id";
  private static final String UUID         = "introduction_uuid";
  private static final String INTRODUCING_RECIPIENT_ID = "introducer";
  private static final String INTRODUCEE_RECIPIENT_ID = "introducer";
  private static final String INTRODUCEE_PUBLIC_IDENTITY_KEY = "introducee_identity_key"; // The one contained in the Introduction
  private static final String TIMESTAMP    = "timestamp";
  private static final String STATE        = "state";

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME + "(" + ID           + " INTEGER PRIMARY KEY, " +
                                           UUID + " TEXT, " +
                                           INTRODUCING_RECIPIENT_ID + " INTEGER, " +
                                           INTRODUCEE_RECIPIENT_ID + " INTEGER, " +
                                           INTRODUCEE_PUBLIC_IDENTITY_KEY + " TEXT, " +
                                           TIMESTAMP +  " INTEGER, " +
                                           STATE + " INTEGER, " +
                                           "UNIQUE(" + UUID + ") ON CONFLICT ABORT)";

  /**
   * An Introduction can either be waiting for a decision from the user (PENDING),
   * been accepted or rejected by the user, or conflict with the Identity Key that the
   * Signal server provided for this recipient. If the identity key of the introducee
   * changes, the entries become stale. // TODO: Should stale state eve be a thing? Or just remove the entry?
   */
  public enum State {
    PENDING, ACCEPTED, REJECTED, CONFLICTING, STALE;

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
        case STALE:
          return 4;
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
          return STALE;
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
                                          @NonNull long timestamp,
                                          @NonNull State state)
      throws SerializationException
  {

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    ContentValues values = new ContentValues(6);

    values.put(UUID, introductionUUID.toString());
    values.put(INTRODUCING_RECIPIENT_ID, introducerId.serialize());
    values.put(INTRODUCEE_RECIPIENT_ID, introduceeId.serialize());
    values.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, serializedIdentityKey);
    values.put(TIMESTAMP, timestamp);
    values.put(STATE, state.toInt());

    long inserted = database.insert(TABLE_NAME, null, values);

    // TODO: Any observers needed? (probably to increase the "count" of 1-hop introductions in the identity database at some point => future issue)
  }

}
