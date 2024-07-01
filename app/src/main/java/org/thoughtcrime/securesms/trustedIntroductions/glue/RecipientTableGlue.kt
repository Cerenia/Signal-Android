/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.database.Cursor
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

interface RecipientTableGlue {

  companion object statics{
    val SERVICE_ID: String = RecipientTable.STORAGE_SERVICE_ID
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

    fun getCursorForSendingTI(recipientIds: Set<RecipientId?>?): Cursor? {
      val query = StringBuilder()
      val ID = RecipientTable.ID
      if (recipientIds != null) {
        if(recipientIds.isNotEmpty()){
          (1 until recipientIds.size).map{
            query.append("$ID=? OR ")
          }
          query.append("$ID=?")
        }
        val identifiers = recipientIds.map { id -> id!!.toLong().toString() }
        return SignalDatabase.recipients.querySignalContacts(RecipientTable.ContactSearchQuery(query.toString(), false))
      } else {
        return null
      }
    }

    fun getCursorForReceivingTI(serializedAcis: List<String>): Cursor? {
      val query = buildService_ID_Query(serializedAcis)
      return SignalDatabase.recipients.querySignalContacts(RecipientTable.ContactSearchQuery(query, false))
    }

    private fun buildService_ID_Query(serializedServiceIds: List<String>) : String{
      val query = StringBuilder();
      if(!serializedServiceIds.isEmpty()){
        (1 until serializedServiceIds.size).map{
          query.append("$SERVICE_ID=? OR ")
        }
        query.append("$SERVICE_ID=?")
      }
      return query.toString()
    }

  }
}
