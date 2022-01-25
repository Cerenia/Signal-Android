/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.signal.core.util.CursorUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.signal.core.util.SqlUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

public class IdentityDatabase extends Database {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(IdentityDatabase.class);

          static final String TABLE_NAME           = "identities";
  private static final String ID                   = "_id";
          static final String ADDRESS              = "address";
          static final String IDENTITY_KEY         = "identity_key";
  private static final String FIRST_USE            = "first_use";
  private static final String TIMESTAMP            = "timestamp";
          static final String VERIFIED             = "verified";
  private static final String NONBLOCKING_APPROVAL = "nonblocking_approval";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                   + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  ADDRESS              + " INTEGER UNIQUE, " +
                                                                                  IDENTITY_KEY         + " TEXT, " +
                                                                                  FIRST_USE            + " INTEGER DEFAULT 0, " +
                                                                                  TIMESTAMP            + " INTEGER DEFAULT 0, " +
                                                                                  VERIFIED             + " INTEGER DEFAULT 0, " +
                                                                                  NONBLOCKING_APPROVAL + " INTEGER DEFAULT 0);";

  private final String[] ID_PROJECTION = {ID};

  /**
   * Trusted Introductions: We differentiate between a direct verification <code>DIRECTLY_VERIFIED</code> (via. QR code)
   * and a weaker, manual verification <code>MANUALLY_VERIFIED</code>. Additionally, a user can become verified by the
   * trusted introductions mechanism <code>TRUSTINGLY_INTRODUCED</code>. A user that has been trustingly introduced and
   * directly verified is <code>DUPLEX_VERIFIED</code>, the strongest level.
   * A user can always manually reset the trust to be unverified.
   * @see ... Verif
   *
   */
  public enum VerifiedStatus {
    DEFAULT, DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED, MANUALLY_VERIFIED, UNVERIFIED;

    public int toInt() {
      switch (this) {
        case DEFAULT:               return 0;
        case DIRECTLY_VERIFIED:     return 1;
        case INTRODUCED:            return 2;
        case DUPLEX_VERIFIED:       return 3;
        case MANUALLY_VERIFIED:     return 4;
        case UNVERIFIED:            return 5;
        default:         throw new AssertionError();
      }
    }

    public static VerifiedStatus forState(int state) {
      switch (state) {
        case 0:  return DEFAULT;
        case 1:  return DIRECTLY_VERIFIED;
        case 2:  return INTRODUCED;
        case 3:  return DUPLEX_VERIFIED;
        case 4:  return MANUALLY_VERIFIED;
        case 5:  return UNVERIFIED;
        default: throw new AssertionError("No such state: " + state);
      }
    }

    /**
     * Much of the code relies on checks of the verification status that are not interested in the finer details.
     * This function can now be called instead of doing 4 comparisons manually.
     * Do not use this to decide if trusted introduction is allowed.
     * @return True is verified, false otherwise.
     */
    public static boolean isVerified(VerifiedStatus verifiedStatus){
      switch (verifiedStatus) {
        case DIRECTLY_VERIFIED:
        case INTRODUCED:
        case DUPLEX_VERIFIED:
        case MANUALLY_VERIFIED:
          return true;
        default:
          return false;
      }
    }

    /**
     * Adding this in order to be able to change my mind easily on what should unlock a TI.
     * For now, only direct verification unlocks it, in order not to propagate malicious verifications
     * further than one connection.
     *
     * @param verifiedStatus the verification status to be checked
     * @return true if strongly enough verified to unlock trusted introduction, false otherwise
     */
    public static boolean tiUnlocked(VerifiedStatus verifiedStatus){
      switch (verifiedStatus) {
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
          return true;
        default:
          return false;
      }
    }

    /**
     * Returns true for any non-trivial positive verification status.
     * Used to promt user when clearing a verification status that is not trivially recoverable.
     */
    public static boolean stronglyVerified(VerifiedStatus verifiedStatus){
      switch (verifiedStatus){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
        case INTRODUCED:
          return true;
        default:
          return false;
      }
    }
  }

  IdentityDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public @Nullable IdentityStoreRecord getIdentityStoreRecord(@NonNull String addressName) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String         query      = ADDRESS + " = ?";
    String[]       args       = SqlUtil.buildArgs(addressName);

