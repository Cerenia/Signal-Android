package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;
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
  // Instead of passing the RecipientID of the introducer as a string.
  public static final String ALL_INTRODUCTIONS = "ALL";

  // TODO: Define all the views
  private Toolbar toolbar;


  // TODO: may want an action bar for button
  private final DynamicTheme dynamicTheme = new DynamicTheme();

  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, TI_ContactsChooseActivity.class);
    intent.putExtra(INTRODUCER_ID, id.toString());
    return intent;
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
    toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24);
    toolbar.setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  // TODO: Grab all the views & initialize in on_create

}
