package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log.i
import org.signal.core.util.logging.Log.tag
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.dependencies.AppDependencies.jobManager
import org.thoughtcrime.securesms.dependencies.AppDependencies.protocolStore
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.trustedIntroductions.ClearVerificationDialog.show
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.updateContactsVerifiedStatus
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus.DIRECTLY_VERIFIED
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus.MANUALLY_VERIFIED
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus.UNVERIFIED
import org.thoughtcrime.securesms.util.IdentityUtil
import org.thoughtcrime.securesms.verify.VerifyDisplayFragment

interface VerifyDisplayFragmentGlue {
  private fun updateContactsVerifiedStatus(status: VerifiedStatus, recipient: Recipient, remoteIdentity: IdentityKey, activity: FragmentActivity) {
    val recipientId = recipient.id
    val sid = recipient.requireServiceId()
    i(TAG_TI, "Saving identity: $recipientId")
    SignalExecutors.BOUNDED.execute {
      ReentrantSessionLock.INSTANCE.acquire().use { _ ->
        val verified: Boolean = VerifiedStatus.isVerified(status)
        if (verified) {
          i(TAG_TI, "Saving identity: $recipientId")
          protocolStore.aci().identities()
            .saveIdentityWithoutSideEffects(
              recipientId,
              sid,
              remoteIdentity,
              VerifiedStatus.toVanilla(status),
              false,
              System.currentTimeMillis(),
              true
            )
        } else {
          protocolStore.aci().identities().setVerified(recipientId, remoteIdentity, VerifiedStatus.toVanilla(status))
        }

        // For other devices but the Android phone, we map the finer statuses to verified or unverified.
        jobManager
          .add(
            MultiDeviceVerifiedUpdateJob(
              recipientId,
              remoteIdentity,
              VerifiedStatus.toVanilla(status)
            )
          )
        StorageSyncHelper.scheduleSyncForDataChange()
        IdentityUtil.markIdentityVerified(activity, recipient, verified, false)
      }
    }
  }

  companion object {
    fun initializeVerifyButton(verified: Boolean, verifyButton: Button, recipientId: RecipientId, activity: FragmentActivity?, remoteIdentity: IdentityKey) {
      updateVerifyButtonText(verified, verifyButton)
      verifyButton.setOnClickListener((View.OnClickListener { button: View -> updateVerifyButtonLogic(button as Button, recipientId, activity, remoteIdentity) }))
    }

    fun extendBundle(extras: Bundle, verifiedState: Boolean) {
      extras.putBoolean(VERIFIED_STATE, verifiedState)
    }

    fun updateVerifyButtonText(verified: Boolean, verifyButton: Button) {
      if (verified) {
        verifyButton.setText(R.string.verify_display_fragment__clear_verification)
      } else {
        verifyButton.setText(R.string.verify_display_fragment__mark_as_verified)
      }
    }

    fun updateVerifyButtonLogic(verifyButton: Button, recipientId: RecipientId, activity: FragmentActivity?, remoteIdentity: IdentityKey) {
      // TODO: This needs a good refactoring since I want to be close to the original. I completely mangled this class.
      // Check the current verification status
      val previousStatus = tiIdentityTable.getVerifiedStatus(recipientId)
      i(TAG_TI, "Saving identity: $recipientId")
      if (VerifiedStatus.stronglyVerified(previousStatus)) {
        // TODO: when would this activity ever be null?
        if (activity != null) {
          // go through user check first.
          show(activity, previousStatus, recipientId, remoteIdentity, verifyButton)
        }
      } else if (previousStatus == MANUALLY_VERIFIED) {
        // manually verified, no user check necessary
        updateContactsVerifiedStatus(recipientId, remoteIdentity, UNVERIFIED)
        updateVerifyButtonText(false, verifyButton)
      } else {
        // Unverified or default, simply set to manually verified
        updateContactsVerifiedStatus(recipientId, remoteIdentity, MANUALLY_VERIFIED)
        updateVerifyButtonText(true, verifyButton)
      }
    }

    fun onSuccessfulVerification(recipientId: RecipientId, remoteIdentity: IdentityKey, verifyButton: Button) {
      // The fingerprint matched after a QR scan and we can update the users verification status
      updateContactsVerifiedStatus(recipientId, remoteIdentity, DIRECTLY_VERIFIED)
      updateVerifyButtonText(true, verifyButton)
    }

    const val VERIFIED_STATE: String = "verified_state"
    val TAG_TI: String = String.format(TI_Utils.TI_LOG_TAG, tag(VerifyDisplayFragment::class.java))
  }
}
