package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity;

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
    return new Data.Builder()
        .putString(KEY_INTRODUCTION, introduction.serialize())
        .putInt(KEY_NEW_STATE, newState.toInt())
        .putString(KEY_LOG_MESSAGE, logMessage)
        .build();

  }

  @NonNull @Override public String getFactoryKey() {
    return KEY;
  }

  @Override public void onFailure() {
    Recipient introducer = Recipient.resolved(introduction.getIntroducerServiceId());
    // TODO: it would also be nice to add an action that can retry (accept/reject introduction) as a service at some point => .addAction(PendingIntent..)
    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR,
                                                   new NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
                                                       .setSmallIcon(R.drawable.ic_notification)
                                                       .setContentTitle(String.format(context.getString(R.string.TrustedIntroductionsWaitForIdentityJob_OnFailureNotificationTitle), newState.toVerbIng()))
                                                       .setContentText(String.format(context.getString(R.string.TrustedIntroductionsWaitForIdentityJob_OnFailureNotificationText), introducer.getDisplayName(context), introduction.getIntroduceeName()))
                                                       .setContentIntent(PendingIntent.getActivity(context, 0, ManageActivity.createIntent(context, introducer.getId()), PendingIntentFlags.immutable()))
                                                       .setAutoCancel(true)
                                                       .build());
   }

  @Override protected void onRun() throws Exception {
      // TODO: test notification codepath
      throw new NullPointerException("Testing Notification Codepath");
      /*
      TI_Utils.getIdentityKey(introduction.getIntroduceeId());
      // if this does not error out, callback to database
      TrustedIntroductionsDatabase db = SignalDatabase.trustedIntroductions();
      db.setStateCallback(introduction, newState, logMessage);
      */
  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsWaitForIdentityJob> {

    @NonNull @Override public TrustedIntroductionsWaitForIdentityJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new TrustedIntroductionsWaitForIdentityJob(TI_Data.Deserializer.deserialize(data.getString(KEY_INTRODUCTION)),
                                                        State.forState(data.getInt(KEY_NEW_STATE)),
                                                        data.getString(KEY_LOG_MESSAGE),
                                                        parameters);
    }
  }

}
