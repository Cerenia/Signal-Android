package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class TrustedIntroductionsRetreiveIdentityJob extends BaseJob{

  public static final String KEY = "TrustedIntroductionsRetreiveIdentityJob";

  private static final String TAG = Log.tag(TrustedIntroductionsRetreiveIdentityJob.class);

  // TI_Data
  private static final String KEY_TI_DATA = "tiData";

  private final TI_Data data;

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
  }

  // TODO: should we be more specific here? We just retry always currently.
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }
}
