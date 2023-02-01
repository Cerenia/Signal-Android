package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

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
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.Optional;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TrustedIntroductionsRetreiveIdentityJob extends BaseJob{

  public static final String KEY = "TrustedIntroductionsRetreiveIdentityJob";

  private static final String TAG = Log.tag(TrustedIntroductionsRetreiveIdentityJob.class);

  // TI_Data
  private static final String KEY_TI_DATA = "tiData";

  private final TI_Data data;

  public static class TI_RetrieveIDJobResult implements Serializable {
    public TI_Data data;
    public String key;
    public String aci;

    public TI_RetrieveIDJobResult(TI_Data data, String key, String aci){
      this.data = data;
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
    this(data, new Parameters.Builder()
                               .setQueue(data.getIntroduceeId().toQueueKey() + data.getIntroduceeNumber() + TAG)
                               .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                               .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                               .addConstraint(NetworkConstraint.KEY)
                               .build());

  }

  private TrustedIntroductionsRetreiveIdentityJob(@NonNull TI_Data data, @NonNull Parameters parameters){
    super(parameters);
    this.data = data;
  }


  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public Data serialize() {
    String serializedData = "";
    try{
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(data);
      final byte[] bA = bos.toByteArray();
      serializedData = Base64.encodeBytes(bA);
      oos.close();
      bos.close();
    } catch (IOException e){
      // TODO: How to fail gracefully?
      e.printStackTrace();
      throw new AssertionError("Serialization of TI_Data failed");
    }
    return new Data.Builder().putString(KEY_TI_DATA, serializedData)
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
    Log.e(TAG, "Could not find a registered user with service id:" + data.getIntroduceeServiceId() + " and phone nr: " + data.getIntroduceeNumber()  +". This introduction failed and will not be retried.");
  }

  @Override protected void onRun() throws Exception {
    //@see RetrieveProfileJob
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Unregistered. Skipping.");
      return;
    }

    //TODO test
    Log.e(TAG, "Retreive Job started!!");
    ServiceId sid = ServiceId.parseOrThrow(data.getIntroduceeServiceId());
    SignalServiceAddress serviceAddress = new SignalServiceAddress(sid);
    ProfileService                                    profileService = ApplicationDependencies.getProfileService();
    Observable<ServiceResponse<ProfileAndCredential>> result         = profileService.getProfile(serviceAddress, Optional.empty(), Optional.empty(), SignalServiceProfile.RequestType.PROFILE, Locale.getDefault()).toObservable();
    // Call db callback and pass the ti_Data & Profile (at least Identity key, phone and aci)
    //@see RetreiveProfileJob
    ServiceResponse<ProfileAndCredential> sr =  result.observeOn(Schedulers.io()).blockingFirst();
    ProfileService.ProfileResponseProcessor processor = new ProfileService.ProfileResponseProcessor(sr);
    TI_RetrieveIDJobResult jobResult = new TI_RetrieveIDJobResult();
    if (processor.notFound()){
      Log.e(TAG, "No user exists with service ID: " + data.getIntroduceeServiceId() + ". Ignoring introduction.");
      return;
    } else if (processor.hasResult()) {
      if (sr.getResult().isPresent()){
        jobResult.data = data;
        SignalServiceProfile profile  =  sr.getResult().get().getProfile();
        jobResult.key = profile.getIdentityKey();
      } else {
        Log.e(TAG, "ServiceResponse.getResult() was empty for service ID: " + data.getIntroduceeServiceId() + ". Ignoring introduction.");
        return;
      }
    } else {
      Log.e(TAG, "Processor did not have a result for service ID: " + data.getIntroduceeServiceId() + ". Ignoring introduction.");
      return;
    }
    TrustedIntroductionsDatabase db = SignalDatabase.trustedIntroductions();
    db.insertIntroductionCallback(jobResult);
  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    if(e instanceof IllegalArgumentException){
      e.printStackTrace();
      Log.e(TAG,"The introduction for " + data.getIntroduceeName() + " with number: " + data.getIntroduceeNumber() + " was not accepted.");
      return false;
    }
    return true;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsRetreiveIdentityJob> {

    @NonNull @Override public TrustedIntroductionsRetreiveIdentityJob create(@NonNull Parameters parameters, @NonNull Data data) {
      // deserialize TI_Data if present
      String serializedTiData = data.getString(KEY_TI_DATA);
      TI_Data d = null;
      if (!serializedTiData.isEmpty()){
        // TODO: What if it is empty?
        try {
          final byte[] bytes = Base64.decode(serializedTiData);
          ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
          ObjectInputStream             ois = new ObjectInputStream(bis);
          d = (TI_Data) ois.readObject();
          ois.close();
          bis.close();
        } catch (IOException | ClassNotFoundException e) {
          // TODO: How to fail gracefully?
          e.printStackTrace();
          throw new AssertionError("Deserialization of TI_Data failed");
        }
      }

      return new TrustedIntroductionsRetreiveIdentityJob(d, parameters);
    }
  }
}


