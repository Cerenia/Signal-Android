package org.thoughtcrime.securesms.trustedIntroductions.database

import android.content.Context
import android.database.Cursor
import androidx.annotation.WorkerThread
import androidx.core.content.contentValuesOf
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.i
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.TI_LOG_TAG
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus


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


  override fun saveIdentity(addressName: String, verifiedStatus: VerifiedStatus): Boolean {
    val contentValues = contentValuesOf(
      ADDRESS to addressName,
      VERIFIED to verifiedStatus.toInt()
    )
    var res = writableDatabase.insert(TABLE_NAME, null, contentValues)
    if(res < 0){
      // Try a replace instead
      res = writableDatabase.replace(TABLE_NAME, null, contentValues)
      if (res < 0){
        throw AssertionError("$TAG: Error replacing recipient: $addressName with status $verifiedStatus into TI_IdentityTable!")
      } else {
        Log.i(TAG, "Successfully replaced the verification status of recipient with service id:$addressName to: $verifiedStatus")
      }
    } else {
      Log.i(TAG, "Successfully added recipient with service id:$addressName and status: $verifiedStatus")
    }
    return true
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

  /**
   * Parts of FSM that react to changing introductions implemented here.
   * PRE: introducee exists in recipient and identity table
   * @param introduceeServiceId The service ID of the recipient whose verification status may change
   * @param previousIntroduceeVerification the previous verification status of the introducee.
   * @param newIntroductionState the new state of the introduction that changed. PRE: May not be PENDING
   * @param logmessage what to print to logcat iff verification status of introducee was modified
   */
  @WorkerThread
  override fun modifyIntroduceeVerification(introduceeServiceId: String, previousIntroduceeVerification: VerifiedStatus, newIntroductionState: TI_Database.State, logmessage: String) {
    val newIntroduceeVerification = when (newIntroductionState) {
        TI_Database.State.PENDING -> throw AssertionError("$TAG Precondition violation! newState may not be PENDING")
        // Any stale state leads to unverified
        TI_Database.State.STALE_PENDING, TI_Database.State.STALE_ACCEPTED, TI_Database.State.STALE_REJECTED, TI_Database.State.STALE_ACCEPTED_CONFLICTING,
        TI_Database.State.STALE_REJECTED_CONFLICTING, TI_Database.State.STALE_PENDING_CONFLICTING -> VerifiedStatus.UNVERIFIED
        // An accepted introduction may lead to the introducee verification state:
        TI_Database.State.ACCEPTED -> when (previousIntroduceeVerification) {
            // Becoming or staying strongly verified
            VerifiedStatus.DUPLEX_VERIFIED, VerifiedStatus.DIRECTLY_VERIFIED -> VerifiedStatus.DUPLEX_VERIFIED
            // Staying or becoming introduced
            VerifiedStatus.DEFAULT, VerifiedStatus.UNVERIFIED, VerifiedStatus.INTRODUCED, VerifiedStatus.MANUALLY_VERIFIED -> VerifiedStatus.INTRODUCED
            // Or staying in the suspected compromised state
            VerifiedStatus.SUSPECTED_COMPROMISE -> VerifiedStatus.SUSPECTED_COMPROMISE
          }
        // A rejected introduction may lead to the introducee verification state:
        TI_Database.State.REJECTED -> when (previousIntroduceeVerification) {
            // Staying the same
            VerifiedStatus.DIRECTLY_VERIFIED, VerifiedStatus.MANUALLY_VERIFIED, VerifiedStatus.DEFAULT, VerifiedStatus.UNVERIFIED -> previousIntroduceeVerification
            // Potentially degrading in status
            VerifiedStatus.DUPLEX_VERIFIED -> {
              if(SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_Database.State.ACCEPTED, introduceeServiceId)) VerifiedStatus.DUPLEX_VERIFIED
              else VerifiedStatus.DIRECTLY_VERIFIED
            }
            VerifiedStatus.INTRODUCED -> {
              if(SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_Database.State.ACCEPTED, introduceeServiceId)) VerifiedStatus.INTRODUCED
              else VerifiedStatus.UNVERIFIED
            }
            // Or staying in the suspected compromised state
            VerifiedStatus.SUSPECTED_COMPROMISE -> VerifiedStatus.SUSPECTED_COMPROMISE
          }
        // An accepted conflicting introduction will lead to a suspected compromise
        TI_Database.State.ACCEPTED_CONFLICTING -> VerifiedStatus.SUSPECTED_COMPROMISE
        // A rejected conflicting introduction might move the introducee out of the conflicting state or keep the state the same
        TI_Database.State.REJECTED_CONFLICTING -> when (previousIntroduceeVerification) {
            VerifiedStatus.SUSPECTED_COMPROMISE -> {
              if(SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_Database.State.ACCEPTED_CONFLICTING, introduceeServiceId)) VerifiedStatus.SUSPECTED_COMPROMISE
              else {
                if (SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_Database.State.ACCEPTED, introduceeServiceId)) VerifiedStatus.INTRODUCED
                else VerifiedStatus.UNVERIFIED
              }
            }
            VerifiedStatus.INTRODUCED, VerifiedStatus.UNVERIFIED, VerifiedStatus.DIRECTLY_VERIFIED, VerifiedStatus.MANUALLY_VERIFIED, VerifiedStatus.DEFAULT,
            VerifiedStatus.DUPLEX_VERIFIED -> previousIntroduceeVerification
          }
        TI_Database.State.PENDING_CONFLICTING -> previousIntroduceeVerification
      }
    // Finally update the verification state and log
    val rid = RecipientId.fromSidOrE164(introduceeServiceId)
    TI_Utils.updateContactsVerifiedStatus(rid, TI_Utils.getIdentityKey(rid) , newIntroduceeVerification)
    i(TAG, logmessage)
  }
}