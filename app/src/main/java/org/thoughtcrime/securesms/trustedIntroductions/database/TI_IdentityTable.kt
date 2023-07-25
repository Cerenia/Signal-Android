package org.thoughtcrime.securesms.trustedIntroductions.database

import android.database.Cursor
import android.content.Context
import androidx.core.content.contentValuesOf
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.logging.Log
import org.signal.core.util.firstOrNull
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.toOptional
import org.signal.core.util.update
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityRecord
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.TI_ADDRESS_PROJECTION
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VERIFIED
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.IdentityUtil
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.lang.StringBuilder
import java.util.Optional


class TI_IdentityTable internal constructor(context: Context?, databaseHelper: SignalDatabase?): DatabaseTable(context, databaseHelper), IdentityTableGlue{

  companion object Singleton{
    private val TAG = Log.tag(TI_IdentityTable::class.java)
    const val TABLE_NAME = "TI_shadow_identities"
    private const val ID = "_id"
    const val ADDRESS = "address"
    const val IDENTITY_KEY = "identity_key"
    private const val FIRST_USE = "first_use"
    private const val TIMESTAMP = "timestamp"
    const val VERIFIED = "verified"
    private const val NONBLOCKING_APPROVAL = "nonblocking_approval"
    const val CREATE_TABLE = """
      CREATE TABLE ${TABLE_NAME} (
        ${ID} INTEGER PRIMARY KEY AUTOINCREMENT, 
        $ADDRESS INTEGER UNIQUE, 
        $IDENTITY_KEY TEXT, 
        $FIRST_USE INTEGER DEFAULT 0, 
        $TIMESTAMP INTEGER DEFAULT 0, 
        $VERIFIED INTEGER DEFAULT 0, 
        $NONBLOCKING_APPROVAL INTEGER DEFAULT 0
      )
    """

    var inst: IdentityTableGlue? = null

    fun setInstance(intfHandle: IdentityTableGlue ){
      if (inst != null){
        throw AssertionError("Attempted to reassign trustedIntroduction Identity Table singleton!")
      }
      inst = intfHandle
    }

  }

  /**
   *
   * @return Returns a Cursor which iterates through all contacts that are unlocked for
   * trusted introductions (for which @see VerifiedStatus.tiUnlocked returns true)
   */
  override fun getCursorForTIUnlocked(): Cursor {
    val validStates: ArrayList<String> = ArrayList()
    // dynamically compute the valid states and query the Signal database for these contacts
    for (e in VerifiedStatus.values()) {
      if (VerifiedStatus.ti_forwardUnlocked(e)) {
        validStates.add(e.toInt().toString())
      }
    }
    assert(validStates.size > 0) { "No valid states defined for TI" }
    val selectionBuilder = StringBuilder()
    selectionBuilder.append(String.format("%s=?", VERIFIED))
    if (validStates.size > 1) {
      for (i in 0 until validStates.size - 1) {
        selectionBuilder.append(String.format(" OR %s=?", VERIFIED))
      }
    }
    // create the rest of the query
    val readableDatabase = readableDatabase
    val states = validStates.toArray(arrayOf<String>())
    return readableDatabase.query(TABLE_NAME, arrayOf<String>(TI_ADDRESS_PROJECTION), selectionBuilder.toString(), states, null, null, null)
  }

  override fun getVerifiedStatus(id: RecipientId?): VerifiedStatus {
    val recipient = Recipient.resolved(id!!)
    if(recipient.hasServiceId()){
      val ir = getIdentityRecord(recipient.requireServiceId().toString())
      return ir.map(TI_IdentityRecord::verifiedStatus).orElse(VerifiedStatus.UNVERIFIED) // fail closed
    } else {
      return VerifiedStatus.DEFAULT
    }
  }


