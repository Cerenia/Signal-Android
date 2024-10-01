/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.backup

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.restore.RestoreViewModel

class NowChooseNormalBackupDialogue {
  companion object{

    fun show(context: Context, chooseNormalBackup: () -> Unit, viewModel: RestoreViewModel){
      MaterialAlertDialogBuilder(context)
        .setTitle(R.string.NowChooseNormalBackupDialogue__title)
        .setMessage(R.string.NowChooseNormalBackupDialogue__now_choose_normal_backup)
        .setPositiveButton(R.string.NowChooseNormalBackupDialogue__positive) { _, _ ->
          chooseNormalBackup()
        }
        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
          // Reset the TI backup filepath
          viewModel.setTIBackupFileUri(null)
          dialog.dismiss()
        }
        .show()
    }
  }
}