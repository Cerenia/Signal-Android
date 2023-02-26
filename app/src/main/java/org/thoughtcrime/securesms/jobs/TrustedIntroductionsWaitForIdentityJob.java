package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.common.util.Strings;
import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase.State;
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity;

public class TrustedIntroductionsWaitForIdentityJob extends BaseJob {

  public static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionsWaitForIdentityJob.class));

  private static final String KEY = "TIWaitIdentityJob";

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
    Recipient introducer = Recipient.resolved(introduction.getIntroducerId());
    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR,
                                                   new NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
                                                       .setSmallIcon(R.drawable.ic_notification)
                                                       .setContentTitle(String.format(context.getString(R.string.TrustedIntroductionsWaitForIdentityJob_OnFailureNotificationTitle), newState.toVerbIng()))
                                                       .setContentText(String.format(context.getString(R.string.TrustedIntroductionsWaitForIdentityJob_OnFailureNotificationText), introducer.getDisplayName(context), introduction.getIntroduceeName()))
                                                       .setContentIntent(PendingIntent.getActivity(context, 0, ManageActivity.createIntent(context, introducer.getId()), PendingIntentFlags.immutable()))
                                                       .setAutoCancel(true)
                                                       .addAc
                                                       .build());
    // NotificationCompat.Builder builder = new NotificationCompat.Builder(this, )
    //Snackbar snackbar = Snackbar.make(context, String.format(R.string.TrustedIntroductionsWaitForIdentityJob_OnFailureSnackbar, newState.toVerbIng(), introduction.getIntroduceeName(), introducer.getShortDisplayName(context)), Snackbar.LENGTH_LONG);
  }

  @Override protected void onRun() throws Exception {

  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }
}
