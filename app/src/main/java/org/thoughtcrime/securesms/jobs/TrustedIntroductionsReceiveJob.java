package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.recipients.RecipientId;

public class TrustedIntroductionsReceiveJob extends BaseJob  {

  private static final String TAG = Log.tag(TrustedIntroductionsReceiveJob.class);

  // Factory Key
  public static final String KEY = "TIReceiveJob";

  private final RecipientId introducerId;
  private final String messageBody;

  // Serialization Keys
  private static final String KEY_INTRODUCTION_RECIPIENT_ID = "introduction_recipient_id";
  private static final String KEY_MESSAGE_BODY = "messageBody";

  public TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody){
    this(introducerId,
         messageBody,
         new Parameters.Builder());
  }

  private TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull Parameters parameters){
    super(parameters);
    this.introducerId = introducerId;
    this.messageBody = messageBody;
  }

  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public Data serialize() {
    return null;
  }

  /**
   * Returns the key that can be used to find the relevant factory needed to create your job.
   */
  @NonNull @Override public String getFactoryKey() {
    return null;
  }

  /**
   * Called when your job has completely failed and will not be run again.
   */
  @Override public void onFailure() {

  }

  @Override protected void onRun() throws Exception {

  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }
}
