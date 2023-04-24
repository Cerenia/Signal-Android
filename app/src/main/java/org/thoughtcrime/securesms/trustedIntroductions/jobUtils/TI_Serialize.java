package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.json.JSONException;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetreiveIdentityJob;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.HashMap;

// We are generally using json so working with Strings.
// TODO: Not sure I need this anymore given it's a single callback now anyways?
// Keeping it for now in case I need to add more.
public abstract class TI_Serialize {
  abstract public String serialize() throws JSONException;

  /**
   * Must also set the factory variable.
   * @param serialized the serialization string.
   * @return the deserialized object.
   * @throws JSONException
   */
  abstract public TI_Serialize deserialize(String serialized) throws JSONException;
  abstract public TI_Data getIntroduction();

  public TI_JobCallback.Factory factory = null;
}
