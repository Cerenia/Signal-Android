package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.content.Context
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.recipients.Recipient

interface ConversationTitleViewGlue {
  companion object {
    @JvmStatic
    fun setIndividualRecipientTitle(recipient: Recipient, context: Context, title: TextView, subtitle: TextView, updateVisibility: Runnable) {
      val displayName = recipient.getDisplayName(context)
      title.text = displayName
      val verifiedStatus = tiIdentityTable.getVerifiedStatus(recipient.id)
      when (verifiedStatus) {
        IdentityTableGlue.VerifiedStatus.MANUALLY_VERIFIED -> subtitle.setText(R.string.ConversationTitleView__manually_verified)
        IdentityTableGlue.VerifiedStatus.DIRECTLY_VERIFIED -> subtitle.setText(R.string.ConversationTitleView__directly_verified)
        IdentityTableGlue.VerifiedStatus.DUPLEX_VERIFIED -> subtitle.setText(R.string.ConversationTitleView__duplex)
        IdentityTableGlue.VerifiedStatus.INTRODUCED -> subtitle.setText(R.string.ConversationTitleView__introduced)
        IdentityTableGlue.VerifiedStatus.SUSPECTED_COMPROMISE -> subtitle.setText(R.string.ConversationTitleView__suspected_compromise)
        else -> subtitle.setText(R.string.ConversationTitleView__unverified) // Should never be visible in this state
      }
      updateVisibility.run()
    }
  }
}
