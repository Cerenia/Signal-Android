package org.thoughtcrime.securesms.trustedIntroductions.send;

import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;

public class ContactsSelectionManager {

  // This is the person which will receive the security numbers of the selected contacts through
  //  a secure introduction.
  private final RecipientId recipientId;

  // Dependency injection makes the class testable
  private final IdentityTable  idb;
  private final RecipientTable rdb;

  ContactsSelectionManager(@NonNull RecipientId recipientId, @NonNull IdentityTable idb, @NonNull RecipientTable rdb){
    this.recipientId = recipientId;
    this.idb = idb;
    this.rdb = rdb;
  }

  void getValidContacts(@NonNull Consumer<List<Recipient>> introducableContacts){
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientTable.RecipientReader reader = rdb.getReaderForValidTI_Candidates(idb.getCursorForTIUnlocked());
      int count = reader.getCount();
      if (count == 0){
        introducableContacts.accept(Collections.emptyList());
      } else {
        List<Recipient> contacts = new ArrayList<>();
        while(reader.getNext() != null){
          Recipient current = reader.getCurrent();
          RecipientId id = current.getId();
          if(!current.isSelf() && id.compareTo(recipientId)!=0){
            contacts.add(current);
          }
        }
        // sort ascending
        Collections.sort(contacts, Comparator.comparing((Recipient recipient) -> recipient.getProfileName().toString()));
        introducableContacts.accept(contacts);
      }
    });
  }

  RecipientId getRecipientId(){
    return this.recipientId;
  }
}
