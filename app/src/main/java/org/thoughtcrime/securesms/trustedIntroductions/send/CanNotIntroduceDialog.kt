package org.thoughtcrime.securesms.trustedIntroductions.send

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity

/**
 * Utility to display a dialog when the user tries to use the introduction functionality with a contact
 * they have not strongly verified, an SMS contact or a group.
 */
object CanNotIntroduceDialog {
  fun show(context: Context, identityRecord: IdentityRecord?, conversationType: ConversationType) {
    val builder = AlertDialog.Builder(context).setTitle(R.string.CanNotIntroduceDialog__Cant_introduce)

    when (conversationType) {
      ConversationType.SMS -> {
        builder.setMessage(R.string.CanNotIntroduceDialog__SMS_contact_not_applicable)
        ConversationType.setNegativeButtonNotSupported(builder)
      }

      ConversationType.GROUP -> {
        builder.setMessage(R.string.CanNotIntroduceDialog__Group_not_yet_supported)
        ConversationType.setNegativeButtonNotSupported(builder)
      }

      ConversationType.SINGLE_SECURE_TEXT -> {
        builder.setMessage(R.string.CanNotIntroduceDialog__direct_verification_needed_for_trusted_introduction)
        builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
          .setPositiveButton(R.string.CanNotIntroduceDialog__verify) { dialog: DialogInterface?, which: Int ->
            if (identityRecord != null) {
              val intent = VerifyIdentityActivity.newIntent(context, identityRecord)
              context.startActivity(intent)
            } else {
              // TODO: More sensible error handling? What does it mean if the identityRecord is empty at this point? No longer a contact??
              Toast.makeText(context, R.string.CanNotIntroduceDialog__Identity_record_empty, Toast.LENGTH_LONG).show()
            }
          }
      }

      else -> {
        builder.setMessage(R.string.CanNotIntroduceDialog__direct_verification_needed_for_trusted_introduction)
        builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
          .setPositiveButton(R.string.CanNotIntroduceDialog__verify) { dialog: DialogInterface?, which: Int ->
            if (identityRecord != null) {
              val intent = VerifyIdentityActivity.newIntent(context, identityRecord)
              context.startActivity(intent)
            } else {
              Toast.makeText(context, R.string.CanNotIntroduceDialog__Identity_record_empty, Toast.LENGTH_LONG).show()
            }
          }
      }
    }
    builder.show()
  }

  enum class ConversationType {
    SMS,
    GROUP,
    SINGLE_SECURE_TEXT;

    companion object {
      fun setNegativeButtonNotSupported(b: AlertDialog.Builder) {
        b.setNegativeButton(android.R.string.ok) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
      }
    }
  }
}
