package org.thoughtcrime.securesms.trustedIntroductions

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Utility to display a dialog when the user tries to use the introduction functionality with a contact
 * they have not strongly verified.
 */
object CanNotIntroduceDialog {
  fun show(context: Context, recipientId: RecipientId) {
    val builder = AlertDialog.Builder(context)
      .setTitle(R.string.CanNotIntroduceDialog__Cant_introduce)
      .setMessage(R.string.CanNotIntroduceDialog__direct_verification_needed_for_trusted_introduction)
    builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
      .setPositiveButton(R.string.CanNotIntroduceDialog__verify) { dialog: DialogInterface, _: Int ->
        dialog.dismiss()
      }
      .show()
  }
}
