package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.lifecycle.ViewModel;


public class TrustedIntroductionContactsViewModel extends ViewModel {

  private final TrustedIntroductionContactManager manager;

  private TrustedIntroductionContactsViewModel() {
    this.manager = new TrustedIntroductionContactManager();
  }

}
