package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;
import java.util.Objects;

public class TI_ManageViewModel extends ViewModel {
  private final TI_ManageManager        manager;
  private final MutableLiveData<String> filter;
  private final MutableLiveData<List<TI_Data>> introductions;
  // TODO: Do I need a container which holds a diff, such that I can apply everything at once when leaving the activity?
  // iff there are performance issues.

  TI_ManageViewModel(TI_ManageManager manager){
    this.manager = manager;
    filter = new MutableLiveData<>("");
    introductions = new MutableLiveData<>();
    loadIntroductions();
  }

  private void loadIntroductions(){
    manager.getIntroductions(introductions::postValue);
  }

  public void setQueryFilter(String filter) {
    this.filter.setValue(filter);
  }

  public LiveData<List<TI_Data>> getIntroductions() {
    return introductions;
  }

  //TODO:
  // accept, reject etc...

  static class Factory implements ViewModelProvider.Factory {

    private final TI_ManageManager manager;

    Factory(RecipientId id) {
      this.manager = new TI_ManageManager(id, SignalDatabase.trustedIntroductions());
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new TI_ManageViewModel(manager)));
    }
  }

}
