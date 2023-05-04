package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetrieveIdentityJob;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

public abstract class TI_JobCallbackData implements TI_Serialize {
  public abstract TrustedIntroductionsRetrieveIdentityJob.TI_RetrieveIDJobResult getIdentityResult();
  abstract public TI_Data getIntroduction();
}
