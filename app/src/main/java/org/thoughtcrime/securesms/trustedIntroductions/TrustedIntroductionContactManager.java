package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;

final class TrustedIntroductionContactManager {

  // This is the person which will receive the security numbers of the selected contacts through
  //  a secure introduction.
  private final RecipientId recipientId;

  TrustedIntroductionContactManager(RecipientId recipientId){
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
            RecipientId id = RecipientId.from(current.hasAci() ? current.getAci().get():null, current.hasE164() ? current.getE164().get():null);
            if(!current.isSelf() && id.compareTo(recipientId)!=0){
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

  RecipientId getRecipientId(){
    return this.recipientId;
  }
}
