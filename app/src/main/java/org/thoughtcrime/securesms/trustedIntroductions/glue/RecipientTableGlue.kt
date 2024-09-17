/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.annotation.SuppressLint
import android.database.Cursor
import org.signal.glide.Log.i
import org.thoughtcrime.securesms.audio.TAG
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId

interface RecipientTableGlue {

  companion object statics{
    val SERVICE_ID: String = RecipientTable.ACI_COLUMN
    // All the values returned in the search projection
    val ID: String = RecipientTable.ID
    val SYSTEM_JOINED_NAME: String = RecipientTable.SYSTEM_JOINED_NAME
    val E164: String = RecipientTable.E164
    val EMAIL: String = RecipientTable.EMAIL
    val SYSTEM_PHONE_LABEL: String = RecipientTable.SYSTEM_PHONE_LABEL
    val SYSTEM_PHONE_TYPE: String = RecipientTable.SYSTEM_PHONE_TYPE
    val REGISTERED: String = RecipientTable.REGISTERED
    val ABOUT: String = RecipientTable.ABOUT
    val ABOUT_EMOJI: String = RecipientTable.ABOUT_EMOJI
    val EXTRAS: String = RecipientTable.EXTRAS
    val GROUPS_IN_COMMON: String = RecipientTable.GROUPS_IN_COMMON
    // The following values are coalesced in the order they appear
    // @see RecipientTable.querySignalContacts()
    //val PROFILE_JOINED_NAME: String = RecipientTable.PROFILE_JOINED_NAME
    //val PROFILE_GIVEN_NAME: String = RecipientTable.PROFILE_GIVEN_NAME
    //val SEARCH_PROFILE_NAME: String = RecipientTable.SEARCH_PROFILE_NAME
    //val NICKNAME_JOINED_NAME: String = RecipientTable.NICKNAME_JOINED_NAME
    //val NICKNAME_GIVEN_NAME: String = RecipientTable.NICKNAME_GIVEN_NAME
    //val SYSTEM_JOINED_NAME: String = RecipientTable.SYSTEM_JOINED_NAME
    //val SYSTEM_GIVEN_NAME: String = RecipientTable.SYSTEM_GIVEN_NAME
    //val USERNAME: String = RecipientTable.USERNAME
    // -> as SORT_NAME
    val SORT_NAME: String = RecipientTable.SORT_NAME

    fun getRecordsForSendingTI(recipientIds: Set<RecipientId>): Map<RecipientId, RecipientRecord> {
      return SignalDatabase.recipients.getRecords(recipientIds)
    }

    @JvmStatic
    fun getRecordsForReceivingTI(serializedAcis: MutableList<String>): Map<RecipientId, RecipientRecord> {
      return getRecipientIdsFromACIs(null, serializedAcis)
    }

    /**
     * Returns a cursor populated with the Recipients that correspond to the
     * ones that are a valid target for an introduction.
     * @See IdentityTable.getCursorForTIUnlocked()
     */
    @JvmStatic
    @SuppressLint("Range")
    fun getValidTI_Candidates(cursorForTIUnlocked: Cursor): Map<RecipientId, RecipientRecord> {
      return getRecipientIdsFromACIs(cursorForTIUnlocked)
    }

    @SuppressLint("Range")
    private fun getRecipientIdsFromACIs(cursor: Cursor? = null, acis: Collection<String>? = null): Map<RecipientId, RecipientRecord>{
      val serviceIdentifyers = arrayListOf<String>()
      val recipientIds: MutableSet<RecipientId> = hashSetOf()
      if (cursor == null && acis == null){
        throw IllegalArgumentException("Either cursor or acis must not be null!")
      } else if (cursor != null){
        if(cursor.moveToFirst()){
          cursor.use {
            while (!it.isAfterLast) {
              serviceIdentifyers.add(it.getString(it.getColumnIndex(IdentityTable.ADDRESS)))
              it.moveToNext()
            }
          }
        }
      } else {
        serviceIdentifyers.addAll(acis!!)
      }
      serviceIdentifyers.forEach { aci ->
        try {
          val rid: RecipientId = SignalDatabase.recipients.getByAci(ServiceId.ACI.parseOrThrow(aci)).get()
          recipientIds.add(rid)
        } catch (e: NoSuchElementException) {
          // Just don't add a recipient that doesn't exist already
          i(TAG, "Recipient with service ID $aci was not present in the database.")
        }
      }
      return SignalDatabase.recipients.getRecords(recipientIds)
    }
  }
}
