package org.thoughtcrime.securesms.jobs;

import android.content.ContentValues;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;


import java.util.Locale;
import java.util.Optional;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TrustedIntroductionsRetreiveIdentityJob extends BaseJob{

  public static final String KEY = "TrustedIntroductionsRetreiveIdentityJob";

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionsRetreiveIdentityJob.class));

  private static final String KEY_JSON_DATA = "data";
  private static final String KEY_JSON_TI_DATA = "tiData";
  private static final String KEY_JSON_KEY = "key";
  private static final String KEY_JSON_ACI = "aci";

  private final TI_RetrieveIDJobResult data;

  public static class TI_RetrieveIDJobResult {
    public TI_Data TIData;
    public String key;
    public String aci;

    private TI_RetrieveIDJobResult(TI_Data data, String key, String aci){
      this.TIData = data;
      this.key = key;
      this.aci = aci;
    }

    public TI_RetrieveIDJobResult() {

    }
  }

  /**
   * @param data introduceeId and IntroduceeNumber must be present
   */
  public TrustedIntroductionsRetreiveIdentityJob(@NonNull TI_Data data){
    // TODO: Currently bogus introduceeId and IntroduceeNumber lead to an application crash
    this(new TI_RetrieveIDJobResult(data, null, null), new Parameters.Builder()
                               .setQueue(data.getIntroducerServiceId() + TAG)
                               .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                               .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                               .addConstraint(NetworkConstraint.KEY)
                               .build());

  }

  private TrustedIntroductionsRetreiveIdentityJob(@NonNull TI_RetrieveIDJobResult data, @NonNull Parameters parameters){
    super(parameters);
    this.data = data;
  }


  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public Data serialize() {
    JSONObject serializedData = new JSONObject();
    try{
      serializedData.put(KEY_JSON_TI_DATA, data.TIData.serialize());
      serializedData.putOpt(KEY_JSON_KEY, data.key);
      serializedData.putOpt(KEY_JSON_ACI, data.aci);
    } catch (JSONException e){
      // TODO: fail gracefully
     e.printStackTrace();
     //throw new AssertionError(TAG + " Json serialization of TI_RetrieveIDJobResult failed!");
    }
    return new Data.Builder().putString(KEY_JSON_DATA, serializedData.toString())
                             .build();
  }

  /**
   * Returns the key that can be used to find the relevant factory needed to create your job.
   */
  @NonNull @Override public String getFactoryKey() {
    return KEY;
  }

  /**
   * Called when your job has completely failed and will not be run again.
   */
  @Override public void onFailure() {
    // TODO: Would we like some kind of note to the user that this happened?
    // Not urgent but may be nice to have in the user-specific screen
    // I think probably not since this could have been a tampered introduction, silent drop seems sensible
    Log.e(TAG, "Could not find a registered user with service id:" + data.TIData.getIntroduceeServiceId() + " and phone nr: " + data.TIData.getIntroduceeNumber()  + ". This introduction failed and will not be retried.");
  }

  @Override protected void onRun() throws Exception {
    //@see RetrieveProfileJob
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Unregistered. Skipping.");
      return;
    }

    //TODO test
    Log.e(TAG, "RetreiveIdentityJob started.");
    ServiceId sid = ServiceId.parseOrThrow(data.TIData.getIntroduceeServiceId());
    SignalServiceAddress serviceAddress = new SignalServiceAddress(sid);
    ProfileService                                    profileService = ApplicationDependencies.getProfileService();
    Observable<ServiceResponse<ProfileAndCredential>> result         = profileService.getProfile(serviceAddress, Optional.empty(), Optional.empty(), SignalServiceProfile.RequestType.PROFILE, Locale.getDefault()).toObservable();
    // Call db callback and pass the ti_Data & Profile (at least Identity key, phone and aci)
    //@see RetreiveProfileJob
    ServiceResponse<ProfileAndCredential> sr =  result.observeOn(Schedulers.io()).blockingFirst();
    ProfileService.ProfileResponseProcessor processor = new ProfileService.ProfileResponseProcessor(sr);
    TI_RetrieveIDJobResult jobResult = new TI_RetrieveIDJobResult();
    if (processor.notFound()){
      Log.e(TAG, "No user exists with service ID: " + data.TIData.getIntroduceeServiceId() + ". Ignoring introduction.");
      return;
    } else if (processor.hasResult()) {
      if (sr.getResult().isPresent()){
        jobResult.TIData = data.TIData;
        SignalServiceProfile profile  =  sr.getResult().get().getProfile();
        jobResult.key = profile.getIdentityKey();
        jobResult.aci = profile.getServiceId().toString();
      } else {
        Log.e(TAG, "ServiceResponse.getResult() was empty for service ID: " + data.TIData.getIntroduceeServiceId() + ". Ignoring introduction.");
        return;
      }
    } else {
      Log.e(TAG, "Processor did not have a result for service ID: " + data.TIData.getIntroduceeServiceId() + ". Ignoring introduction.");
      return;
    }
    TrustedIntroductionsDatabase db = SignalDatabase.trustedIntroductions();
    db.insertIntroductionCallback(jobResult.TIData, jobResult.key, jobResult.aci);
  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    if(e instanceof IllegalArgumentException){
      e.printStackTrace();
      Log.e(TAG,"The introduction for " + data.TIData.getIntroduceeName() + " with number: " + data.TIData.getIntroduceeNumber() + " was not accepted.");
      return false;
    }
    return true;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsRetreiveIdentityJob> {

    @NonNull @Override public TrustedIntroductionsRetreiveIdentityJob create(@NonNull Parameters parameters, @NonNull Data data) {
      // deserialize TI_Data if present
      String serializedTiData = data.getString(KEY_JSON_DATA);
      TI_RetrieveIDJobResult result;
      if (!serializedTiData.isEmpty()){
        try {
          JSONObject j = new JSONObject(serializedTiData);
          String key = j.has(KEY_JSON_KEY) ? j.getString(KEY_JSON_KEY) : null;
          String aci = j.has(KEY_JSON_ACI) ? j.getString(KEY_JSON_ACI) : null;
          result = new TI_RetrieveIDJobResult(TI_Data.Deserializer.deserialize(j.getString(KEY_JSON_TI_DATA)),
                                              key,
                                              aci);
          return new TrustedIntroductionsRetreiveIdentityJob(result, parameters);
        } catch (JSONException e) {
          // TODO: How to fail gracefully?
          e.printStackTrace();
          throw new AssertionError("Deserialization of TI_RetrieveIDJobResult failed");
        }
      }
      // unreachable code but compiler complains..
      return null;
    }
  }
}


