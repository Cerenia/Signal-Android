package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;

public class TrustedIntroductionsWaitForIdentityJob extends BaseJob {

  public static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionsWaitForIdentityJob.class));

  public static final String KEY = "TIWaitIdentityJob";

  // Preserve arguments from setState
  private final TI_Data introduction;
  private final State newState;
  private final String logMessage;

  // Serialization Keys
  private static final String KEY_INTRODUCTION = "introduction";
  private static final String KEY_NEW_STATE = "new_state";
  private static final String KEY_LOG_MESSAGE = "log_message";

  public TrustedIntroductionsWaitForIdentityJob(@NonNull TI_Data introduction, @NonNull State newState, @NonNull String logMessage) {
    this(introduction,
         newState,
         logMessage,
         new Parameters.Builder()
             .setQueue(introduction.getIntroduceeServiceId())
             .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
             .setMaxAttempts(TI_Utils.TI_JOB_IDENTITY_WAIT_MAX_ATTEMPTS)
             .addConstraint(NetworkConstraint.KEY)
             .build());
  }

  private TrustedIntroductionsWaitForIdentityJob(@NonNull TI_Data introduction, @NonNull State newState, @NonNull String logMessage, @NonNull Job.Parameters parameters) {
    super(parameters);
    this.introduction = introduction;
    this.newState = newState;
    this.logMessage = logMessage;
  }


  @NonNull @Override public Data serialize() {
    return null;
  }

  @NonNull @Override public String getFactoryKey() {
    return null;
  }

  @Override public void onFailure() {

  }

  @Override protected void onRun() throws Exception {

  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }
}
