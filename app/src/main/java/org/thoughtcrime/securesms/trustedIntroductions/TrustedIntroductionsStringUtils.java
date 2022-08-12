package org.thoughtcrime.securesms.trustedIntroductions;

import android.annotation.SuppressLint;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class TrustedIntroductionsStringUtils {

  static final String TAG = Log.tag(TrustedIntroductionsStringUtils.class);

  // Random String to mark a message as a trustedIntroduction, since I'm tunneling through normal messages
  static final String TI_IDENTIFYER = "QOikEX9PPGIuXfiejT9nC2SsDB8d9AG0dUPQ9gERBQ8qHF30Xj";
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
  static final String SERVICE_ID = "uuid";
  static final String USERNAME = "username";
  static final String PROFILE_GIVEN_NAME = "signal_profile_name";
  static final String PROFILE_FAMILY_NAME = "profile_family_name";
  static final String PROFILE_JOINED_NAME = "profile_joined_name";
  static final String PHONE = "phone";

  // Json keys
  static final String NAME_J = "name";
  static final String NUMBER_J = "number";
  static final String IDENTITY_J = "identity_key_base64";
  static final String ID_J = "uuid";
  static final String PREDICTED_FINGERPRINT_J = "safety_number";

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

  // Needed because of 19 min API
  private static boolean isOnlyWhitespace(String name){
    for(Character c: name.toCharArray()){
      if(!Character.isWhitespace(c))
        return false;
    }
    return true;
  }

  private static IdentityKey getIdentityKey(RecipientId id){
    Optional<IdentityRecord> identityRecord = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(id);
    // If this doesn't work we have a programming error further up the stack, no introduction can be made if we don't have the identity.
    assert identityRecord.isPresent() : TAG + " No identity found for the introduction recipient!";
    return identityRecord.get().getIdentityKey();
  }

  @SuppressLint("Range") @WorkerThread
  public static String buildMessageBody(@NonNull RecipientId introductionRecipientId, @NonNull List<RecipientId> introducees) throws JSONException, IOException, InvalidKeyException {
    assert introducees.size() > 0: TAG + " buildMessageBody called with no Recipient Ids!";

    // TODO: Should I just use the LiveRecipient Stuff instead?  :/ caching etc..
    RecipientDatabase rdb = SignalDatabase.recipients();
    NumericFingerprintGenerator generator = new NumericFingerprintGenerator(ITERATIONS);
    Cursor recipientCursor = rdb.getCursorForTI(introducees);
    JSONArray data = new JSONArray();

    // Initialize version and introduction recipients id & key
    int version;
    byte[]        introductionRecipientFingerprintId;
    LiveRecipient live = Recipient.live(introductionRecipientId);
    Recipient introductionRecipientResolved = live.resolve();
    // @see VerifyDisplayFragment for verification version differences
    if (FeatureFlags.verifyV2()){
      version = 2;
      Log.e(TAG, introductionRecipientResolved.requireServiceId().toString());
      introductionRecipientFingerprintId = introductionRecipientResolved.requireServiceId().toByteArray();
    } else {
      version = 1;
      Log.e(TAG, introductionRecipientResolved.requireE164());
      introductionRecipientFingerprintId = introductionRecipientResolved.requireE164().getBytes();
    }
    IdentityKey introductionRecipientIdentityKey = getIdentityKey(introductionRecipientId);

    // Loop over all the contacts you want to introduce
    recipientCursor.moveToFirst();
    for(int i = 0; !recipientCursor.isAfterLast(); i++){
      JSONObject introducee = new JSONObject();
      // For the name, try joint name first, if empty, individual components, if still empty username as last attempt
      String name = "";
      name = recipientCursor.getString(recipientCursor.getColumnIndex(PROFILE_JOINED_NAME));
      if (name.isEmpty() || isOnlyWhitespace(name)){
        name = recipientCursor.getString(recipientCursor.getColumnIndex(PROFILE_GIVEN_NAME)) + recipientCursor.getString(recipientCursor.getColumnIndex(PROFILE_FAMILY_NAME));
      }
      if (name.isEmpty() || isOnlyWhitespace(name)){
        name = recipientCursor.getString(recipientCursor.getColumnIndex(USERNAME));
      }
      introducee.put(NAME_J, name);
      String introduceeE164 = recipientCursor.getString(recipientCursor.getColumnIndex(PHONE));
      introducee.put(NUMBER_J, introduceeE164);
      String introduceeACI = recipientCursor.getString(recipientCursor.getColumnIndex(SERVICE_ID));
      introducee.put(ID_J, introduceeACI);
      IdentityKey introduceeIdentityKey = getIdentityKey(introducees.get(i));
      introducee.put(IDENTITY_J, Base64.encodeBytes(introduceeIdentityKey.serialize()));
      byte[] introduceeFingerprintId;
      if (FeatureFlags.verifyV2()){
        introduceeFingerprintId = introduceeACI.getBytes();
      } else {
        introduceeFingerprintId = introduceeE164.getBytes();
      }
      // @see VerifyDisplayFragment::initializeFingerprint(), iterations there also hardcoded to 5200 for FingerprintGenerator
      // @see ServiceId.java to understand how they convert the ACI to ByteArray
      // @see IdentityKey.java
      Fingerprint fingerprint = generator.createFor(version,
                                                      introductionRecipientFingerprintId,
                                                      introductionRecipientIdentityKey,
                                                      introduceeFingerprintId,
                                                      introduceeIdentityKey);
      introducee.put(PREDICTED_FINGERPRINT_J, "\n" + getFormattedSafetyNumbers(fingerprint, SEGMENTS));
      data.put(introducee);
      recipientCursor.moveToNext();
    }

    return TI_IDENTIFYER + TI_SEPARATOR + data.toString(INDENT_SPACES);
  }

}
