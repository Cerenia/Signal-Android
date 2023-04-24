package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

//@see also Callback in TrustedIntroductionsDatabase
public interface TI_JobCallback<T extends TI_Serialize<T>> {
  interface Factory<T>{
    T create();
  }
}