    try (Cursor cursor = database.query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        String      serializedIdentity  = CursorUtil.requireString(cursor, IDENTITY_KEY);
        long        timestamp           = CursorUtil.requireLong(cursor, TIMESTAMP);
        int         verifiedStatus      = CursorUtil.requireInt(cursor, VERIFIED);
        boolean     nonblockingApproval = CursorUtil.requireBoolean(cursor, NONBLOCKING_APPROVAL);
        boolean     firstUse            = CursorUtil.requireBoolean(cursor, FIRST_USE);

        return new IdentityStoreRecord(addressName,
                                       new IdentityKey(Base64.decode(serializedIdentity), 0),
                                       VerifiedStatus.forState(verifiedStatus),
                                       firstUse,
                                       timestamp,
                                       nonblockingApproval);
      } else if (UuidUtil.isUuid(addressName)) {
        if (SignalDatabase.recipients().containsPhoneOrUuid(addressName)) {
          Recipient recipient = Recipient.external(context, addressName);

          if (recipient.hasE164() && !UuidUtil.isUuid(recipient.requireE164())) {
            Log.i(TAG, "Could not find identity for UUID. Attempting E164.");
            return getIdentityStoreRecord(recipient.requireE164());
          } else {
            Log.i(TAG, "Could not find identity for UUID, and our recipient doesn't have an E164.");
          }
        } else {
          Log.i(TAG, "Could not find identity for UUID, and we don't have a recipient.");
        }
      } else {
        Log.i(TAG, "Could not find identity for E164 either.");
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }

