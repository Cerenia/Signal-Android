/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import org.thoughtcrime.securesms.keyvalue.SignalStore

class ChooseLocalTIBackupContract : ActivityResultContracts.GetContent() {
  override fun createIntent(context: Context, input: String): Intent {
    return super.createIntent(context, input).apply {
      putExtra(Intent.EXTRA_LOCAL_ONLY, true)
      if (Build.VERSION.SDK_INT >= 26) {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings.latestSignalBackupDirectory)
      }
    }
  }
}