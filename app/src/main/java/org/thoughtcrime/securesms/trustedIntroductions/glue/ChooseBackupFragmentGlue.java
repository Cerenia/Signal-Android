/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Toast;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import static androidx.core.app.ActivityCompat.startActivityForResult;

public interface ChooseBackupFragmentGlue {
  short  OPEN_TI_FILE_REQUEST_CODE = 3863; // New request code for TI backup
  String TAG                       = String.format(TI_Utils.TI_LOG_TAG, org.signal.core.util.logging.Log.tag(ChooseBackupFragmentGlue.class));


  class onChooseTIBackupSelectedArg {
    public View     view;
    public Context  context;
    public Activity activity;

    public onChooseTIBackupSelectedArg(View view, Context context, Activity activity){
      this.view = view;
      this.context = context;
      this.activity = activity;
    }
  }

  static void onChooseTIBackup(onChooseTIBackupSelectedArg args) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

    intent.setType("application/octet-stream");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

    if (Build.VERSION.SDK_INT >= 26) {
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings().getLatestSignalBackupDirectory());
    }

    try {
      startActivityForResult(args.activity, intent, OPEN_TI_FILE_REQUEST_CODE, null);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(args.context, R.string.ChooseBackupFragment__no_file_browser_available, Toast.LENGTH_LONG).show();
      Log.w(TAG, "No matching activity!", e);
    }
  }
}
