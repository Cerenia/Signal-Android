package org.thoughtcrime.securesms.trustedIntroductions.receive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ManageViewModel extends ViewModel {

  private final String TAG = Log.tag(ManageViewModel.class);

  private final ManageManager           manager;
  private final MutableLiveData<String> filter;
  private final MutableLiveData<List<TI_Data>> introductions;
  private final ManageActivity.IntroductionScreenType type;
  private final String introducerName;

  ManageViewModel(ManageManager manager, ManageActivity.IntroductionScreenType t, @Nullable String iN){
    this.manager = manager;
    filter = new MutableLiveData<>("");
    introductions = new MutableLiveData<>();
    type = t;
    introducerName = iN;
  }

  public void loadIntroductions(){
    manager.getIntroductions(introductions::postValue);
  }

  public @Nullable String getIntroducerName(){
    return introducerName;
  }

  public @NonNull ManageActivity.IntroductionScreenType getScreenType(){
    return type;
  }

  void deleteIntroduction(@NonNull Long introductionId){
    List <TI_Data>     oldIntros = introductions.getValue();
    ArrayList<TI_Data> newIntros = new ArrayList<>();
    for (TI_Data i : oldIntros){
      if (!i.getIntroducerId().equals(RecipientId.from(introductionId))){
        newIntros.add(i);
      }
    }
    introductions.postValue(newIntros);
    SignalExecutors.BOUNDED.execute(() -> {
      boolean res = SignalDatabase.trustedIntroductions().deleteIntroduction(introductionId);
      if(!res){
        Log.e(TAG, String.format("Deleting Introduction with id %d failed. Was it already deleted?", introductionId));
      }
    });
  }

  void forgetIntroducer(@NonNull Long introductionId){
    List<TI_Data> all = introductions.getValue();
    TI_Data curr = all.get(0);
    int i = 1;
    while(curr.getId() != introductionId && i < all.size()){
      curr = all.get(i++);
    }
    i--;
    if(curr.getId() != introductionId){
      throw new AssertionError(TAG +": the introduction id was not present in the viewModels List");
    }
    curr = new TI_Data(curr.getId(), curr.getState(), RecipientId.UNKNOWN, curr.getIntroduceeId(), curr.getIntroduceeServiceId(), curr.getIntroduceeName(), curr.getIntroduceeNumber(), curr.getIntroduceeIdentityKey(), curr.getPredictedSecurityNumber(), curr.getTimestamp());
    all.remove(i);
    if(type.equals(ManageActivity.IntroductionScreenType.ALL)){
      all.add(curr);
    }
    introductions.postValue(all);
    final TI_Data finalCurr = curr;
    SignalExecutors.BOUNDED.execute(() -> {
      SignalDatabase.trustedIntroductions().clearIntroducer(finalCurr);
    });
  }

  public void setQueryFilter(String filter) {
    this.filter.setValue(filter);
  }

  public LiveData<String> getFilter(){
    return this.filter;
  }

  public LiveData<List<TI_Data>> getIntroductions() {
    return introductions;
  }

  //TODO:
  // accept, reject etc...

  static class Factory implements ViewModelProvider.Factory {

    private final ManageManager manager;
    private final ManageActivity.IntroductionScreenType t;
    private final String introducer;

    Factory(RecipientId id, ManageActivity.IntroductionScreenType t, @Nullable String introducerName) {
      this.t = t;
      this.manager = new ManageManager(id, SignalDatabase.trustedIntroductions());
      introducer = introducerName;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ManageViewModel(manager, t, introducer)));
    }
  }

}
