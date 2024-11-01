package org.thoughtcrime.securesms.trustedIntroductions.jobs

import org.json.JSONException
import org.json.JSONObject
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data

// We are generally using json so working with Strings.
// TODO: Not sure I need this anymore given it's a single callback now anyways?
// Keeping it for now in case I need to add more.
interface TISerializable {
  @Throws(JSONException::class)
  fun serialize(): JSONObject?

  /**
   * Must also set the factory initialization state.
   * @param serialized the serialization string.
   * @return the deserialized object.
   * @throws JSONException if the file is malformed
   */
  @Throws(JSONException::class)
  fun deserialize(serialized: JSONObject?): TISerializable?
  val introduction: TI_Data?
}
