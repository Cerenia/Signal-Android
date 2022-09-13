package org.thoughtcrime.securesms.trustedIntroductions.receive;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionAdapter;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionListItem;

public class ManageListFragment {

  // TODO: Will probably need that for all screen
  private ProgressWheel showIntroductionsProgress;

  private class IntroductionClickListener implements ContactsSelectionAdapter.ItemClickListener {

    @Override public void onItemClick(ContactsSelectionListItem item) {
      // TODO:
    }
  }
}
