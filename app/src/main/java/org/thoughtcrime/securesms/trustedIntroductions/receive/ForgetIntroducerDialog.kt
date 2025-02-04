package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN
import java.util.Date

/**
 * Asks user if they really want to forget who made an introduction.
 */
object ForgetIntroducerDialog {
  private val TAG = String.format(TI_Utils.TI_LOG_TAG, tag(ForgetIntroducerDialog::class.java))

  @JvmStatic
  fun show(context: Context, introductionId: Long, introduceeName: String, introducerName: String, date: Date, f: ForgetIntroducer) {
    val builder = MaterialAlertDialogBuilder(context).setTitle(R.string.ForgetIntroducerDialog__Title)
    // TODO: do we still want to differentiate? or can we get rid of t?
    val text = context.getString(R.string.ForgetIntroducerDialog__Forget_Introducer_ALL, introducerName, introduceeName, INTRODUCTION_DATE_PATTERN.format(date))
    builder.setMessage(text)
    builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
      .setPositiveButton(R.string.ForgetIntroducerDialog__forget) { dialog: DialogInterface, which: Int ->
        dialog.dismiss()
        f.forgetIntroducer(introductionId)
      }
    builder.show()
  }

  interface ForgetIntroducer {
    fun forgetIntroducer(introductionId: Long)
  }
}
