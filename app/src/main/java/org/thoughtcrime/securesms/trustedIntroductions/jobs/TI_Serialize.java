package org.thoughtcrime.securesms.trustedIntroductions.jobs;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

// We are generally using json so working with Strings.
// TODO: Not sure I need this anymore given it's a single callback now anyways?
// Keeping it for now in case I need to add more.
public interface TI_Serialize {
  JSONObject serialize() throws JSONException;

  /**
   * Must also set the factory initialization state.
   * @param serialized the serialization string.
   * @return the deserialized object.
   * @throws JSONException
   */
  TI_Serialize deserialize(JSONObject serialized) throws JSONException;
  TI_Data getIntroduction();
}
