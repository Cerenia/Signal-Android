package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersViewModel;

import java.util.Objects;


public class TrustedIntroductionContactsViewModel extends ViewModel {

  private final TrustedIntroductionContactManager manager;

  private TrustedIntroductionContactsViewModel() {
    this.manager = new TrustedIntroductionContactManager();
  }


  public static class Factory implements ViewModelProvider.Factory {

    public Factory(@NonNull GroupId groupId) {
      
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new TrustedIntroductionContactsViewModel()));
    }
  }

}
