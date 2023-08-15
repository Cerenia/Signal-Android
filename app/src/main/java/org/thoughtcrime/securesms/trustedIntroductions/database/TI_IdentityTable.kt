package org.thoughtcrime.securesms.trustedIntroductions.database

import android.database.Cursor
import android.content.Context
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.TI_ADDRESS_PROJECTION
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus
import org.thoughtcrime.securesms.util.Base64
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.lang.StringBuilder


class TI_IdentityTable internal constructor(context: Context?, databaseHelper: SignalDatabase?): DatabaseTable(context, databaseHelper), IdentityTableGlue {

  companion object Singleton {
    private val TAG = Log.tag(TI_IdentityTable::class.java)
    const val TABLE_NAME = "TI_shadow_identities"
    private const val ID = "_id"
    const val ADDRESS = "address"
    const val VERIFIED = "verified"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $ADDRESS INTEGER UNIQUE, 
        $VERIFIED INTEGER DEFAULT 0, 
      )
    """

    var TI_inst: IdentityTableGlue? = null
    private var signal_inst: IdentityTable? = null

    fun setInstance(intfHandle: IdentityTableGlue) {
      if (TI_inst != null) {
        throw AssertionError("Attempted to reassign trustedIntroduction Identity Table singleton!")
      }
      TI_inst = intfHandle
    }

    fun createSignalIdentityTable(context: Context?, databaseHelper: SignalDatabase?) {
      if (signal_inst != null) {
        throw AssertionError("Attempted to reassign signal Identity Table singleton!")
      }
      signal_inst = IdentityTable(context, databaseHelper)
    }
  }

  /**
   *
   * @return Returns a Cursor which iterates through all recipientIds that are unlocked for
   * trusted introductions (for which @see VerifiedStatus.tiUnlocked returns true)
   */
  override fun getCursorForTIUnlocked(): Cursor {
    val validStates: ArrayList<String> = ArrayList()
    // dynamically compute the valid states and query the Signal database for these contacts
    for (e in IdentityTableGlue.VerifiedStatus.values()) {
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
    return readableDatabase.query(TABLE_NAME, arrayOf<String>(ADDRESS), selectionBuilder.toString(), states, null, null, null)
  }

  override fun getVerifiedStatus(id: RecipientId?): VerifiedStatus {
    val recipient = Recipient.resolved(id!!)
    if (recipient.hasServiceId()) {
      val cursor = readableDatabase.query(TABLE_NAME, arrayOf(VERIFIED), String.format("%s=?", ADDRESS), arrayOf(recipient.serviceId.toString()), null, null, null)
      if (cursor.count < 1) {
        VerifiedStatus.DEFAULT // this recipient is not recorded in the table -> default verification state.
      } else {
        assert(cursor.count == 1) { "$TAG table returned more than one recipient with service ID: ${recipient.serviceId}!!" }
        VerifiedStatus.UNVERIFIED // fail closed. But assertion error makes this unreachable.
      }
    } else {
      Log.w(TAG, "Recipient with recipient ID: $id, did not have a service ID and could therefore not be found in the table.")
      VerifiedStatus.DEFAULT
    }
    return VerifiedStatus.UNVERIFIED // fail closed
  }
}