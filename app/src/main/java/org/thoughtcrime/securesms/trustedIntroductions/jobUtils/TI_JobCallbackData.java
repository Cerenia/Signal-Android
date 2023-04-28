package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetreiveIdentityJob;

public abstract class TI_JobCallbackData implements TI_Serialize {
  public abstract TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult getRetrieveIdJobStruct();
}
