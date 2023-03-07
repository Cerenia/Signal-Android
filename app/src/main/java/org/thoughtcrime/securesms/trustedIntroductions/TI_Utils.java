package org.thoughtcrime.securesms.trustedIntroductions;

import android.annotation.SuppressLint;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob;
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
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.ServiceId;

import static org.webrtc.ContextUtils.getApplicationContext;

// TODO: May be able to simplify further by using JsonUtil.java in codebase...
// Serialization for each object that I am sending is already present..

public class TI_Utils {

  // Prefix all logging with this tag for ease of search
  public static final String TI_LOG_TAG = "_TI:%s";
  static final String TAG = String.format(TI_LOG_TAG, Log.tag(TI_Utils.class));

  // Version, change if you change data/message format for compatibility
  // TODO: this is currently only reflected in message format, would need to add this to Database to make
  // Backup/Restore work accross revisions
  public static final String TI_MESSAGE_VERSION = "1.0";
  // Since the Signal version is still important and will not be overwritten I define my own
  // 1: majour changes, 2: feature changes, 3. bug/stability fixes
  public static final String TI_APK_VERSION = "1.0.1";

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
  static final String SERVICE_ID = "uuid";
  static final String SORT_NAME = "sort_name"; // From search projection, keeps me from doing name shenanigans
  static final String PHONE = "phone";

  // Json keys
  // TODO: May want to add that to be part of the introduction at some point. This way we can avoid crashed on importing old backups with version missmatches
  static final String TI_VERSION_J = "ti_version";
  static final String INTRODUCEE_DATA_J = "introducees";
  static final String INTRODUCEE_SERVICE_ID_J = "introducee_uuid";
  static final String NAME_J = "name";
  static final String NUMBER_J = "number";
  static final String IDENTITY_J = "identity_key_base64";
  static final String PREDICTED_FINGERPRINT_J = "safety_number";

  // Job constants
  public static final long TI_JOB_LIFESPAN = TimeUnit.DAYS.toMillis(1);
  // TODO: debugging
  public static final int TI_JOB_MAX_ATTEMPTS = Job.Parameters.UNLIMITED;
  //public static final int TI_JOB_MAX_ATTEMPTS = 1; // TODO: here to avoid infinite crashes for now..
  // TODO: Testing
  //public static final int TI_JOB_IDENTITY_WAIT_MAX_ATTEMPTS = 1;
  public static final int TI_JOB_IDENTITY_WAIT_MAX_ATTEMPTS = 5;

  // How to format dates in introductions:
  @SuppressLint("SimpleDateFormat") public static final SimpleDateFormat INTRODUCTION_DATE_PATTERN = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");

  /**
   * /@see ManageListFragment::getFiltered()
   *
   * @param timestamp the timestamp as long
   * @return The date in 6 parts, in order of the format string, as strings
   */
  public static @NonNull TimestampDateParts splitIntroductionDate(long timestamp){
    String date = INTRODUCTION_DATE_PATTERN.format(timestamp);
    String[] dateTime = date.split(" ");
    String[] dateParts = dateTime[0].split("/");
    String[] timeParts = dateTime[1].split(":");
    return new TimestampDateParts(dateParts[0],
                                  dateParts[1],
                                  dateParts[2],
                                  timeParts[0],
                                  timeParts[1],
                                  timeParts[2]);
  }

  public static class TimestampDateParts {
    public String year;
    public String month;
    public String day;
    public String hours;
    public String minutes;
    public String seconds;

    public TimestampDateParts(String year, String month, String day, String hours, String minutes, String seconds){
      this.year = year;
      this.month = month;
      this.day = day;
      this.hours = hours;
      this.minutes = minutes;
      this.seconds = seconds;
    }
  }

  //@see VerifyDisplayFragment
  private static @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
    String[]      segments = getSegments(fingerprint, segmentCount);
    StringBuilder result   = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      result.append(segments[i]);

