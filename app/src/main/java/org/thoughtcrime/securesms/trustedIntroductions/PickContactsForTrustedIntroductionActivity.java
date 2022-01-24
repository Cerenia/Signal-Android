package org.thoughtcrime.securesms.trustedIntroductions;

import android.os.Bundle;
import android.view.View;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PushContactSelectionActivity;
import org.thoughtcrime.securesms.R;

import androidx.annotation.NonNull;


/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal database,
 * and let's the user choose one for the purpose of carrying out a trusted introduction.
 */
public class PickContactsForTrustedIntroductionActivity extends PushContactSelectionActivity {

  // when done picking contacts (button)
  private View done;
  private TrustedIntroductionContactsViewModel viewModel;

  // TODO: do I need the createIntent function?


  @Override protected void onCreate(Bundle icicle, boolean ready) {
    // TODO: needed?
    getIntent().putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.pick_ti_contacts_activity);
    super.onCreate(icicle, ready);

  }
}
