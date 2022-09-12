package org.thoughtcrime.securesms.trustedIntroductions;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Opens an Activity for Managing Trusted Introductions.
 * Will either open just the Introductions made by a specific contact (//TODO: add tab/button for all?)
 * or all Introductions depending on how you navigated to that screen.
 */
public class TI_ManageActivity extends PassphraseRequiredActivity {

  private static final String TAG = Log.tag(TI_ManageActivity.class);

  // String
  public static final String INTRODUCER_ID                 = "recipient_id";
  public static final String SELECTED_CONTACTS_TO_FORWARD = "forwarding_contacts";
  // Instead of passing the RecipientID of the introducer as a string.
  public static final String ALL_INTRODUCTIONS = "ALL";

  // TODO: may want an action bar for button
  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

}
