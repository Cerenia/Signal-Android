package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.SelectedContactSet;
import org.thoughtcrime.securesms.contacts.SelectedContacts;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class MinimalViewModel extends ViewModel {
  private final MinimalManager                             manager;
  private final MutableLiveData<ArrayList<SelectedStrings.Model>> selectedContacts;
  private final MutableLiveData<List<String>>  introducableContacts;
  private final MutableLiveData<String> filter;

  MinimalViewModel(MinimalManager manager) {
    this.manager = manager;
    introducableContacts = new MutableLiveData<>();
    selectedContacts = new MutableLiveData<>(new ArrayList<>());
    filter = new MutableLiveData<>("");
    loadValidContacts();
  }

  boolean addSelectedContact(@NonNull String contact){
    ArrayList<SelectedStrings.Model> selected = Objects.requireNonNull(selectedContacts.getValue());
    boolean            added    = selected.add(new SelectedStrings.Model(new MinimalStringModel(contact), contact));
    if (added){
      // Notify observers
      selectedContacts.setValue(selected);
    }
    return added;
  }

  int removeFromSelectedContacts(@NonNull String contact){
    ArrayList<SelectedStrings.Model> selected = Objects.requireNonNull(selectedContacts.getValue());
    boolean removed = selected.remove(contact);
    if (removed){
      // Notify observers
      selectedContacts.setValue(selected);
    }
    return removed ? 1:0;
  }

  boolean isSelectedContact(@NonNull String contact){
    return Objects.requireNonNull(selectedContacts.getValue()).contains(contact);
  }

  int getSelectedContactsCount(){
    return Objects.requireNonNull(selectedContacts.getValue()).size();
  }

  // observable
  LiveData<ArrayList<SelectedStrings.Model>> getSelectedContacts(){
    return selectedContacts;
  }

  List<String> listSelectedContacts(){
    ArrayList<SelectedStrings.Model> selected = Objects.requireNonNull(selectedContacts.getValue());
    ArrayList<String> l = new ArrayList<>();
    for(SelectedStrings.Model m: selected){
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

    // Passing databases explicitly for testibility
    Factory(RecipientId id) {
      this.manager = new MinimalManager(id);
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new MinimalViewModel(manager)));
    }
  }

}
