package org.thoughtcrime.securesms.trustedIntroductions;

import android.annotation.SuppressLint;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;

public class TrustedIntroductionsStringUtils {

  // Random String to mark a message as a trustedIntroduction, since I'm tunnelin through normal messages
  static final String TI_IDENTIFYER = "QOikEX9PPGIuXfiejT9nC2SsDB8d9AG0dUPQ9gERBQ8qHF30Xj";
  static final String TI_SEPARATOR = "\n\n"; // marks start of JsonArray

  // Constants to pull values out of the cursors
  // @see RecipientDatabase
  static final String SERVICE_ID = "uuid";
  static final String USERNAME = "username";
  static final String PROFILE_GIVEN_NAME = "signal_profile_name";
  static final String PROFILE_FAMILY_NAME = "profile_family_name";
  static final String PROFILE_JOINED_NAME = "profile_joined_name";
  static final String PHONE = "phone";
  // @see IdentityDatabase
  static final String ADDRESS = "address";
  static final String IDENTITY_KEY = "identity_key";

  // Json keys
  static final String NAME_J = "name";
  static final String NUMBER_J = "number";
  static final String IDENTITY_J = "public_key";
  static final String ID_J = "uuid";

  private static boolean isOnlyWhitespace(String name){
    for(Character c: name.toCharArray()){
      if(!Character.isWhitespace(c))
        return false;
    }
    return true;
  }

  @SuppressLint("Range") @WorkerThread
  public static String buildMessageBody(@NonNull List<RecipientId> recipientIds) throws JSONException{
    assert recipientIds.size() > 0: "buildMessageBody called with no Recipient Ids!";

    RecipientDatabase rdb = SignalDatabase.recipients();
    IdentityDatabase idb = SignalDatabase.identities();

    Cursor recipientCursor = rdb.getCursorForTI(recipientIds);

    List<String> addresses = new ArrayList<>();
    recipientCursor.moveToFirst();
    while (!recipientCursor.isAfterLast()){
      addresses.add(recipientCursor.getString(recipientCursor.getColumnIndex(ADDRESS)));
      recipientCursor.moveToNext();
    }
    Cursor keyCursor = idb.getCursorForIdentityKeys(addresses);

    assert recipientCursor.getCount() == keyCursor.getCount() : "Cursor length mismatch!";

    JSONArray data = new JSONArray();
    recipientCursor.moveToFirst();
    keyCursor.moveToFirst();

    while(!recipientCursor.isAfterLast()){
      JSONObject introducee = new JSONObject();
      try {
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
        introducee.put(NUMBER_J, recipientCursor.getString(recipientCursor.getColumnIndex(PHONE)));
        introducee.put(ID_J, recipientCursor.getString(recipientCursor.getColumnIndex(ADDRESS)));
        introducee.put(IDENTITY_J, keyCursor.getString(keyCursor.getColumnIndex(IDENTITY_KEY)));
        data.put(introducee);
      } catch (JSONException e){
        throw e;
      }
      recipientCursor.moveToNext();
      keyCursor.moveToNext();
    }

    return TI_IDENTIFYER + TI_SEPARATOR + data.toString();
  }

}
