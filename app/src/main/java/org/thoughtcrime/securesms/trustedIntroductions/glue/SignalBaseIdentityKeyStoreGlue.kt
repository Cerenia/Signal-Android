package org.thoughtcrime.securesms.trustedIntroductions.glue

import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log.e
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiDatabase
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils

interface SignalBaseIdentityKeyStoreGlue {
  companion object {
    @JvmStatic
    fun turnAllIntroductionsStale(recipientId: RecipientId) {
      // Security nr. changed, change all introductions for this introducee to stale
      SignalExecutors.BOUNDED.execute {
        val recipient = resolved(recipientId)
        val res = tiDatabase.turnAllIntroductionsStale(recipient.requireServiceId().toString())
        if (!res) {
          e(TAG, "Error occurred while turning all introductions stale for recipient: $recipientId")
        }
      }
    }

    val TAG: String = String.format(TI_Utils.TI_LOG_TAG, tag(SignalBaseIdentityKeyStoreGlue::class.java))
  }
}
