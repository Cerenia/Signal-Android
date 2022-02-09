package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;

import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;

final class TrustedIntroductionContactManager {

  private final Context context;
  // This is the person which will receive the security numbers of the selected contacts through
  //  a secure introduction.
  private final RecipientId recipientId;


  TrustedIntroductionContactManager(RecipientId recipientId){
    this.context = ApplicationDependencies.getApplication();
    this.recipientId = recipientId;
  }

  void getValidContacts(@NonNull Consumer<List<Recipient>> introducableContacts){
    SignalExecutors.BOUNDED.execute(() -> {
      IdentityDatabase idb = SignalDatabase.identities();
      RecipientDatabase rdb = SignalDatabase.recipients();
      try(RecipientDatabase.RecipientReader reader = rdb.getReaderForTI(idb.getTIUnlocked())){
        int count = reader.getCount();
        if (count == 0){
          introducableContacts.accept(Collections.emptyList());
        } else {
          List<Recipient> contacts = new ArrayList<>();
          while(reader.getNext() != null){
            Recipient current = reader.getCurrent();
            if(!current.isSelf()){
              contacts.add(current);
            }
          }
          // sort ascending
          Collections.sort(contacts, Comparator.comparing((Recipient recipient) -> recipient.getProfileName().toString()));
          introducableContacts.accept(contacts);
        }
      }
    });
  }


  public RecipientId getRecipientId(){
    return this.recipientId;
  }

  @WorkerThread RecipientId getOrCreateRecipientIdForForwardedContact(@NonNull SelectedContact selectedContact){
    return selectedContact.getOrCreateRecipientId(context);
  }


}
