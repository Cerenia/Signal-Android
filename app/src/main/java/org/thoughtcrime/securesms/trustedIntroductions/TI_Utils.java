package org.thoughtcrime.securesms.trustedIntroductions;

import android.annotation.SuppressLint;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsReceiveJob;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.json.JSONObject;
import org.json.JSONArray;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.push.ServiceId;

public class TI_Utils {

  static final String TAG = Log.tag(TI_Utils.class);

  // Random String to mark a message as a trustedIntroduction, since I'm tunneling through normal messages
  static final String TI_IDENTIFYER = "QOikEX9PPGIuXfiejT9nC2SsDB8d9AG0dUPQ9gERBQ8qHF30Xj --- This message is part of an experimental feature and not meant to be read by humans --- Introduction Data:\n";
  static final String TI_SEPARATOR = "\n"; // marks start of JsonArray, human friendly
  static final int INDENT_SPACES = 1; // pretty printing for human readableness

  // For safety_number generation
  // @see VerifyDisplayFragment, iterations hardcoded there
  static final int ITERATIONS = 5200;
  // @See length of codes in VerifyDisplayFragment
  static final int SEGMENTS = 12;

  // Constants to pull values out of the cursors
  // Might be worth it to consider using live recipient for all of them... but I only need a few values, not sure
  // what is less overhead and if caching is really relevant here.
  // @see RecipientDatabase
  static final String LOCAL_RECIPIENT_ID = "_id";
  static final String SERVICE_ID = "uuid";
  static final String SORT_NAME = "sort_name"; // From search projection, keeps me from doing name shenanigans
  static final String PHONE = "phone";

  // Json keys
  //static final String INTRODUCER_SERVICE_ID_J = "introducer_uuid"; // TODO: Don't really need this in the message I think... can be inferred when receiving the message
  // Sending the INTRODUCER_SERVICE_ID_J would probably lead to problems if someone spoofs it. Would be preferential to query it when the message is received.
  static final String INTRODUCEE_SERVICE_ID_J = "introducee_uuid";
  static final String NAME_J = "name";
  static final String NUMBER_J = "number";
  static final String IDENTITY_J = "identity_key_base64";
  static final String PREDICTED_FINGERPRINT_J = "safety_number";

  // Job constants
  public static final long TI_JOB_LIFESPAN = TimeUnit.DAYS.toMillis(1);
  public static final int TI_JOB_MAX_ATTEMPTS = Job.Parameters.UNLIMITED;

  // How to format dates in introductions:
  @SuppressLint("SimpleDateFormat") public static final SimpleDateFormat INTRODUCTION_DATE_PATTERN = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");

  //copied from @see VerifyDisplayFragment
  private static @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
    String[]      segments = getSegments(fingerprint, segmentCount);
    StringBuilder result   = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      result.append(segments[i]);

