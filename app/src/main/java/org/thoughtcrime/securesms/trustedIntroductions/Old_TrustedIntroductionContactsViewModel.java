package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.SelectedContactSet;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.List;
import java.util.Objects;

final class Old_TrustedIntroductionContactsViewModel extends ViewModel{

  private final Old_TrustedIntroductionContactManager manager;
  private final MutableLiveData<List<Recipient>>      introducableContacts;
  private final      MutableLiveData<String> filter;
  private final MutableLiveData<SelectedContactSet>      selectedContacts;

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) Old_TrustedIntroductionContactsViewModel(Old_TrustedIntroductionContactManager manager) {
    this.manager = manager;
    introducableContacts = new MutableLiveData<>();
    filter = new MutableLiveData<>("");
    selectedContacts = new MutableLiveData<>(new SelectedContactSet());
    loadValidContacts();
  }

  boolean addSelectedContact(@NonNull SelectedContact contact){
    SelectedContactSet selected = Objects.requireNonNull(selectedContacts.getValue());
    boolean added = selected.add(contact);
    if (added){
      // Notify observers
      selectedContacts.setValue(selected);
    }
    return added;
  }

  int removeFromSelectedContacts(@NonNull SelectedContact contact){
    SelectedContactSet selected = Objects.requireNonNull(selectedContacts.getValue());
    int result = selected.remove(contact);
    if (result > 0){
      // Notify observers
      selectedContacts.setValue(selected);
    }
    return result;
  }

  boolean isSelectedContact(@NonNull SelectedContact contact){
    return Objects.requireNonNull(selectedContacts.getValue()).contains(contact);
  }

  int getSelectedContactsCount(){
    return Objects.requireNonNull(selectedContacts.getValue()).size();
  }

  // observable
  LiveData<SelectedContactSet> getSelectedContacts(){
    return selectedContacts;
  }

  // direct access to the list TODO: is this a good idea? should be aight, SelectedContactSet uses copy-constructor.
  List<SelectedContact> listSelectedContacts(){
    SelectedContactSet selected = Objects.requireNonNull(selectedContacts.getValue());
    return selected.getContacts();
  }

  public LiveData<List<Recipient>> getContacts(){
    return introducableContacts;
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

  void getDialogStateForSelectedContacts(@NonNull Consumer<Old_TrustedIntroductionContactsViewModel.IntroduceDialogMessageState> callback){
    SimpleTask.run(
        () -> {
          List<SelectedContact> selection = Objects.requireNonNull(selectedContacts.getValue()).getContacts();
          return new Old_TrustedIntroductionContactsViewModel.IntroduceDialogMessageState(Recipient.resolved(manager.getRecipientId()), selection);
        },
        callback::accept
    );
  }

  static final class IntroduceDialogMessageState {
    private final Recipient recipient;
    private final List<SelectedContact> toIntroduce;

    private IntroduceDialogMessageState(@NonNull Recipient recipient, List<SelectedContact> toIntroduce) {
      this.recipient      = recipient;
      this.toIntroduce = toIntroduce;
    }

    Recipient getRecipient() {
      return recipient;
    }

    List<SelectedContact> getToIntroduce(){
      return toIntroduce;
    }

  }

  static class Factory implements ViewModelProvider.Factory {

    private final Old_TrustedIntroductionContactManager manager;

    // Passing databases explicitly for testibility
    Factory(RecipientId id) {
      this.manager = new Old_TrustedIntroductionContactManager(id, SignalDatabase.identities(), SignalDatabase.recipients());
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new Old_TrustedIntroductionContactsViewModel(manager)));
    }
  }
}