    return null;
  }

  public void saveIdentity(@NonNull String addressName,
                           @NonNull RecipientId recipientId,
                           IdentityKey identityKey,
                           VerifiedStatus verifiedStatus,
                           boolean firstUse,
                           long timestamp,
                           boolean nonBlockingApproval)
  {
    saveIdentityInternal(addressName, recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
    SignalDatabase.recipients().markNeedsSync(recipientId);
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  public void setApproval(@NonNull String addressName, @NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();

    ContentValues contentValues = new ContentValues(2);
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval);

    database.update(TABLE_NAME, contentValues, ADDRESS + " = ?", SqlUtil.buildArgs(addressName));

    SignalDatabase.recipients().markNeedsSync(recipientId);
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  public void setVerified(@NonNull String addressName, @NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();

    String   query = ADDRESS + " = ? AND " + IDENTITY_KEY + " = ?";
    String[] args  = SqlUtil.buildArgs(addressName, Base64.encodeBytes(identityKey.serialize()));

    ContentValues contentValues = new ContentValues(1);
    contentValues.put(VERIFIED, verifiedStatus.toInt());

    int updated = database.update(TABLE_NAME, contentValues, query, args);

    if (updated > 0) {
      Optional<IdentityRecord> record = getIdentityRecord(addressName);
      if (record.isPresent()) EventBus.getDefault().post(record.get());
      SignalDatabase.recipients().markNeedsSync(recipientId);
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  /**
   *
   * @return Returns a Cursor which iterates through all contacts that are unlocked for
   * trusted introductions (for which @see VerifiedStatus.tiUnlocked returns true)
   */
  public @NonNull Cursor getTIUnlocked(){
    ArrayList<String> validStates = new ArrayList<>();
    // dynamically compute the valid states and query the Signal database for these contacts
    for(VerifiedStatus e: VerifiedStatus.values()){
      if(VerifiedStatus.tiUnlocked(e)){
        validStates.add(String.valueOf(e.toInt()));
      }
    }
    // create selection String
    assert validStates.size() > 0: "No valid states defined for TI";
    StringBuilder selectionBuilder = new StringBuilder();
    selectionBuilder.append(String.format("%s=?", VERIFIED));
    if (validStates.size() > 1) {
      for (int i = 0; i < validStates.size() - 1; i++){
        selectionBuilder.append(String.format(" OR %s=?", VERIFIED));
      }
    }
    // create the rest of the query
    SQLiteDatabase readableDatabase = getReadableDatabase();
    String selection = String.format("%s=? OR ", VERIFIED);
    String[] states = validStates.toArray(new String[]{});
    return readableDatabase.query(TABLE_NAME, ID_PROJECTION, selectionBuilder.toString(), states, null, null, null);
  }

  public void updateIdentityAfterSync(@NonNull String addressName, @NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    Optional<IdentityRecord> existingRecord = getIdentityRecord(addressName);

    boolean hadEntry      = existingRecord.isPresent();
    boolean keyMatches    = hasMatchingKey(addressName, identityKey);
    boolean statusMatches = keyMatches && hasMatchingStatus(addressName, identityKey, verifiedStatus);

    if (!keyMatches || !statusMatches) {
      saveIdentityInternal(addressName, recipientId, identityKey, verifiedStatus, !hadEntry, System.currentTimeMillis(), true);

      Optional<IdentityRecord> record = getIdentityRecord(addressName);

      if (record.isPresent()) {
        EventBus.getDefault().post(record.get());
      }

      ApplicationDependencies.getProtocolStore().aci().identities().invalidate(addressName);
    }

    if (hadEntry && !keyMatches) {
      Log.w(TAG, "Updated identity key during storage sync for " + addressName + " | Existing: " + existingRecord.get().getIdentityKey().hashCode() + ", New: " + identityKey.hashCode());
      IdentityUtil.markIdentityUpdate(context, recipientId);
    }
  }

  public void delete(@NonNull String addressName) {
    databaseHelper.getSignalWritableDatabase().delete(IdentityDatabase.TABLE_NAME, IdentityDatabase.ADDRESS + " = ?", SqlUtil.buildArgs(addressName));
  }

  private Optional<IdentityRecord> getIdentityRecord(@NonNull String addressName) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String         query    = ADDRESS + " = ?";
    String[]       args     = SqlUtil.buildArgs(addressName);

    try (Cursor cursor = database.query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return Optional.of(getIdentityRecord(cursor));
      }
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }

    return Optional.empty();
  }

  private boolean hasMatchingKey(@NonNull String addressName, IdentityKey identityKey) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = ADDRESS + " = ? AND " + IDENTITY_KEY + " = ?";
    String[]       args  = SqlUtil.buildArgs(addressName, Base64.encodeBytes(identityKey.serialize()));

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  private boolean hasMatchingStatus(@NonNull String addressName, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = ADDRESS + " = ? AND " + IDENTITY_KEY + " = ? AND " + VERIFIED + " = ?";
    String[]       args  = SqlUtil.buildArgs(addressName, Base64.encodeBytes(identityKey.serialize()), verifiedStatus.toInt());

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  private static @NonNull IdentityRecord getIdentityRecord(@NonNull Cursor cursor) throws IOException, InvalidKeyException {
    String      addressName         = CursorUtil.requireString(cursor, ADDRESS);
    String      serializedIdentity  = CursorUtil.requireString(cursor, IDENTITY_KEY);
    long        timestamp           = CursorUtil.requireLong(cursor, TIMESTAMP);
    int         verifiedStatus      = CursorUtil.requireInt(cursor, VERIFIED);
    boolean     nonblockingApproval = CursorUtil.requireBoolean(cursor, NONBLOCKING_APPROVAL);
    boolean     firstUse            = CursorUtil.requireBoolean(cursor, FIRST_USE);
    IdentityKey identity            = new IdentityKey(Base64.decode(serializedIdentity), 0);

    return new IdentityRecord(RecipientId.fromExternalPush(addressName), identity, VerifiedStatus.forState(verifiedStatus), firstUse, timestamp, nonblockingApproval);
  }

  private void saveIdentityInternal(@NonNull String addressName,
                                    @NonNull RecipientId recipientId,
                                    IdentityKey identityKey,
                                    VerifiedStatus verifiedStatus,
                                    boolean firstUse,
                                    long timestamp,
                                    boolean nonBlockingApproval)
  {
    SQLiteDatabase database          = databaseHelper.getSignalWritableDatabase();
    String         identityKeyString = Base64.encodeBytes(identityKey.serialize());

    ContentValues contentValues = new ContentValues();
    contentValues.put(ADDRESS, addressName);
    contentValues.put(IDENTITY_KEY, identityKeyString);
    contentValues.put(TIMESTAMP, timestamp);
    contentValues.put(VERIFIED, verifiedStatus.toInt());
    contentValues.put(NONBLOCKING_APPROVAL, nonBlockingApproval ? 1 : 0);
    contentValues.put(FIRST_USE, firstUse ? 1 : 0);

    database.replace(TABLE_NAME, null, contentValues);

    EventBus.getDefault().post(new IdentityRecord(recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval));
  }
}
