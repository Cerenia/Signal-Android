package org.thoughtcrime.securesms.jobs

import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.multidevice.IntroducedMessage
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import java.util.Optional

class TrustedIntroductionMultiDeviceSync(parameters: Parameters, private val introId : Long, private val introData: TI_Data, private val syncState: Int) : BaseJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceTISyncJob"
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionMultiDeviceSync::class.java))

    @JvmStatic
    fun create(introId: Long, introData: TI_Data, syncState: Int): TrustedIntroductionMultiDeviceSync {
      return TrustedIntroductionMultiDeviceSync(
        parameters = Parameters.Builder()
          .setQueue(KEY)
          .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
          .setMaxAttempts(Parameters.UNLIMITED)
          .addConstraint(NetworkConstraint.KEY)
          .build(),
        introId = introId,
        introData = introData,
        syncState = syncState,
      )
    }
  }

  constructor(introId: Long, introData: TI_Data, syncState: Int ): this(
    Parameters.Builder()
      .setQueue(KEY)
      .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .build(),
    introId = introId,
    introData = introData,
    syncState = syncState
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong("introId", introId)
      // todo, put all the data here
      .putString("introData", introData.serialize().toString())
      .putInt("syncState", syncState)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onFailure() = Unit

  override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...")
      return
    }

    // todo: send the right message
    val intro = SignalDatabase.trustedIntroductions.getIntroductionById(introId.toString())

    val dummy = IntroducedMessage(introId,"","","","","","", 0, syncState, 0)
    // deletion happens before network sync job.
    var introducedMessage: IntroducedMessage = dummy;
    if (intro?.id != null){
      introducedMessage = IntroducedMessage(
        intro.id,
        intro.introducerServiceId,
        intro.introduceeServiceId,
        intro.introduceeIdentityKey,
        intro.introduceeName,
        intro.introduceeNumber,
        intro.predictedSecurityNumber,
        intro.state.toInt(),
        syncState,
        intro.timestamp
      )
    }
//    val introduced: List<IntroducedMessage> = listOf(introducedMessage)
    ApplicationDependencies.getSignalServiceMessageSender().sendSyncMessage(SignalServiceSyncMessage.forIntroduced(introducedMessage), Optional.empty())
//    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    Log.w(TAG, "Retrying job, got exception: ${e.message}")
    return true;
  }

  class Factory : Job.Factory<TrustedIntroductionMultiDeviceSync> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TrustedIntroductionMultiDeviceSync {
      val data = JsonJobData.deserialize(serializedData)

      val introId = data.getLong("introId");
      val jsonIntro = JSONObject(data.getString("introData"));
      val introData : TI_Data = TI_Data.deserialize(jsonIntro) // TODO: maybe use IntroducedMessage instead?
      return TrustedIntroductionMultiDeviceSync(parameters, introId, introData, data.getInt("syncState"));
    }
  }
}