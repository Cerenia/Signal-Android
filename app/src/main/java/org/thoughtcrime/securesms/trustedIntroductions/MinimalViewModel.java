package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MinimalViewModel extends ViewModel {
  private final MinimalManager                                    manager;
  private final ArrayList<SelectedStrings.Model> selectedContacts;
  private final MutableLiveData<List<String>>                     introducableContacts;
  private final MutableLiveData<String> filter;

  MinimalViewModel(MinimalManager manager) {
    this.manager = manager;
    introducableContacts = new MutableLiveData<>();
    selectedContacts = new ArrayList<SelectedStrings.Model>();
    filter = new MutableLiveData<>("");
    loadValidContacts();
  }

  boolean addSelectedContact(@NonNull String contact){
    boolean                          added    = selectedContacts.add(new SelectedStrings.Model(new MinimalStringItem(contact), contact));
    return added;
  }

  int removeSelectedContact(@NonNull String contact){
    SelectedStrings.Model c = new SelectedStrings.Model(new MinimalStringItem(contact), contact);
    boolean                          removed  = selectedContacts.remove(c);
    return removed ? 1:0;
  }

  boolean isSelectedContact(@NonNull String contact){
    SelectedStrings.Model c = new SelectedStrings.Model(new MinimalStringItem(contact), contact);
    return Objects.requireNonNull(selectedContacts).contains(c);
  }

  int getSelectedContactsCount(){
    return Objects.requireNonNull(selectedContacts).size();
  }


  List<String> listSelectedContacts(){
    ArrayList<String>                l        = new ArrayList<>();
    for(SelectedStrings.Model m: selectedContacts){
      l.add(m.getStr());
    }
    return l;
  }

  public void setQueryFilter(String filter) {
    this.filter.setValue(filter);
  }

  LiveData<String> getFilter(){
    return this.filter;
  }

  private void loadValidContacts() {
    manager.getValidContacts(introducableContacts::postValue);
  }

  public LiveData<List<String>> getContacts(){
    return introducableContacts;
  }

  // Leaving the dialogue out for now

  static class Factory implements ViewModelProvider.Factory {

    private final MinimalManager manager;

    Factory(RecipientId id) {
      this.manager = new MinimalManager(id);
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new MinimalViewModel(manager)));
    }
  }

}
