package org.thoughtcrime.securesms.trustedIntroductions.jobUtils;

import org.json.JSONException;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.HashMap;

// We are generally using json so working with Strings.
// TODO: Not sure I need this anymore given it's a single callback now anyways?
// Keeping it for now in case I need to add more.
public interface TI_Serialize {
  String serialize() throws JSONException;

  /**
   * Must also set the factory variable.
   * @param serialized the serialization string.
   * @return the deserialized object.
   * @throws JSONException
   */
  TI_Serialize deserialize(String serialized) throws JSONException;
  TI_Data getIntroduction();
}
