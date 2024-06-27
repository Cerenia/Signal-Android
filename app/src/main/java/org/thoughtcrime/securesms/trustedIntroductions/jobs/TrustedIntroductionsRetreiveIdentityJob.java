package org.thoughtcrime.securesms.trustedIntroductions.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.jobs.BaseJob;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
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


import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TrustedIntroductionsRetreiveIdentityJob extends BaseJob {

  public static final String KEY = "TrustedIntroductionsRetreiveIdentityJob";

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionsRetreiveIdentityJob.class));

  private static final String KEY_DATA_J    = "data";
  private static final String KEY_CALLBACK_DATA_J = "callbackData";
  private static final String KEY_SAVE_IDENTITY_J = "saveIdentity";
  private static final String KEY_CALLBACK_TYPE_J = "callbackType";

  private final boolean                                     saveIdentity;
  private final TI_JobCallback callback;


  public static HashMap<String, TI_JobCallback.Factory> instantiate = new HashMap<>();

  static {
    instantiate.put(TI_Database.InsertCallback.tag, new TI_Database.InsertCallback.Factory());
    instantiate.put(TI_Database.SetStateCallback.tag, new TI_Database.SetStateCallback.Factory());
  }

  public static class TI_RetrieveIDJobResult extends TI_JobCallbackData implements setRetreiveIdJobResult {
    public TI_Data TIData;
    public String key;
    public String aci;

    // serialization
    private static final String KEY_TI_DATA_J      = "tiData";
    private static final String KEY_IDENTITY_KEY_J = "key";
    private static final String KEY_ACI_J          = "aci";

    public TI_RetrieveIDJobResult(TI_Data data, String key, String aci){
      this.TIData = data;
      this.key = key;
      this.aci = aci;
    }

    public TI_RetrieveIDJobResult() {

    }

    @NonNull @Override public JSONObject serialize() throws JSONException {
      JSONObject serializedData = new JSONObject();
      serializedData.put(KEY_TI_DATA_J, TIData.serialize());
      serializedData.putOpt(KEY_IDENTITY_KEY_J, key);
      serializedData.putOpt(KEY_ACI_J, aci);
      return serializedData;
    }

    @Override public TI_RetrieveIDJobResult deserialize(@NonNull JSONObject serialized) throws JSONException {
      this.key = serialized.has(KEY_IDENTITY_KEY_J) ? serialized.getString(KEY_IDENTITY_KEY_J) : null;
      this.aci = serialized.has(KEY_ACI_J) ? serialized.getString(KEY_ACI_J) : null;
      this.TIData = TI_Data.Deserializer.deserialize(new JSONObject(serialized.getString(KEY_TI_DATA_J)));
      return this;
    }

    @Override public TI_RetrieveIDJobResult getIdentityResult() {
      return this;
    }

    @Override public TI_Data getIntroduction() {
      return TIData;
    }

    @Override public void setAci(String aci) {
      this.aci = aci;
    }

    @Override public void setPublicKey(String publicKey) {
      this.key = publicKey;
    }
  }

  public interface setRetreiveIdJobResult {
    void setAci(String aci);
    void setPublicKey(String publicKey);
  }

  /**
   * Fetches the unknown identity from the signal service and
   * either saves it in the identity table
   * and|or
   * hands result to the callback for introduction insertion.
   *
   * @param data introduceeId and IntroduceeNumber must be present
   * @param saveIdentity weather to save the identity into the identity table
   * @param introductionInsertCallBack weather to call back for an introduction insert at the end of the fetch
   */
  public TrustedIntroductionsRetreiveIdentityJob(@NonNull TI_Data data, boolean saveIdentity, @Nullable TI_JobCallback introductionInsertCallBack){
    // TODO: Currently bogus introduceeId and IntroduceeNumber lead to an application crash
    this(saveIdentity,
         introductionInsertCallBack,
         new Parameters.Builder()
                               .setQueue(data.getIntroducerServiceId() + TAG)
                               .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                               .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                               .addConstraint(NetworkConstraint.KEY)
                               .build());

  }

  private TrustedIntroductionsRetreiveIdentityJob(boolean saveIdentity, @Nullable TI_JobCallback callBack, @NonNull Parameters parameters){
    super(parameters);
    this.saveIdentity = saveIdentity;
    this.callback     = callBack;
  }


  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public byte[] serialize() {
    JSONObject serializedData = new JSONObject();
    try{
      serializedData.put(KEY_CALLBACK_TYPE_J, callback.getTag());
      serializedData.put(KEY_CALLBACK_DATA_J, callback.getCallbackData().serialize());
      serializedData.put(KEY_SAVE_IDENTITY_J, saveIdentity);
    } catch (JSONException e){
      // TODO: fail gracefully
     e.printStackTrace();
     throw new AssertionError(TAG + " Json serialization of TI_RetrieveIDJobResult failed!");
    }
    return Objects.requireNonNull(new JsonJobData.Builder().putString(KEY_DATA_J, serializedData.toString())
                                                           .build().serialize());
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
    if(callback.getCallbackData() == null){
      throw new AssertionError("Missing data in Job Callback!");
    }
    TI_Data d = callback.getCallbackData().getIntroduction();
    Log.e(TAG, "Could not find a registered user with service id:" + d.getIntroduceeServiceId() + " and phone nr: " + d.getIntroduceeNumber()  + ". This introduction failed and will not be retried.");
  }

  @Override protected void onRun() throws Exception {
    //@see RetrieveProfileJob
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Unregistered. Skipping.");
      return;
    }

    Log.i(TAG, "RetreiveIdentityJob started.");
    ServiceId sid = ServiceId.parseOrThrow(callback.getCallbackData().getIntroduction().getIntroduceeServiceId());
    SignalServiceAddress serviceAddress = new SignalServiceAddress(sid);
    ProfileService                                    profileService = ApplicationDependencies.getProfileService();
    Observable<ServiceResponse<ProfileAndCredential>> result         = profileService.getProfile(serviceAddress, Optional.empty(), Optional.empty(), SignalServiceProfile.RequestType.PROFILE, Locale.getDefault()).toObservable();
    // Call db callback and pass the ti_Data & Profile (at least Identity key, phone and aci)
    //@see RetreiveProfileJob
    ServiceResponse<ProfileAndCredential> sr =  result.observeOn(Schedulers.io()).blockingFirst();
    ProfileService.ProfileResponseProcessor processor = new ProfileService.ProfileResponseProcessor(sr);

    SignalServiceProfile profile;
    TI_RetrieveIDJobResult jobResult = new TI_RetrieveIDJobResult();
    if (processor.notFound()){
      Log.e(TAG, "No user exists with service ID: " + callback.getCallbackData().getIntroduction().getIntroduceeServiceId() + ". Ignoring introduction.");
      return;
    } else if (processor.hasResult()) {
      if (sr.getResult().isPresent()){
        jobResult.TIData = callback.getCallbackData().getIntroduction();
        profile  =  sr.getResult().get().getProfile();
        jobResult.key = profile.getIdentityKey();
        jobResult.aci = profile.getServiceId().toString();
      } else {
        Log.e(TAG, "ServiceResponse.getResult() was empty for service ID: " + callback.getCallbackData().getIntroduction().getIntroduceeServiceId() + ". Ignoring introduction.");
        return;
      }
    } else {
      Log.e(TAG, "Processor did not have a result for service ID: " + callback.getCallbackData().getIntroduction().getIntroduceeServiceId() + ". Ignoring introduction.");
      return;
    }
    if(callback.getCallbackData() instanceof setRetreiveIdJobResult){
      setRetreiveIdJobResult cbData = (setRetreiveIdJobResult) callback.getCallbackData();
      cbData.setAci(jobResult.aci);
      cbData.setPublicKey(jobResult.key);
    }
    if(saveIdentity){
      Log.d(TAG, "Saving identity for service ID: " + jobResult.aci);
      ApplicationDependencies.getProtocolStore().aci().identities().saveIdentity(profile.getServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID), new IdentityKey(Base64.decode(jobResult.key)), true);
    }
    callback.callback();
  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    if(e instanceof IllegalArgumentException){
      e.printStackTrace();
      Log.e(TAG,"The introduction for " + callback.getCallbackData().getIntroduction().getIntroduceeName() + " with number: " + callback.getCallbackData().getIntroduction().getIntroduceeNumber() + " was not accepted.");
      return false;
    }
    return true;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsRetreiveIdentityJob> {

    @NonNull @Override public TrustedIntroductionsRetreiveIdentityJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      // deserialize TI_Data if present
      String serializedTiData = data.getString(KEY_DATA_J);
      if (!serializedTiData.isEmpty()){
        try {
          JSONObject innerData = new JSONObject(serializedTiData);
          boolean saveIdentity = innerData.getBoolean(KEY_SAVE_IDENTITY_J);
          String callbackType = innerData.getString(KEY_CALLBACK_TYPE_J);
          TI_JobCallback.Factory cbFactory           = instantiate.get(callbackType);
          TI_JobCallbackData     deserializedJobData = cbFactory.getEmptyJobDataInstance();
          deserializedJobData.deserialize(innerData.getJSONObject(KEY_CALLBACK_DATA_J));
          cbFactory.initialize(deserializedJobData);
          TI_JobCallback cb = cbFactory.create();
          return new TrustedIntroductionsRetreiveIdentityJob(saveIdentity, cb, parameters);
        } catch (JSONException e) {
          // TODO: How to fail gracefully?
          e.printStackTrace();
          //throw new AssertionError("Deserialization of TI_RetrieveIDJobResult failed");
        }
      }
      // unreachable code but compiler complains..
      return null;
    }
  }
}


