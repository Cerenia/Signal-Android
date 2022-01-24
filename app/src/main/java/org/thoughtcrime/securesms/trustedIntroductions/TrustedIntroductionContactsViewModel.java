package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersViewModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.List;
import java.util.Objects;


public class TrustedIntroductionContactsViewModel extends ViewModel {

  private final TrustedIntroductionContactManager manager;

  private TrustedIntroductionContactsViewModel(){
    this.manager = new TrustedIntroductionContactManager();
  }

  private TrustedIntroductionContactsViewModel(RecipientId recipientId) {
    this.manager = new TrustedIntroductionContactManager(recipientId);
  }

  void getDialogStateForSelectedContacts(@NonNull List<SelectedContact> selectedContacts,
                                         @NonNull Consumer<TrustedIntroductionContactsViewModel.IntroduceDialogMessageState> callback){
    SimpleTask.run(
        () -> {
          // TODO: Is this needed?
          // Slightly confused about the term recipients here. Is it the selected contacts or the ppl already in the group?
          TrustedIntroductionContactsViewModel.IntroduceDialogMessageStatePartial partialState = selectedContacts.size() == 1 ? getDialogStateForSingleContact(selectedContacts.get(0))
                                                                                                             : getDialogStateForMultipleContacts(selectedContacts.size());

          return new TrustedIntroductionContactsViewModel.IntroduceDialogMessageState(partialState.recipientId == null ? Recipient.UNKNOWN : Recipient.resolved(partialState.recipientId),
                                                                     partialState.forwardCount);
        },
        callback::accept
    );
  }

  @WorkerThread
  private TrustedIntroductionContactsViewModel.IntroduceDialogMessageStatePartial getDialogStateForSingleContact(@NonNull SelectedContact selectedContact) {
    return new TrustedIntroductionContactsViewModel.IntroduceDialogMessageStatePartial(manager.getRecipientId(), manager.getOrCreateRecipientIdForForwardedContact(selectedContact));
  }

  private TrustedIntroductionContactsViewModel.IntroduceDialogMessageStatePartial getDialogStateForMultipleContacts(int recipientCount) {
    return new TrustedIntroductionContactsViewModel.IntroduceDialogMessageStatePartial(manager.getRecipientId(), recipientCount);
  }

  private static final class IntroduceDialogMessageStatePartial {
    // In this case, the Recipient is the person that will receive the security numbers of the selected contacts through secure introduction.
    private final RecipientId recipientId;
    // This is the ID of a single forwarded contact. Null if more than one contact is forwarded.
    private final RecipientId   contactId;
    private final int         forwardCount;

    private IntroduceDialogMessageStatePartial(@NonNull RecipientId recipientId, RecipientId contactId) {
      this.recipientId = recipientId;
      this.contactId = contactId;
      this.forwardCount = 1;
    }

    private IntroduceDialogMessageStatePartial(@NonNull RecipientId recipientId, int forwardCount) {
      Preconditions.checkArgument(forwardCount > 1);
      this.forwardCount = forwardCount;
      this.recipientId = recipientId;
      this.contactId = null;
    }
  }

  public static final class IntroduceDialogMessageState {
    private final Recipient recipient;
    private final int       selectionCount;

    private IntroduceDialogMessageState(@NonNull Recipient recipient, int selectionCount) {
      this.recipient      = recipient;
      this.selectionCount = selectionCount;
    }

    public Recipient getRecipient() {
      return recipient;
    }

    public int getSelectionCount() {
      return selectionCount;
    }

  }

  public static class Factory implements ViewModelProvider.Factory {
    // TODO: Needed?
    public Factory() {

    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new TrustedIntroductionContactsViewModel()));
    }
  }

}
