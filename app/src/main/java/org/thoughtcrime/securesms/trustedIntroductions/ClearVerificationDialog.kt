package org.thoughtcrime.securesms.trustedIntroductions

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.Button
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus
import org.thoughtcrime.securesms.trustedIntroductions.glue.VerifyDisplayFragmentGlue

/**
 * Dialog is displayed if a user wants to clear a verification status that is higher than 'manually' verified,
 * since this is an operation that is not trivially undone.
 * Only valid with the verification status DIRECTLY_VERIFIED, INTRODUCED or DUPLEX_VERIFIED
 */
object ClearVerificationDialog {
  private var clearVerification = false
  private var TAG = "TI_ " + ClearVerificationDialog::class

  private fun userInteraction(dialog: DialogInterface, which: Int) {
    if (which == DialogInterface.BUTTON_POSITIVE) {
      clearVerification = true
    }
    dialog.dismiss()
  }

  /**
   * @param context        Caller context.
   * @param status         The contacts verification status. Must be strongly verified (@see IdentityTable)
   * @param recipientId    Our remote recipient ID
   * @param remoteIdentity Remote identity key
   * @param verifyButton   The button to be updated after the clearing operation.
   */
  @JvmStatic
  fun show(context: Context, status: VerifiedStatus, recipientId: RecipientId?, remoteIdentity: IdentityKey?, verifyButton: Button?) {
    assert(VerifiedStatus.stronglyVerified(status)) { "Unsupported Verification status" }

    clearVerification = false
    val builder = AlertDialog.Builder(context).setTitle(R.string.ClearVerificationDialog__Title)
    when (status) {
      VerifiedStatus.DIRECTLY_VERIFIED -> builder.setMessage(R.string.ClearVerificationDialog__Clear_directly_verified)
      VerifiedStatus.INTRODUCED -> builder.setMessage(R.string.ClearVerificationDialog__Clear_introduced)
      VerifiedStatus.DUPLEX_VERIFIED -> builder.setMessage(R.string.ClearVerificationDialog__Clear_duplex)
      else -> assert(false)
    }
    builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
      .setPositiveButton(R.string.ClearVerificationDialog__Positive) { dialog: DialogInterface, _: Int ->
        onClearVerification(recipientId, remoteIdentity, verifyButton)
        dialog.dismiss()
      }
      .show()
  }

  fun onClearVerification(recipientId: RecipientId?, remoteIdentity: IdentityKey?, verifyButton: Button?) {
    if (recipientId == null || remoteIdentity == null) {
      Log.w(TAG, "could not clear verification, empty recipientId or remoteIdentity")
      return
    }
    TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, VerifiedStatus.UNVERIFIED)
    VerifyDisplayFragmentGlue.updateVerifyButtonText(false, verifyButton)
  }
}

