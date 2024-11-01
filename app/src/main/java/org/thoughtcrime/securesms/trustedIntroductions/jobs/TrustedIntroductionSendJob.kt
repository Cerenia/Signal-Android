package org.thoughtcrime.securesms.trustedIntroductions.jobs

import android.net.Uri
import org.signal.core.util.logging.Log.e
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient.Companion.live
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.buildMessageBody
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.serializeForQueue
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.stream.Collectors

class TrustedIntroductionSendJob private constructor(introducerRecipientId: RecipientId, introductionRecipientId: RecipientId, introduceeIds: Set<RecipientId>, parameters: Parameters) :
  BaseJob(parameters) {
  private val introducerRecipientId: RecipientId
  private val introductionRecipientId: RecipientId
  private val introduceeIds: Set<RecipientId>

  constructor(introducerRecipientId: RecipientId, introductionRecipientId: RecipientId, introduceeIds: Set<RecipientId>) : this(
    introducerRecipientId,
    introductionRecipientId,
    introduceeIds,
    Parameters.Builder()
      .setQueue(introductionRecipientId.toQueueKey() + serializeForQueue(introduceeIdSetToLong(introduceeIds)))
      .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
      .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
      .addConstraint(NetworkConstraint.KEY)
      .build()
  )

  init {
    if (introduceeIds.isEmpty()) {
      // TODO: What do I do in this case? should not happen.
      throw AssertionError()
    }
    this.introducerRecipientId = introducerRecipientId
    this.introductionRecipientId = introductionRecipientId
    this.introduceeIds = introduceeIds
  }

  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
        .putString(KEY_INTRODUCER_RECIPIENT_ID, introducerRecipientId.serialize())
        .putString(KEY_INTRODUCTION_RECIPIENT_ID, introductionRecipientId.serialize())
        .putLongListAsArray(KEY_INTRODUCEE_IDS, introduceeIds.stream().map { obj: RecipientId -> obj.toLong() }.collect(Collectors.toList()))
        .build().serialize()
  }

  /**
   * Returns the key that can be used to find the relevant factory needed to create your job.
   */
  override fun getFactoryKey(): String {
    return KEY
  }

  /**
   * Called when your job has completely failed and will not be run again.
   */
  override fun onFailure() {
    e(TAG, String.format(Locale.ENGLISH, "Failed to introduce %d contacts to %s", introduceeIds.size, introductionRecipientId.toString()))
  }

  /**
   * Builds a Trusted Introduction with the data passed through the constructor of the job.
   * If it succeeds to build the body (would, e.g., fail if invalid Recipient IDs were passed for any entity),
   * the data gets tunneled through Signal's document Attachment mechanism.
   */
  @Throws(Exception::class)
  override fun onRun() {
    val body = buildMessageBody(introducerRecipientId, introductionRecipientId, introduceeIds)
    val liveIntroductionRecipient = live(introductionRecipientId)
    val introductionRecipient = liveIntroductionRecipient.resolve()
    val uri = BlobProvider.getInstance().forData(body.toByteArray(StandardCharsets.UTF_8)).withMimeType(TI_Utils.TI_MIME_TYPE).withFileName(TI_Utils.TI_MESSAGE_FILENAME).createForSingleUseInMemory()
    val attachmentList = getAttachments(uri)
    // TODO: this is bad v
    val msgBody = "I would like to introduce some people to you, please navigate to the Trusted Introductions management screen to see the new introductions."
    // TODO: should extract this ^ to a resource string, with appropriate localization placeholders!
    val message = OutgoingMessage(
      introductionRecipient,
      msgBody,
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
      emptyList(),
      emptyList(),
      emptyList(),
      emptySet(),
      emptySet(),
      null,
      true,
      null,
      -1,
      0
    )
    MessageSender.send(context, message, -1, MessageSender.SendType.SIGNAL, null, null)
  }

  // TODO: should we be more specific here? We just retry always currently.
  override fun onShouldRetry(e: Exception): Boolean {
    return true
  }

  class Factory : Job.Factory<TrustedIntroductionSendJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TrustedIntroductionSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return TrustedIntroductionSendJob(
        RecipientId.from(data.getString(KEY_INTRODUCER_RECIPIENT_ID)),
        RecipientId.from(data.getString(KEY_INTRODUCTION_RECIPIENT_ID)),
        data.getLongArrayAsList(KEY_INTRODUCEE_IDS).stream().map { id: Long? -> RecipientId.from(id!!) }.collect(Collectors.toSet()),
        parameters
      )
    }
  }

  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, tag(TrustedIntroductionSendJob::class.java))

    // Factory Key
    const val KEY: String = "TISendJob"

    // Serialization Keys
    private const val KEY_INTRODUCER_RECIPIENT_ID = "introducer_recipient_id"
    private const val KEY_INTRODUCTION_RECIPIENT_ID = "introduction_recipient_id"
    private const val KEY_INTRODUCEE_IDS = "introducee_recipient_ids"


    /**
     * Makes sure this parameter of the job is serializable for queue key creation.
     * TODO: Is this reused? should that be somewhere else?
     */
    private fun introduceeIdSetToLong(introduceeIds: Set<RecipientId>): Set<Long> {
      val result: MutableSet<Long> = HashSet()
      // Can't do this, min API too low
      //introduceeIds.forEach((id) -> result.add(id.toLong()));
      for (id in introduceeIds) {
        result.add(id.toLong())
      }
      return result
    }

    private fun getAttachments(uri: Uri): ArrayList<Attachment> {
      val a: Attachment = UriAttachment(
        uri,
        TI_Utils.TI_MIME_TYPE,
        AttachmentTable.TRANSFER_PROGRESS_PENDING,
        0,
        0,
        0,
        TI_Utils.TI_MESSAGE_FILENAME,
        null,
        voiceNote = false,
        borderless = false,
        videoGif = false,
        quote = false,
        caption = null,
        stickerLocator = null,
        blurHash = null,
        audioHash = null,
        transformProperties = null
      )
      val attachmentList = ArrayList<Attachment>()
      attachmentList.add(a)
      return attachmentList
    }
  }
}
