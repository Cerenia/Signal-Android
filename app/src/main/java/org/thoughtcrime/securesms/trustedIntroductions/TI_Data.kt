package org.thoughtcrime.securesms.trustedIntroductions

import org.json.JSONObject
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase
import org.thoughtcrime.securesms.trustedIntroductions.jobUtils.TI_Serialize

// TODO: predictedSecurityNumber only needs to be nullable because I parse the TI_Message somewhat awkardly... maybe change at some point? Not super critical...
// IntroduceeRecipientId and Introducer
// introduceeIdentityKey is encoded in Base64 (this is how it is currently stored in the Identity Database) @see TI_Utils.encodeIdentityKey
data class TI_Data (val id: Long?, val state: TrustedIntroductionsDatabase.State,  val introducerServiceId: String?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long) : TI_Serialize<TI_Data>() {

  override fun serialize() : String {
    // Absence of key signifies null
    val builder = JSONObject()
    // does nothing iff id == null see: https://developer.android.com/reference/kotlin/org/json/JSONObject
    builder.putOpt("id", id)
    builder.put("state", state.toInt())
    builder.putOpt("introducerServiceId", introducerServiceId)
    builder.put("introduceeServiceId", introduceeServiceId)
    builder.put("introduceeName", introduceeName)
    builder.put("introduceeNumber", introduceeNumber)
    builder.put("introduceeIdentityKey", introduceeIdentityKey)
    builder.putOpt("predictedSecurityNumber", predictedSecurityNumber)
    builder.put("timestamp", timestamp)
    return builder.toString()
  }

  override fun deserialize(serialized: String) : TI_Data{
    return Deserializer.deserialize(serialized)
  }

  companion object Deserializer {
    // factory from serialized String
    fun deserialize(serialized: String): TI_Data {
      val d = JSONObject(serialized)
      // Absence of key signifies null
      val id: Long?
      if (d.has("id")){
        id = d.getLong("id")
      } else{
        id = null
      }
      val  state = TrustedIntroductionsDatabase.State.forState(d.getInt("state"))
      val introducerServiceId: String?
      if (d.has("introducerServiceId")){
        introducerServiceId = d.getString("introducerServiceId")
      } else {
        introducerServiceId = null
      }
      val introduceeServiceId = d.getString("introduceeServiceId")
      val introduceeName = d.getString("introduceeName")
      val introduceeNumber = d.getString("introduceeNumber")
      val introduceeIdentityKey = d.getString("introduceeIdentityKey")
      val predictedSecurityNumber: String?
      if (d.has("predictedSecurityNumber")){
        predictedSecurityNumber = d.getString("predictedSecurityNumber")
      } else{
        predictedSecurityNumber = null
      }
      val timestamp = d.getLong("timestamp")
      return TI_Data(id, state, introducerServiceId, introduceeServiceId, introduceeName, introduceeNumber, introduceeIdentityKey, predictedSecurityNumber, timestamp)
    }
  }
}