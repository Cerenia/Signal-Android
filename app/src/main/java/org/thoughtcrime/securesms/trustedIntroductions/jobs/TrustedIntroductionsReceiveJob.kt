package org.thoughtcrime.securesms.trustedIntroductions.jobs

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.logging.Log.e
import org.signal.core.util.logging.Log.i
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data.Deserializer.deserialize
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.constructIntroduceesFromTrustedIntrosString
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.getIntroducerFromRawMessage
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.serializeForQueue
import java.util.Locale
import java.util.Objects

class TrustedIntroductionsReceiveJob private constructor(
  private var introducerId: RecipientId?,
  private val messageBody: String,
  private var bodyParsed: Boolean,
  private val timestamp: Long,
  tiData: ArrayList<TI_Data>?,
  parameters: Parameters
) :
  BaseJob(parameters) {
  private val introductions = if (!tiData.isNullOrEmpty()) tiData else ArrayList()

  // counter keeping track of which TI_DATA has made it's way to the database
  // allows to only serialize introductions that have not yet been done if process gets interrupted
  private var insertsSucceeded = 0

  constructor(messageBody: String, timestamp: Long) : this(
    null,
    messageBody,
    false,
    timestamp,
    null,
    Parameters.Builder()
      .setQueue(serializeForQueue(messageBody) + timestamp)
      .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
      .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
      .addConstraint(NetworkConstraint.KEY)
      .build()
  )

  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  override fun serialize(): ByteArray? {
    while (insertsSucceeded > 0) {
      introductions.removeAt(0)
      insertsSucceeded--
    }
    val serializedIntroductions = JSONArray()
    for (d in introductions) {
      serializedIntroductions.put(d.serialize())
    }
    return Objects.requireNonNull(
      JsonJobData.Builder()
        .putString(KEY_INTRODUCER_ID, if (introducerId == null) "NULL" else introducerId!!.serialize())
        .putString(KEY_MESSAGE_BODY, messageBody)
        .putBoolean(KEY_BODY_PARSED, bodyParsed)
        .putString(KEY_INTRODUCTIONS, serializedIntroductions.toString())
        .putLong(KEY_TIMESTAMP, timestamp)
        .build().serialize()
    )
  }

  /**
   * Returns the key that can be used to find the relevant factory needed to create your job.
   */
  override fun getFactoryKey(): String {
    return KEY
  }

  /**
   * Called when your job has completely failed and will not be run again.
   */
  override fun onFailure() {
    e(TAG, String.format(Locale.ENGLISH, "Failed to write introductions into the database originating from this message %s", messageBody))
  }


  @Throws(Exception::class)
  override fun onRun() {
    if (introducerId == null) {
      introducerId = getIntroducerFromRawMessage(messageBody)
    }
    if (!bodyParsed) {
      val tiData = constructIntroduceesFromTrustedIntrosString(messageBody, timestamp, introducerId!!)
      if (tiData == null) {
        e(TAG, "Introduction did not parse correctly, aborting!")
        return
      }
      introductions.addAll(tiData)
      bodyParsed = true
    }
    val db = tiDatabase
    for (introduction in introductions) {
      val result = db.incomingIntroduction(introduction)
      if (result == -1L) {
        // TODO: How to fail gracefully?
        e(TAG, String.format("Introduction insertion for %s failed...", introduction.introduceeName))
        //throw new AssertionError(TAG + String.format("Introduction insertion for %s failed...", introduction.getIntroduceeName()));
      }
      insertsSucceeded++
    }
    i(TAG, "TrustedIntroductionsReceiveJob completed!")
  }

  // TODO
  override fun onShouldRetry(e: Exception): Boolean {
    return false
  }

  class Factory : Job.Factory<TrustedIntroductionsReceiveJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TrustedIntroductionsReceiveJob {
      // Deserialize introduction_data if present
      val data = JsonJobData.deserialize(serializedData)
      val serializedIntroductions = data.getString(KEY_INTRODUCTIONS)
      val tiData = ArrayList<TI_Data>()
      i(TAG, serializedIntroductions)
      if (serializedIntroductions.isNotEmpty()) {
        try {
          val arr = JSONArray(serializedIntroductions)
          for (i in 0 until arr.length()) {
            tiData.add(deserialize(JSONObject(arr.getString(i))))
          }
        } catch (e: JSONException) {
          // TODO: fail gracefully
          throw AssertionError("JSON deserialization of introductions failed!: " + e.message)
        } catch (e: NullPointerException) {
          throw AssertionError("JSON deserialization of introductions failed!: " + e.message)
        }
      }
      var introducer: RecipientId? = null
      if (data.getString(KEY_INTRODUCER_ID) != "NULL") {
        introducer = RecipientId.from(data.getString(KEY_INTRODUCER_ID))
      }
      return TrustedIntroductionsReceiveJob(
        introducer,
        data.getString(KEY_MESSAGE_BODY),
        data.getBoolean(KEY_BODY_PARSED),
        data.getLong(KEY_TIMESTAMP),
        tiData,
        parameters
      )
    }
  }

  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, tag(TrustedIntroductionsReceiveJob::class.java))

    // Factory Key
    const val KEY: String = "TIReceiveJob"

    // Serialization Keys
    private const val KEY_INTRODUCER_ID = "introducer_id"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_MESSAGE_BODY = "messageBody"
    private const val KEY_BODY_PARSED = "bodyParsed"
    private const val KEY_INTRODUCTIONS = "serialized_remaining_introduction_data"
  }
}
