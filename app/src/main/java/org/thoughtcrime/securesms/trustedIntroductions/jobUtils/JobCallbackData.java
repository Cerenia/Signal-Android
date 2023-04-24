package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetreiveIdentityJob;

public abstract class JobCallbackData extends TI_Serialize {
  public abstract TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult getRetrieveIdJobStruct();
}
