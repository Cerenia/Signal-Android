package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.concurrent.TimeUnit

class TrustedIntroductionMultiDeviceSync(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceTISyncJob"
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionMultiDeviceSync::class.java))

    @JvmStatic
    fun create(): TrustedIntroductionMultiDeviceSync {
      return TrustedIntroductionMultiDeviceSync(
        parameters = Parameters.Builder()
          .setQueue(KEY)
          .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
          .setMaxAttempts(Parameters.UNLIMITED)
          .addConstraint(NetworkConstraint.KEY)
          .build()
      )
    }
  }

  constructor(): this(
    Parameters.Builder()
      .setQueue(KEY)
      .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .build()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      // todo, put all the data here
      //.putString()
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

    val messageSender = ApplicationDependencies.getSignalServiceMessageSender()
    // todo: send the right message

  }

  override fun onShouldRetry(e: Exception): Boolean {
    Log.w(TAG, "Retrying job, got exception: ${e.message}")
    return true;
  }

  class Factory : Job.Factory<TrustedIntroductionMultiDeviceSync> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TrustedIntroductionMultiDeviceSync {
      return TrustedIntroductionMultiDeviceSync(parameters);
    }
  }
}