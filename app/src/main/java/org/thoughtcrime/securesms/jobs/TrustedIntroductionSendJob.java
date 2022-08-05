package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.Job.Factory;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TrustedIntroductionSendJob extends BaseJob {

  private static final String TAG = Log.tag(TrustedIntroductionSendJob.class);

  // Factory Key
  public static final String KEY = "TISendJob";

  private final RecipientId introductionRecipientId;
  private final List<RecipientId> introduceeIds;

  // Serialization Keys
  private static final String KEY_INTRODUCTION_RECIPIENT_ID = "introduction_recipient_id";
  private static final String KEY_INTRODUCEE_IDS = "introducee_recipient_ids";

  // TODO: How about enforcing rate limiting?
  // TODO: You should not be able to send the same job again if you've already tried earlier with the same introducees...
  // TODO: Now that I think about it... Ideally one would query if a Job already exists that goes to the introductionRecipient and just add whichever
  // TODO: Peeps you want to additionally introduce to the List of this Job if it hasn't run yet... Prolly a good idea to listen to .cancel() and recreate if necessary?


  public TrustedIntroductionSendJob(@NonNull RecipientId introductionRecipientId, @NonNull List<RecipientId> introduceeIds){
    this(introductionRecipientId,
         introduceeIds,
         new Parameters.Builder()
                       .setQueue(introductionRecipientId.toQueueKey())
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .addConstraint(NetworkConstraint.KEY)
                       .build());
  }

  private TrustedIntroductionSendJob(@NonNull RecipientId introductionRecipientId, @NonNull List<RecipientId> introduceeIds, @NonNull Parameters parameters) {
    super(parameters);
    if (introduceeIds.isEmpty()){
      // TODO: What do I do in this case? should not happen.
      throw new AssertionError();
    }
    this.introductionRecipientId = introductionRecipientId;
    this.introduceeIds = introduceeIds;
  }


  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public Data serialize() {
    return new Data.Builder()
                    .putString(KEY_INTRODUCTION_RECIPIENT_ID, introductionRecipientId.serialize())
                    .putLongListAsArray(KEY_INTRODUCEE_IDS, introduceeIds.stream().map(RecipientId::toLong).collect(Collectors.toList()))
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
    Log.e(TAG, String.format(Locale.ENGLISH,"Failed to introduce %d contacts to %s", introduceeIds.size(), introductionRecipientId.toString()));
  }

  @Override protected void onRun() throws Exception {
    // For now, just query the database and create the fingerprint for each recipient
    // TODO, how do I encapsulate this task, probably in a Whisper message? Rebuild what they do there I guess.. idk if this will mess with my state though. Definitely needs to be encrypted though!

  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    // TODO: ... HM, which exceptions should I NOT retry on? for now following example of ResendMessageJob
    return e instanceof PushNetworkException;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionSendJob> {

    @Override
    public @NonNull TrustedIntroductionSendJob create(@NonNull Parameters params, @NonNull Data data){
      //  TODO
      return null;
    }
  }
}
