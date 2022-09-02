package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.List;
import java.util.Locale;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.parseTIMessage;

public class TrustedIntroductionsReceiveJob extends BaseJob  {

  private static final String TAG = Log.tag(TrustedIntroductionsReceiveJob.class);

  // Factory Key
  public static final String KEY = "TIReceiveJob";

  private final RecipientId introducerId;
  private final long timestamp;
  private final String messageBody;

  // Serialization Keys
  private static final String KEY_INTRODUCER_ID = "introducer_id";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_MESSAGE_BODY = "messageBody";

  public TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull long timestamp){
    this(introducerId,
         messageBody,
         timestamp,
         new Parameters.Builder()
                       .setQueue(introducerId.toQueueKey() + TI_Utils.serializeForQueue(messageBody))
                       .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                       .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                       .addConstraint(NetworkConstraint.KEY)
                       .build());
  }

  private TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull long timestamp, @NonNull Parameters parameters){
    super(parameters);
    this.introducerId = introducerId;
    this.timestamp = timestamp;
    this.messageBody = messageBody;
  }

  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public Data serialize() {
    return new Data.Builder()
        .putString(KEY_INTRODUCER_ID, introducerId.serialize())
        .putString(KEY_MESSAGE_BODY, messageBody)
        .putLong(KEY_TIMESTAMP, timestamp)
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
    Log.e(TAG, String.format(Locale.ENGLISH, "Failed to write introductions into the database originating from this message %s", messageBody));
  }


  @Override protected void onRun() throws Exception {
    List<TI_Data> tiData = parseTIMessage(messageBody, timestamp);
    for(TI_Data introduction: tiData){

    }
  }

  // TODO
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsReceiveJob> {

    @NonNull @Override public TrustedIntroductionsReceiveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new TrustedIntroductionsReceiveJob(RecipientId.from(data.getString(KEY_INTRODUCER_ID)),
                                                data.getString(KEY_MESSAGE_BODY),
                                                data.getLong(KEY_TIMESTAMP),
                                                parameters);
    }
  }
}
