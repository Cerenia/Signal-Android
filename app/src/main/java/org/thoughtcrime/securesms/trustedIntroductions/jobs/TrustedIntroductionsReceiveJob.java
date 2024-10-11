package org.thoughtcrime.securesms.trustedIntroductions.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;

import org.json.JSONObject;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobs.BaseJob;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.constructIntroduceesFromTrustedIntrosString;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.getIntroducerFromRawMessage;

public class TrustedIntroductionsReceiveJob extends BaseJob {

  private static final String TAG =  String.format(TI_Utils.TI_LOG_TAG, Log.tag(TrustedIntroductionsReceiveJob.class));

  // Factory Key
  public static final String KEY = "TIReceiveJob";

  private RecipientId introducerId;
  private final long timestamp;
  private final String messageBody;
  private boolean bodyParsed;
  private ArrayList<TI_Data> introductions = new ArrayList<>();
  // counter keeping track of which TI_DATA has made it's way to the database
  // allows to only serialize introductions that have not yet been done if process get's interrupted
  private int                 inserts_succeeded = 0;

  // Serialization Keys
  private static final String KEY_INTRODUCER_ID = "introducer_id";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_MESSAGE_BODY = "messageBody";
  private static final String KEY_BODY_PARSED = "bodyParsed";
  private static final String KEY_INTRODUCTIONS = "serialized_remaining_introduction_data";

  public TrustedIntroductionsReceiveJob(@NonNull String messageBody, @NonNull long timestamp){
    this(null,
         messageBody,
         false,
         timestamp,
         null,
         new Parameters.Builder()
                       .setQueue(TI_Utils.serializeForQueue(messageBody) + timestamp)
                       .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                       .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                       .addConstraint(NetworkConstraint.KEY)
                       .build());
  }

  /**
  * Because the tunneling mechanism was changed from using a plain text message to an attachment, we now only instantiate this job when processing the attachment in the AttachmentTable. The Recipient from which the message
  * came from is not as easily accessible as it was when we were instantiating this job in the `IncomingMessageProcessor` and is now parsed from the message body. Thus the introducerId will first be null and only be instantiated
  * after the body was parsed.
  * This has the additional advantage that we could in principle write a standalone viewer for introductions, that eats '*.trustedIntroduction' files, since the introducer is no longer assumed based on the thread the message
  * was forwarded on, making it more useful for users that do not have the modified Client of Signal installed.
  *
  **/
  private TrustedIntroductionsReceiveJob(@Nullable RecipientId introducerId, @NonNull String messageBody, @NonNull Boolean bodyParsed, @NonNull long timestamp, @Nullable ArrayList<TI_Data> tiData, @NonNull Parameters parameters){
    super(parameters);
    this.introducerId = introducerId;
    this.timestamp = timestamp;
    this.messageBody = messageBody;
    this.bodyParsed = bodyParsed;
    this.introductions = !(tiData == null || tiData.isEmpty()) ? tiData : new ArrayList<>();
  }

  /**
   *
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public byte[] serialize() {
    while (inserts_succeeded > 0){
      introductions.remove(0);
      inserts_succeeded--;
    }
    JSONArray serializedIntroductions = new JSONArray();
    for (TI_Data d: introductions){
      serializedIntroductions.put(d.serialize());
    }
    return Objects.requireNonNull(new JsonJobData.Builder()
                                      .putString(KEY_INTRODUCER_ID, introducerId == null ? "NULL" : introducerId.serialize())
                                      .putString(KEY_MESSAGE_BODY, messageBody)
                                      .putBoolean(KEY_BODY_PARSED, bodyParsed)
                                      .putString(KEY_INTRODUCTIONS, serializedIntroductions.toString())
                                      .putLong(KEY_TIMESTAMP, timestamp)
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
    Log.e(TAG, String.format(Locale.ENGLISH, "Failed to write introductions into the database originating from this message %s", messageBody));
  }


  @Override protected void onRun() throws Exception {
    if (introducerId == null){
      introducerId = getIntroducerFromRawMessage(messageBody);
    }
    if(!bodyParsed){
      List<TI_Data> tiData = constructIntroduceesFromTrustedIntrosString(messageBody, timestamp, introducerId);
      if(tiData == null) {
        Log.e(TAG, "Introduction did not parse correctly, aborting!");
        return;
      }
      introductions.addAll(tiData);
      bodyParsed = true;
    }
    TI_DatabaseGlue db = SignalDatabase.tiDatabase();
    for(TI_Data introduction: introductions){
      long result = db.incomingIntroduction(introduction);
      if (result == -1){
        // TODO: How to fail gracefully?
        Log.e(TAG, String.format("Introduction insertion for %s failed...", introduction.getIntroduceeName()));
        //throw new AssertionError(TAG + String.format("Introduction insertion for %s failed...", introduction.getIntroduceeName()));
      }
      inserts_succeeded++;
    }
    Log.i(TAG, "TrustedIntroductionsReceiveJob completed!");
  }

  // TODO
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsReceiveJob> {

    @NonNull @Override public TrustedIntroductionsReceiveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      // Deserialize introduction_data if present
      JsonJobData data = JsonJobData.deserialize(serializedData);
      String serializedIntroductions = data.getString(KEY_INTRODUCTIONS);
      ArrayList<TI_Data> tiData = new ArrayList<>();
      Log.i(TAG, serializedIntroductions);
      if (!serializedIntroductions.isEmpty()) {
        try{
          JSONArray arr = new JSONArray(serializedIntroductions);
          for (int i = 0; i < arr.length(); i++){
            tiData.add(TI_Data.Deserializer.deserialize(new JSONObject(arr.getString(i))));
          }
        } catch (JSONException | NullPointerException e) {
          e.printStackTrace();
          // TODO: fail gracefully
          throw new AssertionError("JSON deserialization of introductions failed!");
        }
      }
      RecipientId introducer = null;
      if (!data.getString(KEY_INTRODUCER_ID).equals("NULL")){
        introducer = RecipientId.from(data.getString(KEY_INTRODUCER_ID));
      }
      return new TrustedIntroductionsReceiveJob(introducer,
                                                data.getString(KEY_MESSAGE_BODY),
                                                data.getBoolean(KEY_BODY_PARSED),
                                                data.getLong(KEY_TIMESTAMP),
                                                tiData,
                                                parameters);
    }
  }
}
