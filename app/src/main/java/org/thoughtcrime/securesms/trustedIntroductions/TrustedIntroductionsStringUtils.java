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
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
  // @see RecipientDatabase
  static final String SERVICE_ID = "uuid";
  static final String USERNAME = "username";
  static final String PROFILE_GIVEN_NAME = "signal_profile_name";
  static final String PROFILE_FAMILY_NAME = "profile_family_name";
  static final String PROFILE_JOINED_NAME = "profile_joined_name";
  static final String PHONE = "phone";
  // @see IdentityDatabase
  static final String IDENTITY_KEY = "identity_key";

  // Json keys
  static final String NAME_J = "name";
  static final String NUMBER_J = "number";
  static final String IDENTITY_J = "public_key";
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

  private static void advanceCursors(Cursor c1, Cursor c2){
    c1.moveToNext();
    c2.moveToNext();
  }

  private static boolean isOnlyWhitespace(String name){
    for(Character c: name.toCharArray()){
      if(!Character.isWhitespace(c))
        return false;
    }
    return true;
  }

  @SuppressLint("Range") @WorkerThread
  public static String buildMessageBody(@NonNull RecipientId introductionRecipient, @NonNull List<RecipientId> introducees) throws JSONException, IOException, InvalidKeyException {
    assert introducees.size() > 0: TAG + " buildMessageBody called with no Recipient Ids!";

    // TODO: Should I just use the LiveRecipient Stuff instead?  :/ caching etc..
    RecipientDatabase rdb = SignalDatabase.recipients();
    IdentityDatabase idb = SignalDatabase.identities();

    NumericFingerprintGenerator generator = new NumericFingerprintGenerator(ITERATIONS);

    // Add introduction recipient to list, such that we can use their public key to predict the security number with everybody else
    ArrayList<RecipientId> allQueryArgs = new ArrayList<>();
    allQueryArgs.add(introductionRecipient);
    allQueryArgs.addAll(introducees);

    Cursor recipientCursor = rdb.getCursorForTI(allQueryArgs);

    List<String> addresses = new ArrayList<>();
    recipientCursor.moveToFirst();
    while (!recipientCursor.isAfterLast()){
      addresses.add(recipientCursor.getString(recipientCursor.getColumnIndex(SERVICE_ID)));
      recipientCursor.moveToNext();
    }
    Cursor keyCursor = idb.getCursorForIdentityKeys(addresses);

    // If this triggers, there is a programming error further up the chain. The UI must not allow a TI for contacts that we
    // do not have the identity key for.
    assert recipientCursor.getCount() == keyCursor.getCount() : TAG + " Cursor length mismatch!";

    JSONArray data = new JSONArray();
    recipientCursor.moveToFirst();
    keyCursor.moveToFirst();

    // Pull out public key and ACI (ServiceID) of introduction recipient for fingerprint generation and handle the rest of the list seperately
    String introductionRecipientSerializedPublicKey = keyCursor.getString(keyCursor.getColumnIndex(IDENTITY_KEY));
    String introductionRecipientACI = recipientCursor.getString(recipientCursor.getColumnIndex(SERVICE_ID));
    String introcutionRecipientE164 = recipientCursor.getString(recipientCursor.getColumnIndex(PHONE));
    advanceCursors(recipientCursor, keyCursor);

    // Initialize version and introduction recipients id & key
    int version;
    byte[] introductionRecipientId;
    IdentityKey introductionRecipientPublicKey = new IdentityKey(Base64.decode(introductionRecipientSerializedPublicKey));
    // @see VerifyDisplayFragment for verification version differences... Not sure I am doing this correctly
    if (FeatureFlags.verifyV2()){
      version = 2;
      introductionRecipientId = introductionRecipientACI.getBytes();
    } else {
      version = 1;
      introductionRecipientId = introcutionRecipientE164.getBytes();
    }

    while(!recipientCursor.isAfterLast()){
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
      String introduceeSerializedPublicKey = keyCursor.getString(keyCursor.getColumnIndex(IDENTITY_KEY));
      introducee.put(IDENTITY_J, introduceeSerializedPublicKey);

      byte[] introduceeId;
      if (FeatureFlags.verifyV2()){
        introduceeId = introduceeACI.getBytes();
      } else {
        introduceeId = introduceeE164.getBytes();
      }
      // @see VerifyDisplayFragment::initializeFingerprint(), iterations there also hardcoded to 5200 for FingerprintGenerator
      // @see ServiceId.java to understand how they convert the ACI to ByteArray
      // @see IdentityKey.java
      Fingerprint fingerprint = generator.createFor(version,
                                                      introductionRecipientId,
                                                      introductionRecipientPublicKey,
                                                      introduceeId,
                                                      new IdentityKey(Base64.decode(introductionRecipientSerializedPublicKey)));
      introducee.put(PREDICTED_FINGERPRINT_J, "\n" + getFormattedSafetyNumbers(fingerprint, SEGMENTS));
      data.put(introducee);
      advanceCursors(recipientCursor, keyCursor);
    }

    return TI_IDENTIFYER + TI_SEPARATOR + data.toString(INDENT_SPACES);
  }

}
