package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN
import java.util.Date

/**
 * Asks user if they really want to delete the introduction. Text changes depending on which management screen the user is on.
 */
object DeleteIntroductionDialog {
  private val TAG = String.format(TI_Utils.TI_LOG_TAG, tag(DeleteIntroductionDialog::class.java))

  @JvmStatic
  fun show(context: Context, introductionId: Long, introduceeName: String, introducerName: String, date: Date, f: DeleteIntroduction) {
    val builder = AlertDialog.Builder(context).setTitle(R.string.DeleteIntroductionDialog__Title)
    val text = context.getString(R.string.DeleteIntroductionDialog__Delete_Introduction, introducerName, introduceeName, INTRODUCTION_DATE_PATTERN.format(date))
    builder.setMessage(text)
    builder.setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int -> dialog.dismiss() }
      .setPositiveButton(R.string.delete) { dialog: DialogInterface, which: Int ->
        dialog.dismiss()
        f.deleteIntroduction(introductionId)
      }
    builder.show()
  }

  interface DeleteIntroduction {
    fun deleteIntroduction(introductionId: Long)
  }
}
