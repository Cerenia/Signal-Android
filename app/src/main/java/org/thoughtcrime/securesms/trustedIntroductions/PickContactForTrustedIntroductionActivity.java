package org.thoughtcrime.securesms.trustedIntroductions;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;

/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal database,
 * and let's the user choose one for the purpose of carrying out a trusted introduction.
 */
public class PickContactForTrustedIntroductionActivity extends ContactSelectionActivity
    implements ContactSelectionListFragment.OnContactSelectedListener{


  @Override public void onSelectionChanged() {

  }
}
