package org.thoughtcrime.securesms.trustedIntroductions

import org.json.JSONObject
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TISerializable

// TODO: predictedSecurityNumber only needs to be nullable because I parse the TI_Message somewhat awkwardly... maybe change at some point? Not super critical...
// IntroduceeRecipientId and Introducer
// introduceeIdentityKey is encoded in Base64 (this is how it is currently stored in the Identity Database) @see TI_Utils.encodeIdentityKey
// Service ID == ACI. PNI may be used to query profiles but once a chat is established we always have an ACI.
data class TI_Data(
  val id: Long?,
  val state: TI_DatabaseGlue.Companion.State,
  val introducerServiceId: String?,
  val introduceeServiceId: String,
  val introduceeName: String?,
  val introduceeNumber: String?,
  val introduceeIdentityKey: String,
  val introduceeProfileKey: String, // todo: change when all constructors fixed
  var predictedSecurityNumber: String?,
  val timestamp: Long
) : TISerializable {

  override fun serialize(): JSONObject {
    // Absence of key signifies null
    val builder = JSONObject()
    // does nothing iff id == null see: https://developer.android.com/reference/kotlin/org/json/JSONObject
    builder.putOpt("id", id)
    builder.put("state", state.toInt())
    builder.putOpt("introducerServiceId", introducerServiceId)
    builder.put("introduceeServiceId", introduceeServiceId)
    builder.putOpt("introduceeName", introduceeName)
    builder.putOpt("introduceeNumber", introduceeNumber)
    builder.put("introduceeIdentityKey", introduceeIdentityKey)
    builder.put("introduceeProfileKey", introduceeProfileKey)
    builder.putOpt("predictedSecurityNumber", predictedSecurityNumber)
    builder.put("timestamp", timestamp)
    return builder
  }

  override fun deserialize(serialized: JSONObject?): TI_Data {
    if (serialized == null) {
      throw NullPointerException("cannot deserialize null TI_Data")
    }
    return Deserializer.deserialize(serialized)
  }

  override val introduction: TI_Data
    get() = this

  companion object Deserializer {
    // factory from serialized String
    fun deserialize(serialized: JSONObject): TI_Data {
      // Absence of key signifies null
      val id: Long? = if (serialized.has("id")) {
        serialized.getLong("id")
      } else {
        null
      }
      val state = TI_DatabaseGlue.Companion.State.forState(serialized.getInt("state"))
      val introducerServiceId: String? = if (serialized.has("introducerServiceId")) {
        serialized.getString("introducerServiceId")
      } else {
        null
      }
      val introduceeServiceId = serialized.getString("introduceeServiceId")
      val introduceeName: String? = if (serialized.has("introduceeName")) {
        serialized.getString("introduceeName")
      } else {
        null
      }
      val introduceeNumber: String? = if (serialized.has("introduceeNumber")) {
        serialized.getString("introduceeNumber")
      } else {
        null
      }
      val introduceeIdentityKey = serialized.getString("introduceeIdentityKey")
      val predictedSecurityNumber: String? = if (serialized.has("predictedSecurityNumber")) {
        serialized.getString("predictedSecurityNumber")
      } else {
        null
      }
      val introduceeProfileKey = if (serialized.has("introduceeProfileKey")) {
        serialized.getString("introduceeProfileKey")
      } else {
        ""
      }
      val timestamp = serialized.getLong("timestamp")
      return TI_Data(
        id = id,
        state = state,
        introducerServiceId = introducerServiceId,
        introduceeServiceId = introduceeServiceId,
        introduceeName = introduceeName,
        introduceeNumber = introduceeNumber,
        introduceeIdentityKey = introduceeIdentityKey,
        introduceeProfileKey = introduceeProfileKey,
        predictedSecurityNumber = predictedSecurityNumber,
        timestamp = timestamp
      )
    }
  }
}