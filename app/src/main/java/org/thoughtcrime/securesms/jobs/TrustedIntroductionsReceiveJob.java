package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
  private final ArrayList<TI_Data> introductions;
  // counter keeping track of which TI_DATA has made it's way to the database
  // allows to only serialize introductions that have not yet been done if process get's interrupted
  private int                 inserts_succeeded = 0;

  // Serialization Keys
  private static final String KEY_INTRODUCER_ID = "introducer_id";
  private static final String KEY_TIMESTAMP = "timestamp";
  private static final String KEY_MESSAGE_BODY = "messageBody";
  private static final String KEY_INTRODUCTIONS = "serialized_remaining_introduction_data";

  public TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull long timestamp){
    this(introducerId,
         messageBody,
         timestamp,
         null,
         new Parameters.Builder()
                       .setQueue(introducerId.toQueueKey() + TI_Utils.serializeForQueue(messageBody))
                       .setLifespan(TI_Utils.TI_JOB_LIFESPAN)
                       .setMaxAttempts(TI_Utils.TI_JOB_MAX_ATTEMPTS)
                       .addConstraint(NetworkConstraint.KEY)
                       .build());
  }

  private TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull long timestamp, @Nullable ArrayList<TI_Data> tiData, @NonNull Parameters parameters){
    super(parameters);
    this.introducerId = introducerId;
    this.timestamp = timestamp;
    this.messageBody = messageBody;
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
    String serializedIntroductions = "";
    try{
      // Serialize remaining List
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream    oos = new ObjectOutputStream(bos);
      oos.writeObject(introductions);
      serializedIntroductions = bos.toString();
      oos.close();
      bos.close();
    } catch (IOException e){
      // TODO: How to fail gracefully?
      e.printStackTrace();
      throw new AssertionError("Serialization of TI_Data failed");
    }
    return new Data.Builder()
        .putString(KEY_INTRODUCER_ID, introducerId.serialize())
        .putString(KEY_MESSAGE_BODY, messageBody)
        .putString(KEY_INTRODUCTIONS, serializedIntroductions)
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
    if(introductions.isEmpty()){
      List<TI_Data> tiData = parseTIMessage(messageBody, timestamp, introducerId);
      introductions.addAll(tiData);
    }
    TrustedIntroductionsDatabase db = SignalDatabase.trustedIntroductions();
    for(TI_Data introduction: introductions){
      db.incomingIntroduction(introduction);
      inserts_succeeded++;
      // TODO: What if insert fails? (-1 answer)
      // Testing
      Log.e(TAG, String.format("Inserted introduction of %s into the database", introduction.getIntroduceeName()));
    }
  }

  // TODO
  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  public static final class Factory implements Job.Factory<TrustedIntroductionsReceiveJob> {

    @NonNull @Override public TrustedIntroductionsReceiveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      // Deserialize introduction_data if present
      String serialized_introduction_data = data.getString(KEY_INTRODUCTIONS);
      ArrayList<TI_Data> tiData = null;
      if (!serialized_introduction_data.isEmpty()) {
        try {
          ByteArrayInputStream bis = new ByteArrayInputStream(serialized_introduction_data.getBytes());
          ObjectInputStream    ois = new ObjectInputStream(bis);
          tiData = (ArrayList<TI_Data>) ois.readObject();
          ois.close();
          bis.close();
        } catch (IOException | ClassNotFoundException e) {
          // TODO: How to fail gracefully?
          // Right now, list just gets lost.
          e.printStackTrace();
          //assert false : "Deserialization of TI_Data list failed";
        }
      }

      // private TrustedIntroductionsReceiveJob(@NonNull RecipientId introducerId, @NonNull String messageBody, @NonNull long timestamp, @Nullable ArrayList<TI_Data> tiData, @NonNull Parameters parameters)
      return new TrustedIntroductionsReceiveJob(RecipientId.from(data.getString(KEY_INTRODUCER_ID)),
                                                data.getString(KEY_MESSAGE_BODY),
                                                data.getLong(KEY_TIMESTAMP),
                                                tiData,
                                                parameters);
    }
  }
}
