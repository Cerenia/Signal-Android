/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.choosebackup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentChooseBackupBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate
import org.thoughtcrime.securesms.restore.RestoreViewModel
// TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
import org.thoughtcrime.securesms.trustedIntroductions.glue.ChooseLocalTIBackupContract
import org.thoughtcrime.securesms.util.navigation.safeNavigate
// TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

/**
 * This fragment presents a button to the user to browse their local file system for a legacy backup file.
 */
class ChooseBackupFragment : LoggingFragment(R.layout.fragment_choose_backup) {
  private val sharedViewModel by activityViewModels<RestoreViewModel>()
  private val binding: FragmentChooseBackupBinding by ViewBinderDelegate(FragmentChooseBackupBinding::bind)

  private val pickMedia = registerForActivityResult(BackupFileContract()) {
    if (it != null) {
      onUserChoseBackupFile(it)
    } else {
      Log.i(TAG, "Null URI returned for backup file selection.")
    }
  }

  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  private val pickTIMedia = registerForActivityResult(ChooseLocalTIBackupContract()){
    if (it != null) {
      onUserChoseTIBackupFile(it)
    } else {
      Log.i(TAG, "Null URI returned for backup file selection.")
    }
  }
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(binding.chooseBackupFragmentTitle)
    binding.chooseBackupFragmentButton.setOnClickListener { onChooseBackupSelected() }

    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
    binding.chooseTiBackupFragmentButton.setOnClickListener { onChooseTIBackupSelected() }
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

    binding.chooseBackupFragmentLearnMore.text = HtmlCompat.fromHtml(String.format("<a href=\"%s\">%s</a>", getString(R.string.backup_support_url), getString(R.string.ChooseBackupFragment__learn_more)), 0)
    binding.chooseBackupFragmentLearnMore.movementMethod = LinkMovementMethod.getInstance()
  }

  private fun onChooseBackupSelected() {
    pickMedia.launch("application/octet-stream")
  }

  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  private fun onChooseTIBackupSelected() {
    pickTIMedia.launch("application/octet-stream")
  }

  private fun onUserChoseTIBackupFile(backupFileUri: Uri) {
    sharedViewModel.setTIBackupFileUri(backupFileUri)
    Toast.makeText(context, R.string.ChooseBackupFragment__now_choose_normal_backup, Toast.LENGTH_LONG).show()
    onChooseTIBackupSelected()
  }
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

  private fun onUserChoseBackupFile(backupFileUri: Uri) {
    sharedViewModel.setBackupFileUri(backupFileUri)
    NavHostFragment.findNavController(this).safeNavigate(ChooseBackupFragmentDirections.actionChooseLocalBackupFragmentToRestoreLocalBackupFragment())
  }

  private class BackupFileContract : ActivityResultContracts.GetContent() {
    override fun createIntent(context: Context, input: String): Intent {
      return super.createIntent(context, input).apply {
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        if (Build.VERSION.SDK_INT >= 26) {
          putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings.latestSignalBackupDirectory)
        }
      }
    }
  }

  companion object {
    private val TAG = Log.tag(ChooseBackupFragment::class.java)
  }
}
