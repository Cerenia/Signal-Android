/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.choosebackup

// TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
import android.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentChooseBackupBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.trustedIntroductions.glue.ChooseBackupFragmentGlue.OPEN_TI_FILE_REQUEST_CODE
import org.thoughtcrime.securesms.trustedIntroductions.glue.ChooseBackupFragmentGlue.onChooseTIBackup
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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(binding.chooseBackupFragmentTitle)
    binding.chooseBackupFragmentButton.setOnClickListener { onChooseBackupSelected() }

    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
    binding.chooseTiBackupFragmentButton.setOnClickListener {onChooseTIBackup(binding.chooseTiBackupFragmentButton, requireContext(), activity) }
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

    binding.chooseBackupFragmentLearnMore.text = HtmlCompat.fromHtml(String.format("<a href=\"%s\">%s</a>", getString(R.string.backup_support_url), getString(R.string.ChooseBackupFragment__learn_more)), 0)
    binding.chooseBackupFragmentLearnMore.movementMethod = LinkMovementMethod.getInstance()
  }

  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    val OPEN_FILE_REQUEST_CODE = 3862;
    val restore: ChooseBackupFragmentDirections.ActionRestore = ChooseBackupFragmentDirections.actionRestore()
    if (resultCode == Activity.RESULT_OK && data != null) {
      if (requestCode == OPEN_FILE_REQUEST_CODE) {
        restore.setUri(data.data)
      } else if (requestCode == OPEN_TI_FILE_REQUEST_CODE.toInt()) {
        restore.setTiUri(data.data)
      }
      SafeNavigation.safeNavigate(findNavController(requireView()), restore)
    }
  }
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end

  private fun onChooseBackupSelected() {
    pickMedia.launch("application/octet-stream")
  }

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
