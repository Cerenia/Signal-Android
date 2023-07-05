package org.thoughtcrime.securesms.trustedIntroductions.database

import android.database.Cursor
import android.content.Context
import org.signal.core.util.firstOrNull
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.toOptional
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.TI_ADDRESS_PROJECTION
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VERIFIED
import org.thoughtcrime.securesms.util.Base64
import java.lang.StringBuilder
import java.util.Optional


class TI_IdentityTable internal constructor(context: Context?, databaseHelper: SignalDatabase?): DatabaseTable(context, databaseHelper), IdentityTableGlue{

  companion object Singleton{
    var inst: IdentityTableGlue? = null
    var FIRST_USE: String? = null
    var TIMESTAMP: String? = null
    var NONBLOCKING_APPROVAL: String? = null
    var ALL_KEYS: ArrayList<String> = ArrayList<String>()

    fun setInstance(intfHandle: IdentityTableGlue ){
      if (inst != null){
        throw AssertionError("Attempted to reassign trustedIntroduction Identity Table singleton!")
      }
      inst = intfHandle
    }

    /**
     * Sets any variables that are usually private in parent class for later use in internal functions.
     */
    fun setExportedPrivates(exports: IdentityTableExports){
      if (FIRST_USE != null || TIMESTAMP != null || NONBLOCKING_APPROVAL != null || !ALL_KEYS.isEmpty()){
        throw AssertionError("Attempted to reassign exports to Identity Table singleton!")
      }
      FIRST_USE = exports.FIRST_USE
      TIMESTAMP = exports.TIMESTAMP
      NONBLOCKING_APPROVAL = exports.NONBLOCKING_APPROVAL
      ALL_KEYS.addAll(exports.allDatabaseKeys)
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
    for (e in IdentityTable.VerifiedStatus.values()) {
      if (IdentityTable.VerifiedStatus.ti_forwardUnlocked(e)) {
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
    return readableDatabase.query(IdentityTable.TABLE_NAME, arrayOf<String>(TI_ADDRESS_PROJECTION), selectionBuilder.toString(), states, null, null, null)
  }

  override fun getVerifiedStatus(id: RecipientId?): IdentityTable.VerifiedStatus {
    val recipient = Recipient.resolved(id!!)
    if(recipient.hasServiceId()){
      val ir = getIdentityRecord(recipient.requireServiceId().toString())
      return ir.map(IdentityRecord::verifiedStatus).orElse(IdentityTable.VerifiedStatus.UNVERIFIED) // fail closed
    } else {
      return IdentityTable.VerifiedStatus.DEFAULT
    }
  }

  private fun getIdentityRecord(addressName: String): Optional<IdentityRecord> {
    if(FIRST_USE == null || TIMESTAMP == null || NONBLOCKING_APPROVAL == null){
      throw AssertionError("Accessed one of the exported Identity Table constants before they were assigned.")
    }
    return readableDatabase
      .select()
      .from(IdentityTableGlue.TABLE_NAME)
      .where("${IdentityTable.ADDRESS} = ?", addressName)
      .run()
      .firstOrNull { cursor ->
        IdentityRecord(
          recipientId = RecipientId.fromSidOrE164(cursor.requireNonNullString(IdentityTable.ADDRESS)),
          identityKey = IdentityKey(Base64.decode(cursor.requireNonNullString(IdentityTable.IDENTITY_KEY)), 0),
          verifiedStatus = IdentityTable.VerifiedStatus.forState(cursor.requireInt(IdentityTable.VERIFIED)),
          firstUse = cursor.requireBoolean(FIRST_USE!!),
          timestamp = cursor.requireLong(TIMESTAMP!!),
          nonblockingApproval = cursor.requireBoolean(NONBLOCKING_APPROVAL!!)
        )
      }
      .toOptional()
  }
}

data class IdentityTableExports(val FIRST_USE: String, val TIMESTAMP: String, val NONBLOCKING_APPROVAL: String, val allDatabaseKeys: ArrayList<String>)