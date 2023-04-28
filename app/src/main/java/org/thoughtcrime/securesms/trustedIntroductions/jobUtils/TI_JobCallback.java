package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;

//@see also TI_DB_Callback in TrustedIntroductionsDatabase
public interface TI_JobCallback {
  interface Factory{
    TrustedIntroductionsDatabase.TI_DB_Callback create();
    void initialize(TI_JobCallbackData data);
    TI_JobCallbackData getEmptyJobDataInstance();
  }
}