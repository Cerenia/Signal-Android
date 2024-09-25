/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;

import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.restore.restorelocalbackup.RestoreLocalBackupFragmentDirections;
import org.thoughtcrime.securesms.restore.restorelocalbackup.RestoreLocalBackupFragment;
import org.thoughtcrime.securesms.restore.restorelocalbackup.RestoreLocalBackupFragment.postToastForBackupRestorationFailure;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.Locale;

public interface RestoreLocalBackupFragmentGlue {

  String TAG                       = String.format(TI_Utils.TI_LOG_TAG, org.signal.core.util.logging.Log.tag(RestoreLocalBackupFragmentGlue.class));

  static void getFromUriTI(@NonNull Context context,
                           @Nullable Uri tiBackupUri,
                           @Nullable RestoreLocalBackupFragment.OnBackupSearchResultListener tiListener)
  {
    if(tiBackupUri != null) {
      SimpleTask.run(() -> {
                       try {
                         return BackupUtil.getBackupInfoFromSingleUri(context, tiBackupUri);
                       } catch (BackupUtil.BackupFileException e) {
                         Log.w(TAG, "Could not restore TI backup.", e);
                         postToastForBackupRestorationFailure(context, e);
                         return null;
                       }
                     },
                     tiListener::run);
    }
  }

  class BackupInfoViews {
    public TextView restoreBackupSizeTI;
    public TextView restoreBackupTimeTI;

    public BackupInfoViews(TextView restoreBackupSizeTI, TextView restoreBackupTimeTI){
      this.restoreBackupSizeTI = restoreBackupSizeTI;
      this.restoreBackupTimeTI = restoreBackupTimeTI;
    }

  }

  static BackupUtil.BackupInfo handleTIBackupInfo(@NonNull View view, @NonNull Context context, @NonNull BackupInfoViews views,BackupUtil.BackupInfo tiBackupInfo) {
    if (context == null) {
      Log.i(TAG, "No context on fragment, must have navigated away.");
      return null;
    }

    if (tiBackupInfo == null) {
      Log.i(TAG, "Skipping TI backup detection. No backup found, or permission revoked since.");
      SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                  RestoreBackupFragmentDirections.actionNoBackupFound());
      views.restoreBackupSizeTI.setText(context.getString(R.string.RegistrationActivity_ti_backup_missing));
      views.restoreBackupTimeTI.setText("");
    } else {
      views.restoreBackupSizeTI.setText(context.getString(R.string.RegistrationActivity_ti_backup_size_s, Util.getPrettyFileSize(tiBackupInfo.getSize())));
      views.restoreBackupTimeTI.setText(context.getString(R.string.RegistrationActivity_ti_backup_timestamp_s, DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), tiBackupInfo.getTimestamp())));
    }
    return tiBackupInfo;
  }

}
