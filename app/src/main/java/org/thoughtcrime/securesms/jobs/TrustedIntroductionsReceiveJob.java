package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.Base64;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
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
  private boolean bodyParsed;
  private final ArrayList<TI_Data> introductions;
  // counter keeping track of which TI_DATA has made it's way to the database
  // allows to only serialize introductions that have not yet been done if process get's interrupted
  private int                 inserts_succeeded = 0;

  // Serialization Keys
  private static final String KEY_INTRODUCER_ID = "introducer_id";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_MESSAGE_BODY = "messageBody";
  private static final String KEY_BODY_PARSED = "bodyParsed";
  private static final String KEY_INTRODUCTIONS = "serialized_remaining_introduction_data";

  public TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull long timestamp){
    this(introducerId,
         messageBody,
         false,
         timestamp,
         null,
         new Parameters.Builder()
                       .setQueue(introducerId.toQueueKey() + TI_Utils.serializeForQueue(messageBody) + timestamp) // TODO: Why does this need a seperate queue? Can I just enqueue each intro per introducer?
                       .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                       .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                       .addConstraint(NetworkConstraint.KEY)
                       .build());
  }

  private TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull Boolean bodyParsed, @NonNull long timestamp, @Nullable ArrayList<TI_Data> tiData, @NonNull Parameters parameters){
    super(parameters);
    this.introducerId = introducerId;
    this.timestamp = timestamp;
    this.messageBody = messageBody;
    this.bodyParsed = bodyParsed;
    this.introductions = tiData != null ? tiData : new ArrayList<>();
  }

  /**
   * // TODO: Test serialization and deserialization
   * Serialize your job state so that it can be recreated in the future.
   */
  @NonNull @Override public Data serialize() {
    while (inserts_succeeded > 0){
      introductions.remove(0);
      inserts_succeeded--;
    }
    JSONArray serializedIntroductions = new JSONArray();
    for (TI_Data d: introductions){
      serializedIntroductions.put(d.serialize());
    }
    return new Data.Builder()
        .putString(KEY_INTRODUCER_ID, introducerId.serialize())
        .putString(KEY_MESSAGE_BODY, messageBody)
        .putBoolean(KEY_BODY_PARSED, bodyParsed)
        .putString(KEY_INTRODUCTIONS, serializedIntroductions.toString())
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
    if(!bodyParsed){
      List<TI_Data> tiData = parseTIMessage(messageBody, timestamp, introducerId);
      introductions.addAll(tiData);
      bodyParsed = true;
    }
    TrustedIntroductionsDatabase db = SignalDatabase.trustedIntroductions();
    for(TI_Data introduction: introductions){
      long result = db.incomingIntroduction(introduction);
      if (result == -1){
        // TODO: How to fail gracefully?
        throw new AssertionError(TAG + String.format("Introduction insertion for %s failed...", introduction.getIntroduceeName()));
      }
      inserts_succeeded++;
      // Testing
      Log.e(TAG, String.format("Inserted introduction of %s into the database", introduction.getIntroduceeName()));
    }
    Log.e(TAG, "TrustedIntroductionsReceiveJob completed!");
  }

  // TODO
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsReceiveJob> {

    @NonNull @Override public TrustedIntroductionsReceiveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      // Deserialize introduction_data if present
      String serializedIntroductions = data.getString(KEY_INTRODUCTIONS);
      ArrayList<TI_Data> tiData = null;
      Log.e(TAG, serializedIntroductions);
      if (!serializedIntroductions.isEmpty()) {
        try{
          JSONArray arr = new JSONArray(serializedIntroductions);
          for (int i = 0; i < arr.length(); i++){
            // Casting directly since I'm the only one deserializing
            tiData.add(TI_Data.Deserializer.deserialize((String)arr.get(i)));
          }
        } catch (JSONException | NullPointerException e) {
          Log.e(TAG, "JSON deserialization of introductions failed!");
          e.printStackTrace();
        }
      }

      return new TrustedIntroductionsReceiveJob(RecipientId.from(data.getString(KEY_INTRODUCER_ID)),
                                                data.getString(KEY_MESSAGE_BODY),
                                                data.getBoolean(KEY_BODY_PARSED),
                                                data.getLong(KEY_TIMESTAMP),
                                                tiData,
                                                parameters);
    }
  }
}
