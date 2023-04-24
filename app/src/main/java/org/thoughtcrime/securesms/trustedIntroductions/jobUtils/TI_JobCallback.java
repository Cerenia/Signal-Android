package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;

//@see also Callback in TrustedIntroductionsDatabase
public interface TI_JobCallback {
  interface Factory{
    TrustedIntroductionsDatabase.Callback create();
    void initialize(JobCallbackData data);
    JobCallbackData getEmptyJobDataInstance();
  }
}