  /**
   * Expose all keys of the database for Precondition check in @TI_Cursor.java
   */
  override fun getAllDatabaseKeys(): ArrayList<String> {
    val keys = arrayListOf<String>()
    keys.add(ID)
    keys.add(ADDRESS)
    keys.add(IDENTITY_KEY)
    keys.add(FIRST_USE)
    keys.add(VERIFIED)
    keys.add(NONBLOCKING_APPROVAL)
    keys.add(TIMESTAMP)
    return keys
  }

  fun getIdentityStoreRecord(addressName: String): TI_IdentityStoreRecord? {
    readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("${ADDRESS} = ?", addressName)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          return TI_IdentityStoreRecord(
            addressName = addressName,
            identityKey = IdentityKey(Base64.decode(cursor.requireNonNullString(IDENTITY_KEY)), 0),
            verifiedStatus = VerifiedStatus.forState(cursor.requireInt(VERIFIED)),
            firstUse = cursor.requireBoolean(FIRST_USE),
            timestamp = cursor.requireLong(TIMESTAMP),
            nonblockingApproval = cursor.requireBoolean(NONBLOCKING_APPROVAL)
          )
        } else if (UuidUtil.isUuid(addressName)) {
          val byServiceId = SignalDatabase.recipients.getByServiceId(ServiceId.parseOrThrow(addressName))
          if (byServiceId.isPresent) {
            val recipient = Recipient.resolved(byServiceId.get())
            if (recipient.hasE164() && !UuidUtil.isUuid(recipient.requireE164())) {
              Log.i(TAG, "Could not find identity for UUID. Attempting E164.")
              return getIdentityStoreRecord(recipient.requireE164())
            } else {
              Log.i(TAG, "Could not find identity for UUID, and our recipient doesn't have an E164.")
            }
          } else {
            Log.i(TAG, "Could not find identity for UUID, and we don't have a recipient.")
          }
        } else {
          Log.i(TAG, "Could not find identity for E164 either.")
        }
      }

    return null
  }

  fun saveIdentity(
    addressName: String,
    recipientId: RecipientId,
    identityKey: IdentityKey,
    verifiedStatus: VerifiedStatus,
    firstUse: Boolean,
    timestamp: Long,
    nonBlockingApproval: Boolean
  ) {
    saveIdentityInternal(addressName, recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval)
    //SignalDatabase.recipients.markNeedsSync(recipientId) # no sideeffects in shadow table
    //StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun setApproval(addressName: String, recipientId: RecipientId, nonBlockingApproval: Boolean) {
    val updated = writableDatabase
      .update(TABLE_NAME)
      .values(NONBLOCKING_APPROVAL to nonBlockingApproval)
      .where("${ADDRESS} = ?", addressName)
      .run()

    //# no sideeffects in shadow table
    //if (updated > 0) {
    //  SignalDatabase.recipients.markNeedsSync(recipientId)
    //  StorageSyncHelper.scheduleSyncForDataChange()
    }

  fun setVerified(addressName: String, recipientId: RecipientId, identityKey: IdentityKey, verifiedStatus: VerifiedStatus) {
    val updated = writableDatabase
      .update(TABLE_NAME)
      .values(VERIFIED to verifiedStatus.toInt())
      .where("${ADDRESS} = ? AND ${IDENTITY_KEY} = ?", addressName, Base64.encodeBytes(identityKey.serialize()))
      .run()
    /* # no sideeffects
    if (updated > 0) {
      val record = getIdentityRecord(addressName)
      if (record.isPresent) {
        EventBus.getDefault().post(record.get())
      }
      SignalDatabase.recipients.markNeedsSync(recipientId)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
    */
  }

  fun updateIdentityAfterSync(addressName: String, recipientId: RecipientId, identityKey: IdentityKey, verifiedStatus: VerifiedStatus) {
    val existingRecord = getIdentityRecord(addressName)
    val hadEntry = existingRecord.isPresent
    val keyMatches = hasMatchingKey(addressName, identityKey)
    val statusMatches = keyMatches && hasMatchingStatus(addressName, identityKey, verifiedStatus)

    if (!keyMatches || !statusMatches) {
      saveIdentityInternal(addressName, recipientId, identityKey, verifiedStatus, !hadEntry, System.currentTimeMillis(), nonBlockingApproval = true)

      /* No sideeffects
      val record = getIdentityRecord(addressName)
      if (record.isPresent) {
        EventBus.getDefault().post(record.get())
      }

      ApplicationDependencies.getProtocolStore().aci().identities().invalidate(addressName)
      */
    }

    /* No sideeffects
    if (hadEntry && !keyMatches) {
      Log.w(IdentityTable.TAG, "Updated identity key during storage sync for " + addressName + " | Existing: " + existingRecord.get().identityKey.hashCode() + ", New: " + identityKey.hashCode())
      IdentityUtil.markIdentityUpdate(context, recipientId)
    }
    */
  }

  fun delete(addressName: String) {
  writableDatabase
    .delete(TABLE_NAME)
    .where("${ADDRESS} = ?", addressName)
    .run()
  }

  private fun getIdentityRecord(addressName: String): Optional<TI_IdentityRecord> {
  if(FIRST_USE == null || TIMESTAMP == null || NONBLOCKING_APPROVAL == null){
    throw AssertionError("Accessed one of the exported Identity Table constants before they were assigned.")
  }
  return readableDatabase
    .select()
    .from(IdentityTableGlue.TABLE_NAME)
    .where("${ADDRESS} = ?", addressName)
    .run()
    .firstOrNull { cursor ->
      TI_IdentityRecord(
        recipientId = RecipientId.fromSidOrE164(cursor.requireNonNullString(ADDRESS)),
        identityKey = IdentityKey(Base64.decode(cursor.requireNonNullString(IDENTITY_KEY)), 0),
        verifiedStatus = VerifiedStatus.forState(cursor.requireInt(VERIFIED)),
        firstUse = cursor.requireBoolean(FIRST_USE!!),
        timestamp = cursor.requireLong(TIMESTAMP!!),
        nonblockingApproval = cursor.requireBoolean(NONBLOCKING_APPROVAL!!)
      )
    }
    .toOptional()
  }

  private fun hasMatchingKey(addressName: String, identityKey: IdentityKey): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("${ADDRESS} = ? AND ${IDENTITY_KEY} = ?", addressName, Base64.encodeBytes(identityKey.serialize()))
      .run()
  }

  private fun hasMatchingStatus(addressName: String, identityKey: IdentityKey, verifiedStatus: VerifiedStatus): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("${ADDRESS} = ? AND ${IDENTITY_KEY} = ? AND ${VERIFIED} = ?", addressName, Base64.encodeBytes(identityKey.serialize()), verifiedStatus.toInt())
      .run()
  }

  private fun saveIdentityInternal(
    addressName: String,
    recipientId: RecipientId,
    identityKey: IdentityKey,
    verifiedStatus: VerifiedStatus,
    firstUse: Boolean,
    timestamp: Long,
    nonBlockingApproval: Boolean
  ) {
    val contentValues = contentValuesOf(
      ADDRESS to addressName,
      IDENTITY_KEY to Base64.encodeBytes(identityKey.serialize()),
      TIMESTAMP to timestamp,
      VERIFIED to verifiedStatus.toInt(),
      NONBLOCKING_APPROVAL to if (nonBlockingApproval) 1 else 0,
      FIRST_USE to if (firstUse) 1 else 0
    )
    writableDatabase.replace(TABLE_NAME, null, contentValues)
    //EventBus.getDefault().post(IdentityRecord(recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval))
  }

  // "TI_GLUE: eNT9XAHgq0lZdbQs2nfH /start"
  /**
   * Trusted Introductions: We differentiate between a direct verification <code>DIRECTLY_VERIFIED</code> (via. QR code)
   * and a weaker, manual verification <code>MANUALLY_VERIFIED</code>. Additionally, a user can become verified by the
   * trusted introductions mechanism <code>TRUSTINGLY_INTRODUCED</code>. A user that has been trustingly introduced and
   * directly verified is <code>DUPLEX_VERIFIED</code>, the strongest level.
   * A user can always manually reset the trust to be unverified.
   *
   * TODO: More decoupling by building an identity shadow table?
   *
   */
  enum class VerifiedStatus {
    DEFAULT, MANUALLY_VERIFIED, UNVERIFIED, DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED;

    fun toInt(): Int {
      return when (this) {
        DEFAULT -> 0
        MANUALLY_VERIFIED -> 1
        UNVERIFIED -> 2
        DIRECTLY_VERIFIED -> 3
        INTRODUCED -> 4
        DUPLEX_VERIFIED -> 5
      }
    }

    companion object {
      @JvmStatic
      fun forState(state: Int): VerifiedStatus {
        return when (state) {
          0 -> DEFAULT
          1 -> MANUALLY_VERIFIED
          2 -> UNVERIFIED
          3 -> DIRECTLY_VERIFIED
          4 -> INTRODUCED
          5 -> DUPLEX_VERIFIED
          else -> throw java.lang.AssertionError("No such state: $state")
        }
      }

      @JvmStatic
      fun toVanilla(state: Int): Int {
        val s = forState(state)
        return when (s) {
          DEFAULT -> 0
          DIRECTLY_VERIFIED -> 1
          INTRODUCED -> 1
          DUPLEX_VERIFIED -> 1
          MANUALLY_VERIFIED -> 1
          UNVERIFIED -> 2
        }
      }

      /**
       * Much of the code relies on checks of the verification status that are not interested in the finer details.
       * This function can now be called instead of doing 4 comparisons manually.
       * Do not use this to decide if trusted introduction is allowed.
       * @return True is verified, false otherwise.
       */
      @JvmStatic
      fun isVerified(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          INTRODUCED -> true
          DUPLEX_VERIFIED -> true
          MANUALLY_VERIFIED -> true
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }


      /**
       * Adding this in order to be able to change my mind easily on what should unlock a TI.
       * For now, only direct verification unlocks forwarding a contact's public key,
       * in order not to propagate malicious verifications further than one connection.
       *
       * @param verifiedStatus the verification status to be checked
       * @return true if strongly enough verified to unlock forwarding this contact as a
       * trusted introduction, false otherwise
       */
      @JvmStatic
      fun ti_forwardUnlocked(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          DUPLEX_VERIFIED -> true
          INTRODUCED -> false
          MANUALLY_VERIFIED -> false
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }

      /**
       * A recipient can only receive TrustedIntroductions iff they have previously been strongly verified.
       * This function exists as it's own thing to allow for flexible changes.
       *
       * @param verifiedStatus The verification status of the recipient.
       * @return True if this recipient can receive trusted introductions.
       */
      @JvmStatic
      fun ti_recipientUnlocked(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          DUPLEX_VERIFIED -> true
          //INTRODUCED: false (if someone is being MiTmed, an introduction could be sensitive data. So you should be sure who you are talking to before you forward)
          //TODO: Both versions of this have their own pros and cons... Which one should it be?
          // for now, opting to unlock also on introduced in order to give more room to play for the study
          INTRODUCED -> true
          MANUALLY_VERIFIED -> false
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }

      /**
       * Returns true for any non-trivial positive verification status.
       * Used to prompt user when clearing a verification status that is not trivially recoverable and to decide
       * if a channel is secure enough to forward an introduction over.
       */
      @JvmStatic
      fun stronglyVerified(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          DUPLEX_VERIFIED -> true
          INTRODUCED -> true
          MANUALLY_VERIFIED -> false
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }
    }
  }
}

data class IdentityTableExports(val FIRST_USE: String, val TIMESTAMP: String, val NONBLOCKING_APPROVAL: String, val allDatabaseKeys: ArrayList<String>)