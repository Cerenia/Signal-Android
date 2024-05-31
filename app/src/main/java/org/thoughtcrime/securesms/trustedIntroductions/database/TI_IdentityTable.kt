package org.thoughtcrime.securesms.trustedIntroductions.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.TI_LOG_TAG
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus
import org.thoughtcrime.securesms.util.Base64


class TI_IdentityTable internal constructor(context: Context?, databaseHelper: SignalDatabase?): DatabaseTable(context, databaseHelper), IdentityTableGlue {

  companion object {
    private val TAG = TI_LOG_TAG.format(Log.tag(TI_IdentityTable::class.java))
    const val TABLE_NAME = "TI_shadow_identities"
    private const val ID = "_id"
    const val ADDRESS = "address"//serviceID
    const val VERIFIED = "verified"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $ADDRESS INTEGER UNIQUE, 
        $VERIFIED INTEGER DEFAULT 0
      )
    """
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
    return readableDatabase.query(TABLE_NAME, arrayOf(ADDRESS), selectionBuilder.toString(), states, null, null, null)
  }

  override fun getVerifiedStatus(id: RecipientId?): VerifiedStatus {
    val recipient = Recipient.resolved(id!!)
    if (recipient.hasServiceId) {
      readableDatabase
        .select()
        .from(TABLE_NAME)
        .where("${IdentityTable.ADDRESS} = ?", recipient.requireServiceId().toString())
        .run()
        .use { cursor ->
          if (!cursor.moveToFirst()){
            Log.w(TAG, "Recipient: $id, with service id: ${recipient.serviceId.get()} was not found in the table. Returned default verification status.")
            return VerifiedStatus.DEFAULT // this recipient is not recorded in the table -> default verification state.
          } else {
            assert(cursor.count == 1) { "$TAG table returned more than one recipient with service ID: ${recipient.serviceId.get()}!!" }
            return VerifiedStatus.forState(cursor.getInt(cursor.getColumnIndexOrThrow(VERIFIED)))
          }
        }
    } else {
      Log.w(TAG, "Recipient with recipient ID: $id, did not have an associated service id. Returned default verification status.")
      return VerifiedStatus.DEFAULT
    }
  }

  override fun setVerifiedStatus(id: RecipientId, newStatus: VerifiedStatus): Boolean {
    val serviceID = Recipient.live(id).resolve().requireServiceId().toString()
    val contentValues = contentValuesOf(
      ADDRESS to serviceID,
      VERIFIED to newStatus.toInt()
    )
    val res = writableDatabase.replace(TABLE_NAME, null, contentValues)
    if(res < 0){
        throw AssertionError("$TAG: Error inserting recipient: ${id} with status $newStatus into TI_IdentityTable!")
      } else {
        Log.i(TAG, "Successfully inserted recipient $id with service id:$serviceID and status: $newStatus")
      }
    return true
  }
}