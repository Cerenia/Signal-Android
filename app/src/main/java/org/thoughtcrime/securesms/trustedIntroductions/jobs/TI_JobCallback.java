package org.thoughtcrime.securesms.trustedIntroductions.jobs;

//@see also TI_DB_Callback in TrustedIntroductionsDatabase
public interface TI_JobCallback {
  abstract public void callback();
  abstract public String getTag();
  abstract public TI_JobCallbackData getCallbackData();
  interface Factory{
    TI_JobCallback create();
    void initialize(TI_JobCallbackData data);
    TI_JobCallbackData getEmptyJobDataInstance();
  }
}