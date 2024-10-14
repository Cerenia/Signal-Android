package org.thoughtcrime.securesms.trustedIntroductions.jobs;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentCreator;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobs.BaseJob;
import org.thoughtcrime.securesms.media.UriMediaInput;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaUploadRepository;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.FileProviderUtil;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.StorageUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class TrustedIntroductionSendJob extends BaseJob {

  private static final String TAG =  String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionSendJob.class));

  // Factory Key
  public static final String KEY = "TISendJob";

  private final RecipientId introducerRecipientId;
  private final RecipientId introductionRecipientId;
  private final Set<RecipientId>  introduceeIds;

  // Serialization Keys
  private static final String KEY_INTRODUCER_RECIPIENT_ID = "introducer_recipient_id";
  private static final String KEY_INTRODUCTION_RECIPIENT_ID = "introduction_recipient_id";
  private static final String KEY_INTRODUCEE_IDS = "introducee_recipient_ids";


  public TrustedIntroductionSendJob(@NonNull RecipientId introducerRecipientId, @NonNull RecipientId introductionRecipientId, @NonNull Set<RecipientId> introduceeIds){
    this(introducerRecipientId,
        introductionRecipientId,
         introduceeIds,
         new Parameters.Builder()
                       .setQueue(introductionRecipientId.toQueueKey() + TI_Utils.serializeForQueue(introduceeIdSetToLong(introduceeIds)))
                       .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                       .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                       .addConstraint(NetworkConstraint.KEY)
                       .build());
  }

  private TrustedIntroductionSendJob(@NonNull RecipientId introducerRecipientId, @NonNull RecipientId introductionRecipientId, @NonNull Set<RecipientId> introduceeIds, @NonNull Parameters parameters) {
    super(parameters);
    if (introduceeIds.isEmpty()){
      // TODO: What do I do in this case? should not happen.
      throw new AssertionError();
    }
    this.introducerRecipientId = introducerRecipientId;
    this.introductionRecipientId = introductionRecipientId;
    this.introduceeIds = introduceeIds;
  }

  /**
   * Makes sure this parameter of the job is serializable for queue key creation.
   * TODO: Is this reused? should that be somewhere else?
   */
  private static @NonNull Set<Long> introduceeIdSetToLong(@NonNull Set<RecipientId> introduceeIds){
    Set<Long> result = new HashSet<>();
    // Can't do this, min API too low
    //introduceeIds.forEach((id) -> result.add(id.toLong()));
    for (RecipientId id: introduceeIds) {
      result.add(id.toLong());
    }
    return result;
  }

  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public byte[] serialize() {
    return Objects.requireNonNull(new JsonJobData.Builder()
                                      .putString(KEY_INTRODUCER_RECIPIENT_ID, introducerRecipientId.serialize())
                                      .putString(KEY_INTRODUCTION_RECIPIENT_ID, introductionRecipientId.serialize())
                                      .putLongListAsArray(KEY_INTRODUCEE_IDS, introduceeIds.stream().map(RecipientId::toLong).collect(Collectors.toList()))
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
    Log.e(TAG, String.format(Locale.ENGLISH,"Failed to introduce %d contacts to %s", introduceeIds.size(), introductionRecipientId.toString()));
  }

  /**
   * Builds a Trusted Introduction with the data passed through the constructor of the job.
   * If it succeeds to build the body (would, e.g., fail if invalid Recipient IDs were passed for any entity),
   * the data gets tunneled through Signal's document Attachment mechanism.
   *
   */
  @Override protected void onRun() throws Exception {
    String body = TI_Utils.buildMessageBody(introducerRecipientId, introductionRecipientId, introduceeIds);
    LiveRecipient liveIntroductionRecipient = Recipient.live(introductionRecipientId);
    Recipient introductionRecipient = liveIntroductionRecipient.resolve();
    Uri uri = BlobProvider.getInstance().forData(body.getBytes(StandardCharsets.UTF_8)).withMimeType(TI_Utils.TI_MIME_TYPE).withFileName(TI_Utils.TI_MESSAGE_FILENAME).createForSingleUseInMemory();
    Attachment a = new UriAttachment(uri,
                                     TI_Utils.TI_MIME_TYPE,
                                     AttachmentTable.TRANSFER_PROGRESS_PENDING,
                                     0,
                                     0,
                                     0,
                                     TI_Utils.TI_MESSAGE_FILENAME,
                                     null,
                                     false,
                                     false,
                                     false,
                                     false,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null
                                     );
    ArrayList<Attachment> attachmentList = new ArrayList<>();
    attachmentList.add(a);
    OutgoingMessage message =  new OutgoingMessage(introductionRecipient,
                                                   "I would like to introduce you to some people, please navigate to the Trusted Introductions management screen to see the new introductions.",
                                                   attachmentList,
                                                   System.currentTimeMillis(),
                                                   0,
                                                   1,
                                                   false,
                                                   ThreadTable.DistributionTypes.DEFAULT,
                                                   StoryType.NONE,
                                                   null,
                                                   false,
                                                   null,
                                                   Collections.emptyList(),
                                                   Collections.emptyList(),
                                                   Collections.emptyList(),
                                                   Collections.emptySet(),
                                                   Collections.emptySet(),
                                                   null,
                                                   true,
                                                   null,
                                                   -1,
                                                   0);
    MessageSender.send(context, message, -1, MessageSender.SendType.SIGNAL, null, null);
  }

  // TODO: should we be more specific here? We just retry always currently.
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionSendJob> {

    @NonNull @Override public TrustedIntroductionSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new TrustedIntroductionSendJob(RecipientId.from(data.getString(KEY_INTRODUCER_RECIPIENT_ID)),
                                            RecipientId.from(data.getString(KEY_INTRODUCTION_RECIPIENT_ID)),
                                            data.getLongArrayAsList(KEY_INTRODUCEE_IDS).stream().map(RecipientId::from).collect(Collectors.toSet()),
                                            parameters);
    }
  }
}
