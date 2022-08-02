package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MinimalViewModel extends ViewModel {
  private final MinimalManager                      manager;
  private final ArrayList<SelectedTIContacts.Model> selectedContacts;
  private final MutableLiveData<List<Recipient>>    introducableContacts;
  private final MutableLiveData<String>             filter;

  MinimalViewModel(MinimalManager manager) {
    this.manager = manager;
    introducableContacts = new MutableLiveData<>();
    selectedContacts = new ArrayList<>();
    filter = new MutableLiveData<>("");
    loadValidContacts();
  }

  boolean addSelectedContact(@NonNull Recipient contact){
    boolean                          added    = selectedContacts.add(new SelectedTIContacts.Model(contact, contact.getId()));
    return added;
  }

  int removeSelectedContact(@NonNull Recipient contact){
    SelectedTIContacts.Model c       = new SelectedTIContacts.Model(contact, contact.getId());
    boolean                  removed = selectedContacts.remove(c);
    return removed ? 1:0;
  }

  boolean isSelectedContact(@NonNull Recipient contact){
    SelectedTIContacts.Model c = new SelectedTIContacts.Model(contact, contact.getId());
    return Objects.requireNonNull(selectedContacts).contains(c);
  }

  int getSelectedContactsCount(){
    return Objects.requireNonNull(selectedContacts).size();
  }


  List<SelectedTIContacts.Model> listSelectedContacts(){
     return selectedContacts;
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

  public LiveData<List<Recipient>> getContacts(){
    return introducableContacts;
  }

  // Leaving the dialogue out for now

  static class Factory implements ViewModelProvider.Factory {

    private final MinimalManager manager;

    Factory(RecipientId id) {
      this.manager = new MinimalManager(id, SignalDatabase.identities(), SignalDatabase.recipients());
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new MinimalViewModel(manager)));
    }
  }

}