      if (i != segments.length - 1) {
        if (((i + 1) % 4) == 0) result.append('\n');
        else result.append(' ');
      }
    }

    return result.toString();
  }

  //copied from @see VerifyDisplayFragment
  private static String[] getSegments(Fingerprint fingerprint, int segmentCount) {
    String[] segments = new String[segmentCount];
    String   digits   = fingerprint.getDisplayableFingerprint().getDisplayText();
    int      partSize = digits.length() / segmentCount;

    for (int i = 0; i < segmentCount; i++) {
      segments[i] = digits.substring(i * partSize, (i * partSize) + partSize);
    }

    return segments;
  }

  /**
   * Recreates the safety number that is generated between two recipients.
   * (used when sending intro, and to conveniently compute difference on conflict to expose in UI)
   * PRE: Nullable parameters must either ALL BE NULL or NONE BE NULL.
   * @param introductionRecipientId first Recipient
   * @param introduceeId second Recipient (introducee) => Must be present in the local database!
   * @param introduceeServiceId, fetched if null and needed
   * @param introduceeE164, phone nr., fetched if null and needed
   * @param introduceeIdentityKey fetched if null
   * @return The expected safety number as a String, formated into segments identical to the VerifyDisplayFragment
   */
  public static String predictFingerprint(@NonNull RecipientId introductionRecipientId, @NonNull RecipientId introduceeId, @Nullable String introduceeServiceId, @Nullable String introduceeE164, @Nullable IdentityKey introduceeIdentityKey){
    if(introduceeServiceId == null && introduceeE164 == null && introduceeIdentityKey == null){
      // Fetch all the values
      LiveRecipient liveIntroducee = Recipient.live(introduceeId);
      Recipient introduceeResolved = liveIntroducee.resolve();
      introduceeServiceId = introduceeResolved.getServiceId().orElseGet((Supplier<? extends ServiceId>) ServiceId.UNKNOWN).toString();
      introduceeE164 = introduceeResolved.requireE164();
    } else if(introduceeServiceId != null && introduceeE164 != null && introduceeIdentityKey != null){
      //noop, normal case when recipient fetched through cursor
    } else {
      // TODO: Does that make sense??
      assert false: "Unexpected non-null parameter in TI_Utils.predictFingerprint";
    }
    // Initialize version and introduction recipients id & key
    int version;
    byte[]        introductionRecipientFingerprintId;
    byte[] introduceeFingerprintId;
    LiveRecipient live = Recipient.live(introductionRecipientId);
    Recipient introductionRecipientResolved = live.resolve();
    NumericFingerprintGenerator generator = new NumericFingerprintGenerator(ITERATIONS);
    // @see VerifyDisplayFragment for verification version differences
    if (FeatureFlags.verifyV2()){
      version = 2;
      Log.e(TAG, introductionRecipientResolved.requireServiceId().toString());
      introductionRecipientFingerprintId = introductionRecipientResolved.requireServiceId().toByteArray();
      introduceeFingerprintId = introduceeServiceId.getBytes();
    } else {
      version = 1;
      Log.e(TAG, introductionRecipientResolved.requireE164());
      introductionRecipientFingerprintId = introductionRecipientResolved.requireE164().getBytes();
      introduceeFingerprintId = introduceeE164.getBytes();
    }
    IdentityKey introductionRecipientIdentityKey = getIdentityKey(introductionRecipientId);
    // @see VerifyDisplayFragment::initializeFingerprint(), iterations there also hardcoded to 5200 for FingerprintGenerator
    // @see ServiceId.java to understand how they convert the ACI to ByteArray
    // @see IdentityKey.java
    Fingerprint fingerprint = generator.createFor(version,
                                                  introductionRecipientFingerprintId,
                                                  introductionRecipientIdentityKey,
                                                  introduceeFingerprintId,
                                                  introduceeIdentityKey);
    return getFormattedSafetyNumbers(fingerprint, SEGMENTS).replace("\n", "");
  }

  private static IdentityKey getIdentityKey(RecipientId id){
    Optional<IdentityRecord> identityRecord = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(id);
    // If this doesn't work we have a programming error further up the stack, no introduction can be made if we don't have the identity.
    assert identityRecord.isPresent() : TAG + " No identity found for the introduction recipient!";
    return identityRecord.get().getIdentityKey();
  }

  private static String encodeIdentityKey(IdentityKey key){
    return Base64.encodeBytes(key.serialize());
  }

  public static boolean encodedIdentityKeysEqual(RecipientId presentIntroduceeId, String identityKeyIntroduction){
    return encodeIdentityKey(getIdentityKey(presentIntroduceeId)).compareTo(identityKeyIntroduction) == 0;
  }

  @SuppressLint("Range") @WorkerThread
  public static String buildMessageBody(@NonNull RecipientId introductionRecipientId, @NonNull Set<RecipientId> introducees) throws JSONException, IOException, InvalidKeyException {
    assert introducees.size() > 0: TAG + " buildMessageBody called with no Recipient Ids!";

    // TODO: Should I just use the LiveRecipient Stuff instead?  :/ caching etc..
    RecipientDatabase rdb = SignalDatabase.recipients();
    Cursor recipientCursor = rdb.getCursorForSendingTI(introducees);
    JSONArray data = new JSONArray();

    // Loop over all the contacts you want to introduce
    recipientCursor.moveToFirst();
    ArrayList<RecipientId> introduceesList = new ArrayList<>(introducees);
    for(int i = 0; !recipientCursor.isAfterLast(); i++){
      JSONObject introducee = new JSONObject();
      introducee.put(NAME_J, recipientCursor.getString(recipientCursor.getColumnIndex(SORT_NAME)));
      String introduceeE164 = recipientCursor.getString(recipientCursor.getColumnIndex(PHONE));
      introducee.put(NUMBER_J, introduceeE164);
      String introduceeServiceId = recipientCursor.getString(recipientCursor.getColumnIndex(SERVICE_ID));
      introducee.put(INTRODUCEE_SERVICE_ID_J, introduceeServiceId);
      IdentityKey introduceeIdentityKey = getIdentityKey(introduceesList.get(i));
      introducee.put(IDENTITY_J, encodeIdentityKey(introduceeIdentityKey));
      String formatedSafetyNr = predictFingerprint(introductionRecipientId, introduceesList.get(i), introduceeServiceId, introduceeE164, introduceeIdentityKey);
      introducee.put(PREDICTED_FINGERPRINT_J, formatedSafetyNr);
      data.put(introducee);
      recipientCursor.moveToNext();
    }

    recipientCursor.close();

    return TI_IDENTIFYER + TI_SEPARATOR + data.toString(INDENT_SPACES);
  }

  // This structure allows for a oneliner in the processing logic to minimize additional code needed in there.
  public static void handleTIMessage(RecipientId introducer, String message, long timestamp){
    if(!message.contains(TI_IDENTIFYER)) return;
    // Schedule Reception Job
    ApplicationDependencies.getJobManager().add(new TrustedIntroductionsReceiveJob(introducer, message, timestamp));
  }

  @SuppressLint("Range") // keywords exists
  public static @NonNull List<TI_Data> parseTIMessage(String body, long timestamp, RecipientId introducerId){
    if (!body.contains(TI_IDENTIFYER)){
      assert false: "Non TI message passed into parse TI!";
    }
    ArrayList<TI_Data> result = new ArrayList<>();
    String jsonDataS = body.replace(TI_IDENTIFYER, "");
    try {
      JSONArray data = new JSONArray(jsonDataS);
      ArrayList<String> serviceIds = new ArrayList<>();
      // Get all SerciveIds of introducees first to minimize database Queries
      for(int i = 0; i < data.length(); i++){
        JSONObject o = data.getJSONObject(i);
        serviceIds.add(o.getString(INTRODUCEE_SERVICE_ID_J));
      }
      Cursor cursor = SignalDatabase.recipients().getCursorForReceivingTI(serviceIds);
      // Construct TI Data & rebuild serviceId List to only contain the ones present in the database, freeing some memory
      // TODO could this be simplified with ServiceId.known?
      serviceIds = new ArrayList<>();
      if(cursor.getCount() > 0) {
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
          RecipientId introduceeId = RecipientId.from(cursor.getLong(cursor.getColumnIndex(LOCAL_RECIPIENT_ID)));
          String serviceId = cursor.getString(cursor.getColumnIndex(SERVICE_ID));
          serviceIds.add(serviceId);
          String name = cursor.getString(cursor.getColumnIndex(SORT_NAME));
          String phone = cursor.getString(cursor.getColumnIndex(PHONE));
          IdentityKey identityKey = getIdentityKey(introduceeId);
          TI_Data d = new TI_Data(null, null, introducerId, introduceeId, serviceId, name, phone, encodeIdentityKey(identityKey), null, timestamp);
          result.add(d);
          cursor.moveToNext();
        }
        cursor.close();
      }
      // Iterate through JSONData again and create incomplete introductions for the still unknown recipients & set the predictedSecurityNumbers
      for(int i = 0; i < data.length(); i++){
        JSONObject o = data.getJSONObject(i);
        // If data was fetched from local database, simply add the Security number information
        String introduceeServiceId = o.getString(INTRODUCEE_SERVICE_ID_J);
        if (serviceIds.contains(introduceeServiceId)){
          int j = 0;
          while(!result.get(j).getIntroduceeServiceId().equals(introduceeServiceId)) j++;
          assert j < serviceIds.size(): "Programming error in parseTIMessage (size of service IDs vs. JSONArray)";
          result.get(j).setPredictedSecurityNumber(o.getString(PREDICTED_FINGERPRINT_J));
        } else {
          TI_Data d = new TI_Data(null, null, introducerId, null, o.getString(INTRODUCEE_SERVICE_ID_J), o.getString(NAME_J), o.getString(NUMBER_J), o.getString(IDENTITY_J), o.getString(PREDICTED_FINGERPRINT_J), timestamp);
          result.add(d);
        }
      }
    } catch(JSONException e){
      Log.e(TAG, String.format("A JSON exception occured while trying to parse the TI message: %s", jsonDataS));
      Log.e(TAG, e.toString());
      return null;
    }
    return result;
  }

  /**
   * @param o Object, must be serializable.
   * @return A string that can be used for the Job queue key.
   */
  public static String serializeForQueue(Object o){
    MessageDigest md;
    String        hashtext;
    try {
      md = MessageDigest.getInstance("MD5");
      // Serialize introducee Set
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream    oos = new ObjectOutputStream(bos);
      oos.writeObject(o);
      oos.flush();
      // Create digest and convert to Hex String
      byte[]     digest = md.digest(bos.toByteArray());
      BigInteger no     = new BigInteger(1, digest);
      hashtext = no.toString(16);
    } catch (NoSuchAlgorithmException e){
      Log.e(TAG, e.toString());
      Log.e(TAG, e.getMessage());
      assert false: "No such Algorithm!";
      return "InvalidKey";
    } catch (IOException ioe){
      Log.e(TAG, ioe.toString());
      Log.e(TAG, ioe.getMessage());
      assert false: "IO exception!";
      return "InvalidKey";
    }
    return hashtext;
  }

}
