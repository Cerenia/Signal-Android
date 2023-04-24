package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

//@see also Callback in TrustedIntroductionsDatabase
public interface TI_JobCallback<T> {
  interface Factory<T>{
    T create();
  }
  public TI_Data getIntroduction();
}