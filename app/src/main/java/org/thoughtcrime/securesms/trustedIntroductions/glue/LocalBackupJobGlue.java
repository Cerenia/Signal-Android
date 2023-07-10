package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.util.Log;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.trustedIntroductions.backup.TI_Cursor;
import org.thoughtcrime.securesms.trustedIntroductions.backup.ThrowingLambda;

import java.io.IOException;
import java.util.function.Consumer;

public interface LocalBackupJobGlue {

  static final String TAG = String.format(TI_Utils.TI_LOG_TAG, org.signal.core.util.logging.Log.tag(LocalBackupJobGlue.class));

  static void repeatBackup(ThrowingLambda<String, IOException> doBackup){
    String[] backupNames = { "signal-%s.backup", "signal_trusted_introductions-%s.backup" };
    for (String name : backupNames) {
      try{
        doBackup.accept(name);
      } catch (IOException e) {
        Log.e(TAG, "Exception occured during Backup creation!");
        Log.e(TAG, e.toString());
      }
    }
  }
}
