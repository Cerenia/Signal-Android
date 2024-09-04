package org.thoughtcrime.securesms.trustedIntroductions.jobs;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

public abstract class TI_JobCallbackData implements TI_Serialize {
  abstract public TI_Data getIntroduction();
}
