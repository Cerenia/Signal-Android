package org.thoughtcrime.securesms.trustedIntroductions

import org.json.JSONObject
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TI_Serialize

// TODO: predictedSecurityNumber only needs to be nullable because I parse the TI_Message somewhat awkardly... maybe change at some point? Not super critical...
// IntroduceeRecipientId and Introducer
// introduceeIdentityKey is encoded in Base64 (this is how it is currently stored in the Identity Database) @see TI_Utils.encodeIdentityKey
// Service ID == ACI. PNI may be used to query profiles but once a chat is established we always have an ACI.
data class TI_Data (val id: Long?, val state: TI_Database.State, val introducerServiceId: String?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String?, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long) : TI_Serialize {

  override fun serialize() : JSONObject {
    // Absence of key signifies null
    val builder = JSONObject()
    // does nothing iff id == null see: https://developer.android.com/reference/kotlin/org/json/JSONObject
    builder.putOpt("id", id)
    builder.put("state", state.toInt())
    builder.putOpt("introducerServiceId", introducerServiceId)
    builder.put("introduceeServiceId", introduceeServiceId)
    builder.put("introduceeName", introduceeName)
    builder.putOpt("introduceeNumber", introduceeNumber)
    builder.put("introduceeIdentityKey", introduceeIdentityKey)
    builder.putOpt("predictedSecurityNumber", predictedSecurityNumber)
    builder.put("timestamp", timestamp)
    return builder
  }

  override fun deserialize(serialized: JSONObject) : TI_Data{
    return Deserializer.deserialize(serialized)
  }

  override fun getIntroduction(): TI_Data {
    return this
  }

  companion object Deserializer {
    // factory from serialized String
    fun deserialize(serialized: JSONObject): TI_Data {
      // Absence of key signifies null
      val id: Long?
      if (serialized.has("id")){
        id = serialized.getLong("id")
      } else{
        id = null
      }
      val  state = TI_Database.State.forState(serialized.getInt("state"))
      val introducerServiceId: String?
      if (serialized.has("introducerServiceId")){
        introducerServiceId = serialized.getString("introducerServiceId")
      } else {
        introducerServiceId = null
      }
      val introduceeServiceId = serialized.getString("introduceeServiceId")
      val introduceeName = serialized.getString("introduceeName")
      val introduceeNumber = serialized.getString("introduceeNumber")
      val introduceeIdentityKey = serialized.getString("introduceeIdentityKey")
      val predictedSecurityNumber: String?
      if (serialized.has("predictedSecurityNumber")){
        predictedSecurityNumber = serialized.getString("predictedSecurityNumber")
      } else{
        predictedSecurityNumber = null
      }
      val timestamp = serialized.getLong("timestamp")
      return TI_Data(id, state, introducerServiceId, introduceeServiceId, introduceeName, introduceeNumber, introduceeIdentityKey, predictedSecurityNumber, timestamp)
    }
  }
}