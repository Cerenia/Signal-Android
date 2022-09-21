package org.thoughtcrime.securesms.trustedIntroductions.receive;

import androidx.annotation.NonNull;
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
  // TODO: Do I need a container which holds a diff, such that I can apply everything at once when leaving the activity?
  // iff there are performance issues.

  ManageViewModel(ManageManager manager){
    this.manager = manager;
    filter = new MutableLiveData<>("");
    introductions = new MutableLiveData<>();
    loadIntroductions();
  }

  private void loadIntroductions(){
    manager.getIntroductions(introductions::postValue);
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
    while(curr.getIntroducerId().toLong() != introductionId && i < all.size()){
      curr = all.get(i++);
    }
    if(curr.getIntroducerId().toLong() != introductionId){
      throw new AssertionError(TAG +": the introduction id was not present in the viewModels List");
    }
    // data class TI_Data (val id: Long?, val state: TrustedIntroductionsDatabase.State?, val introducerId: RecipientId?, val introduceeId: RecipientId?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long) : Serializable
    curr = new TI_Data(curr.getId(), curr.getState(), RecipientId.UNKNOWN, curr.getIntroduceeId(), curr.getIntroduceeServiceId(), curr.getIntroduceeName(), curr.getIntroduceeNumber(), curr.getIntroduceeIdentityKey(), curr.getPredictedSecurityNumber(), curr.getTimestamp());
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

    Factory(RecipientId id) {
      this.manager = new ManageManager(id, SignalDatabase.trustedIntroductions());
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ManageViewModel(manager)));
    }
  }

}