      if (i != segments.length - 1) {
        result.append(' ');
      }
    }

    return result.toString();
  }

  //@see VerifyDisplayFragment
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
   * @return The expected safety number as a String, formated into segments identical to the VerifyDisplayFragment TODO: fix whacky formatting (some whitespaces missing)
   */
  public static String predictFingerprint(@NonNull RecipientId introductionRecipientId, @NonNull RecipientId introduceeId, @Nullable String introduceeServiceId, @Nullable String introduceeE164, @Nullable IdentityKey introduceeIdentityKey) throws TI_MissingIdentityException {
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
      Log.i(TAG, "using " + introductionRecipientResolved.requireServiceId());
      introductionRecipientFingerprintId = introductionRecipientResolved.requireServiceId().toByteArray();
      introduceeFingerprintId = introduceeServiceId.getBytes();
    } else {
      version = 1;
      Log.i(TAG, "using " + introductionRecipientResolved.requireE164());
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

  /**
   * Also used in  TrustedIntroductionsDatabase
   * @param id recipient ID
   * @return their identity as saved in the Identity database
   */
  public static IdentityKey getIdentityKey(RecipientId id) throws TI_MissingIdentityException {
    Optional<IdentityRecord> identityRecord = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(id);
    // If this doesn't work we have a programming error further up the stack, no introduction can be made if we don't have the identity.
    if(!identityRecord.isPresent()){
      throw new TI_MissingIdentityException(TAG + " No identity found for the recipient with id: " + id);
    }
    return identityRecord.get().getIdentityKey();
  }

  public static class TI_MissingIdentityException extends Exception {
    // throw this if the identity is not found in the database
    TI_MissingIdentityException(String message){
      super(message);
    }
  }

  private static String encodeIdentityKey(IdentityKey key){
    return Base64.encodeBytes(key.serialize());
  }

  public static String getEncodedIdentityKey(RecipientId id) throws TI_MissingIdentityException {
    return encodeIdentityKey(getIdentityKey(id));
  }

  @SuppressLint("Range") @WorkerThread
  public static String buildMessageBody(@NonNull RecipientId introductionRecipientId, @NonNull Set<RecipientId> introducees) throws JSONException {
    assert introducees.size() > 0: TAG + " buildMessageBody called with no Recipient Ids!";

    JSONObject data = new JSONObject();

    // TODO: LiveRecipient?
    RecipientTable rdb = SignalDatabase.recipients();
    Cursor recipientCursor = rdb.getCursorForSendingTI(introducees);
    JSONArray introduceeData = new JSONArray();

    data.put(TI_VERSION_J, TI_MESSAGE_VERSION);
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
      String formatedSafetyNr;
      try{
        IdentityKey introduceeIdentityKey = getIdentityKey(introduceesList.get(i));
        introducee.put(IDENTITY_J, encodeIdentityKey(introduceeIdentityKey));
        formatedSafetyNr = predictFingerprint(introductionRecipientId, introduceesList.get(i), introduceeServiceId, introduceeE164, introduceeIdentityKey);
      } catch (TI_MissingIdentityException e){
        e.printStackTrace();
        throw new AssertionError(TAG + " Unexpected missing identities when building TI message body!");
      }
      introducee.put(PREDICTED_FINGERPRINT_J, formatedSafetyNr);
      introduceeData.put(introducee);
      recipientCursor.moveToNext();
    }

    recipientCursor.close();
    data.put(INTRODUCEE_DATA_J, introduceeData);

    return TI_IDENTIFYER + TI_SEPARATOR + data.toString(INDENT_SPACES);
  }

  // This structure allows for a oneliner in the processing logic to minimize additional code needed in there.
  public static void handleTIMessage(RecipientId introducer, String message, long timestamp){
    if(!message.contains(TI_IDENTIFYER)) return;
    // Schedule Reception Job
    ApplicationDependencies.getJobManager().add(new TrustedIntroductionsReceiveJob(introducer, message, timestamp));
  }

  /**
   *
   * @param id recipient Id for which the cache should be queried.
   * @return ACI as string if present, null otherwise
   */
  public static String getServiceIdFromRecipientId(RecipientId id){
    if (Recipient.live(id).resolve().getServiceId().isPresent()){
      return Recipient.live(id).resolve().getServiceId().get().toString();
    } else {
      return null;
    }
  }

  /**
   * Parses an incoming TI message to create introduction data
   * @param body of the incoming message
   * @param timestamp when message was received
   * @param introducerId whom the message came from
   * @return populated List<TI_Data> if successfull, null otherwise
   */
  @WorkerThread
  @SuppressLint("Range") // keywords exists
  public static @Nullable List<TI_Data> parseTIMessage(String body, long timestamp, RecipientId introducerId){
    if (!body.contains(TI_IDENTIFYER)){
      throw new AssertionError("Non TI message passed into parse TI!");
    }
    // TODO: Check version and ignore/convert depending on change
    String introducerServiceId = getServiceIdFromRecipientId(introducerId);
    ArrayList<TI_Data> result = new ArrayList<>();
    String jsonDataS = body.replace(TI_IDENTIFYER, "");
    try {
      JSONObject data = new JSONObject(jsonDataS);
      String version = data.getString(TI_VERSION_J);
      if (!version.equals(TI_MESSAGE_VERSION)){
        // TODO: For now we just ignore introductions with missmatched versions
        // would add any migration code here
        return null;
      }
      JSONArray introducees = data.getJSONArray(INTRODUCEE_DATA_J);
      ArrayList<IdKeyPair> idKeyPairs = new ArrayList<>();
      List<String> recipientServiceIds = new ArrayList<>();
      // Get all SerciveIds of introducees first to minimize database Queries
      for (int i = 0; i < introducees.length(); i++){
        JSONObject o = introducees.getJSONObject(i);
        String introduceeServiceId = o.getString(INTRODUCEE_SERVICE_ID_J);
        idKeyPairs.add(new IdKeyPair(introduceeServiceId, o.getString(IDENTITY_J)));
        recipientServiceIds.add(introduceeServiceId);
      }
      // Get any known recipients & add to result
      Cursor cursor = SignalDatabase.recipients().getCursorForReceivingTI(recipientServiceIds);
      ArrayList<String> knownIds = new ArrayList<>();
      if (cursor.getCount() > 0) {
        cursor.moveToFirst();
        while (!cursor.isAfterLast()){
          // Guaranteed to be the same as in introduction
          String introduceeServiceId = cursor.getString(cursor.getColumnIndex(SERVICE_ID));
          knownIds.add(introduceeServiceId);
          String name = cursor.getString(cursor.getColumnIndex(SORT_NAME));
          String phone = cursor.getString(cursor.getColumnIndex(PHONE));
          String identityKey = IdKeyPair.findCorrespondingKeyInList(introduceeServiceId, idKeyPairs);
          TI_Data d = new TI_Data(null, TrustedIntroductionsDatabase.State.PENDING, introducerServiceId, introduceeServiceId, name, phone, identityKey, null, timestamp);
          result.add(d);
          cursor.moveToNext();
        }
        cursor.close();
      }
      // Iterate through JSONData again, create the introductions for the still unknown recipients & set the predictedSecurityNumbers for all
      for(int i = 0; i < introducees.length(); i++){
        JSONObject o = introducees.getJSONObject(i);
        // If data was fetched from local database, simply add the Security number information
        String introduceeServiceId = o.getString(INTRODUCEE_SERVICE_ID_J);
        if (knownIds.contains(introduceeServiceId)){
          int j = 0;
          while(!result.get(j).getIntroduceeServiceId().equals(introduceeServiceId)) j++;
          if (j < knownIds.size() - 1){
            throw new AssertionError("Something went wrong fetching recipients in parseTIMessage (size of service IDs > JSONArray)");
          }
          result.get(j).setPredictedSecurityNumber(o.getString(PREDICTED_FINGERPRINT_J));
        } else {
          TI_Data d = new TI_Data(null, TrustedIntroductionsDatabase.State.PENDING, introducerServiceId, o.getString(INTRODUCEE_SERVICE_ID_J), o.getString(NAME_J), o.getString(NUMBER_J),  o.getString(IDENTITY_J), o.getString(PREDICTED_FINGERPRINT_J), timestamp);
          result.add(d);
        }
      }
    } catch(JSONException e){
      Log.e(TAG, String.format("A JSON exception occured while trying to parse the TI message: %s", jsonDataS));
      Log.e(TAG, e.toString());
      return null; // unsuccessful parse
    }
    return result;
  }

  /**
   * //TODO: What are generally sensible queue strings? Does it matter if there are multiple vs. one queue for our purposes (e.g., multiple incoming)?
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
      throw new AssertionError("No such Algorithm!");
    } catch (IOException ioe){
      Log.e(TAG, ioe.toString());
      Log.e(TAG, ioe.getMessage());
      throw new AssertionError("IO exception!");
    }
    return hashtext;
  }

  private static class IdKeyPair{
    public String id;
    public String key;

    public IdKeyPair(String id, String key){
      this.id = id;
      this.key = key;
    }

    public static String findCorrespondingKeyInList(String id, ArrayList<IdKeyPair> list){
      for (IdKeyPair p: list) {
        if(id.equals(p.id)){
          return p.key;
        }
      }
      throw new AssertionError(TAG + " The Id you were searching for was not found in the list!");
    }
  }

  /**
   * Spawns it's own thread.
   * Used both by verifyDisplayFragment and Introduction database.
   * TODO: Should this be a job for persistence?
   *
   * @param status The new verification status
   */
  public static void updateContactsVerifiedStatus(RecipientId recipientId, IdentityKey identityKey, IdentityTable.VerifiedStatus status) {
    Log.i(TAG, "Saving identity: " + recipientId);
    SignalExecutors.BOUNDED.execute(() -> {
      try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
        final boolean verified = IdentityTable.VerifiedStatus.isVerified(status);
        if (verified) {
          ApplicationDependencies.getProtocolStore().aci().identities()
                                 .saveIdentityWithoutSideEffects(recipientId,
                                                                 identityKey,
                                                                 status,
                                                                 false,
                                                                 System.currentTimeMillis(),
                                                                 true);
        } else {
          ApplicationDependencies.getProtocolStore().aci().identities().setVerified(recipientId, identityKey, status);
        }

        // For other devices but the Android phone, we map the finer statusses to verified or unverified.
        // TODO: Change once we add new devices for TI
        ApplicationDependencies.getJobManager()
                               .add(new MultiDeviceVerifiedUpdateJob(recipientId,
                                                                     identityKey,
                                                                     status));
        StorageSyncHelper.scheduleSyncForDataChange();
        Recipient recipient = Recipient.live(recipientId).resolve();
        IdentityUtil.markIdentityVerified(getApplicationContext(), recipient, verified, false);
      }
    });
  }

  /**
   * //TODO: May need some cashing in TA_Data here
   * Query db and check if recipient is present. If not return Unknown.
   * PRE: serviceId represents valid ACI
   */
  @WorkerThread
  public static RecipientId getRecipientIdOrUnknown(String serviceId){
    ServiceId sId = ServiceId.parseOrThrow(serviceId);
    RecipientTable recipients = SignalDatabase.recipients();
    Optional<RecipientId> rId = recipients.getByServiceId(sId);
    return rId.orElse(RecipientId.UNKNOWN);
  }
}
