package org.thoughtcrime.securesms.trustedIntroductions.send;

import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue;
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.annotations.NonNull;

public class ContactsSelectionManager {

  // This is the person which will receive the security numbers of the selected contacts through
  //  a secure introduction.
  private final RecipientId recipientId;

  // Dependency injection makes the class testable
  private final @NonNull IdentityTableGlue idb;

  ContactsSelectionManager(@NonNull RecipientId recipientId, @NonNull IdentityTableGlue idb){
    this.recipientId = recipientId;
    this.idb = idb;
  }

  void getValidContacts(@NonNull Consumer<List<Recipient>> introducableContacts){
    SignalExecutors.BOUNDED.execute(() -> {
      Map<RecipientId, RecipientRecord> elligibleCandidates = RecipientTableGlue.getValidTI_Candidates(idb.getCursorForTIUnlocked());
      int count = elligibleCandidates.size();
      if (count == 0){
        introducableContacts.accept(Collections.emptyList());
      } else {
        List<Recipient> contacts = new ArrayList<>();
        elligibleCandidates.forEach((recipientID, recipientRecord) -> {
          if (recipientID.compareTo(this.recipientId) != 0){
            contacts.add(Recipient.resolved(recipientID));
          }
        });
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
