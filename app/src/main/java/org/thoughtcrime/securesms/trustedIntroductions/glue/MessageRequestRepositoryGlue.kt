/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.trustedIntroductions.glue

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.dependencies.AppDependencies.application
import org.thoughtcrime.securesms.dependencies.AppDependencies.signalServiceMessageSender
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException

interface MessageRequestRepositoryGlue {
  companion object {
    /**
     * TODO: Do I need some kind of callback to modify the banner as the results come in?
     *
     * When a new conversation is initiated by us, we need to check the introduction database for existing introductions for that service ID.
     * For each introduction that was present, we must shift them from the 'unknown' state to their 'known' counterpart (FSM transitions in TI_Database) &
     * check if they must be turned stale, by comparing the current Identity Key (hits the network) with what we have in the introductions.
     * Iff an accepted Introduction is present at the end of this process, we *may* need to adjust the verification state (call FSM logic in TI_Identity_Database).
     *
     * @param recipient the new conversation recipient
     */
    @JvmStatic
    @WorkerThread
    fun handleNewUnknownRecipient(recipient: Recipient) {
      val serviceId = recipient.requireServiceId()
      if (tiDatabase.atLeastOneIntroductionIsUnknown(serviceId.toString())) {
        // Fetch the identity key of the new recipient
        val bundles: List<PreKeyBundle>
        try {
          bundles = signalServiceMessageSender.getPreKeys(SignalServiceAddress(serviceId), null, SignalServiceAddress.DEFAULT_DEVICE_ID, false)
          val s = tiDatabase.handleUnknownIntroductions(serviceId.toString(), TI_Utils.encodeIdentityKey(bundles[0].identityKey))
          // at least one 'unknown' introduction for this service ID existed and this is the highest priority state (as defined in TI_DB handleUnknownIntroductions)
          if (s != null) {
            // Now check if the verified status needs to be updated.
            val previousVerificationState = tiIdentityTable.getVerifiedStatus(recipient.id)
            tiIdentityTable.modifyIntroduceeVerification(
              serviceId.toString(),
              previousVerificationState,
              s,
              String.format(
                LOG_MSG,
                recipient.getDisplayName(application.applicationContext)
              )
            )
          }
        } catch (e: IOException) {
          Log.e(
            TAG, """Could not fetch keys for recipient %s though there wer preexisting introductions...:
${e.stackTraceToString()}"""
          )
          // TODO: This should probably schedule a job that retries this in the background as we may have consistency issues further down the line if it does not happen here.
        }
      }
    }

    val TAG: String = String.format(TI_Utils.TI_LOG_TAG, tag(MessageRequestRepositoryGlue::class.java))

    private const val LOG_MSG: String = "New Recipient: %s had a preexisting 'unknown' introduction."
  }
}
