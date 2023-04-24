package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.json.JSONException;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsRetreiveIdentityJob;

import java.util.HashMap;

// We are generally using json so working with Strings.
// TODO: Not sure I need this anymore given it's a single callback now anyways?
// Keeping it for now in case I need to add more.
public abstract class TI_Serialize<T> {
  abstract public String serialize() throws JSONException;

  /**
   * Must also set the factory variable.
   * @param serialized the serialization string.
   * @return the deserialized object.
   * @throws JSONException
   */
  abstract public T deserialize(String serialized) throws JSONException;

  public TI_JobCallback.Factory<T> factory = null;

  // TODO: This would only be necessary if more callbacks are to be passed to the jobs.
  @SuppressWarnings("rawtypes") public static HashMap<String, TI_JobCallback.Factory> instantiate = new HashMap<String, TI_JobCallback.Factory>();

  static {
    instantiate.put(Log.tag(TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult.class), new TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult.Factory());
    //instantiate.put()
  }
}
