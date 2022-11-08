package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Consumer;

public class TrustedIntroductionsRetreiveIdentityJob extends BaseJob{

  public static final String KEY = "TrustedIntroductionsRetreiveIdentityJob";

  private static final String TAG = Log.tag(TrustedIntroductionsRetreiveIdentityJob.class);

  // TI_Data
  private static final String KEY_TI_DATA = "tiData";

  private final TI_Data data;

  public static class TI_RetrieveIDJobResult implements Serializable {
    public TI_Data data;
    public IdentityKey key;
    public String aci;
    public String phone;

    public TI_RetrieveIDJobResult(TI_Data data, IdentityKey key, String aci, String phone){
      this.data = data;
      this.key = key;
      this.aci = aci;
      this.phone = phone;
    }
  }

  /**
   * @param data introduceeId and IntroduceeNumber must be present
   */
  public TrustedIntroductionsRetreiveIdentityJob(@NonNull TI_Data data){
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
    String serializedData;
    try{
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(data);
      serializedData = bos.toString();
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
    //TODO
    // fetch profile
    // Call db callback and pass the ti_Data & Profile (at least Identity key, phone and aci)
  }

  // TODO: should we be more specific here? We just retry always currently.
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
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
          ByteArrayInputStream bis = new ByteArrayInputStream(serializedTiData.getBytes());
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


