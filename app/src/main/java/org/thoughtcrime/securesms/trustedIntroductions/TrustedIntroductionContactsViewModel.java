package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.SelectedContactSet;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.webrtc.ContextUtils.getApplicationContext;


public class TrustedIntroductionContactsViewModel extends ViewModel{

  private final TrustedIntroductionContactManager manager;
  // TODO: Do I also need MutableLiveData? (looks like this is related to fetching contacts in background thread)
  // Yes I do, this is the common case atm.
  // https://developer.android.com/topic/libraries/architecture/viewmodel
  private final MutableLiveData<List<Recipient>>  introducableContacts;
  private final      MutableLiveData<String> filter;
  private final MutableLiveData<SelectedContactSet>      selectedContacts;

  private TrustedIntroductionContactsViewModel(TrustedIntroductionContactManager manager) {
    this.manager = manager;
    introducableContacts = new MutableLiveData<>(new ArrayList<>());
    filter = new MutableLiveData<>("");
    selectedContacts = new MutableLiveData<>(new SelectedContactSet());
    loadValidContacts();
  }

  boolean addSelectedContact(@NonNull SelectedContact contact){
    SelectedContactSet selected = Objects.requireNonNull(selectedContacts.getValue());
    boolean result = selected.add(contact);
    // Notify observers
    selectedContacts.setValue(selected);
    return result;
  }

  int removeFromSelectedContacts(@NonNull SelectedContact contact){
    SelectedContactSet selected = Objects.requireNonNull(selectedContacts.getValue());
    int result = selected.remove(contact);
    // Notify observers
    selectedContacts.setValue(selected);
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

  // direct access to the list TODO: is this a good idea?
  List<SelectedContact> listSelectedContacts(){
    SelectedContactSet selected = Objects.requireNonNull(selectedContacts.getValue());
    return selected.getContacts();
  }

  int getSelectedContactsSize(){
    return Objects.requireNonNull(selectedContacts.getValue()).size();
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

  void getDialogStateForSelectedContacts(@NonNull Consumer<TrustedIntroductionContactsViewModel.IntroduceDialogMessageState> callback){
    SimpleTask.run(
        () -> {
          List<SelectedContact> selection = Objects.requireNonNull(selectedContacts.getValue()).getContacts();
          return new TrustedIntroductionContactsViewModel.IntroduceDialogMessageState(Recipient.resolved(manager.getRecipientId()), selection);
        },
        callback::accept
    );
  }

  public static final class IntroduceDialogMessageState {
    private final Recipient recipient;
    private final List<SelectedContact> toIntroduce;

    private IntroduceDialogMessageState(@NonNull Recipient recipient, List<SelectedContact> toIntroduce) {
      this.recipient      = recipient;
      this.toIntroduce = toIntroduce;
    }

    public Recipient getRecipient() {
      return recipient;
    }

    public List<SelectedContact> getToIntroduce(){
      return toIntroduce;
    }

  }

  public static class Factory implements ViewModelProvider.Factory {

    private final TrustedIntroductionContactManager manager;

    public Factory(RecipientId id) {
      this.manager = new TrustedIntroductionContactManager(id);
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new TrustedIntroductionContactsViewModel(manager)));
    }
  }

}
