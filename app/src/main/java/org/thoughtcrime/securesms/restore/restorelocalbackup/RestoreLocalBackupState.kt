/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import android.net.Uri
import org.thoughtcrime.securesms.restore.RestoreRepository
import org.thoughtcrime.securesms.util.BackupUtil.BackupInfo

/**
 * State holder for a backup restore.
 */
data class RestoreLocalBackupState(
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  val tiBackupUri: Uri? = null,
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end
  val uri: Uri,
  val backupInfo: BackupInfo? = null,
  val backupPassphrase: String = "",
  val restoreInProgress: Boolean = false,
  val backupVerifyingInProgress: Boolean = false,
  val backupProgressCount: Long = -1,
  val backupEstimatedTotalCount: Long = -1,
  val backupRestoreComplete: Boolean = false,
  val backupImportResult: RestoreRepository.BackupImportResult? = null,
  val abort: Boolean = false
)
