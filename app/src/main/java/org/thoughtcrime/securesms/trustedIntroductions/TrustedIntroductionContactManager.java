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
import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;

final class TrustedIntroductionContactManager {

  private final Context context;
  // This is the person which will receive the security numbers of the selected contacts through
  //  a secure introduction.
  private final RecipientId recipientId;

  /**
   * Code from ContactRepository which is needed to recreate the multiselect Fragment.
   */
  static final int NORMAL_TYPE       = 0;
  static final int PUSH_TYPE         = 1 << 0;
  static final int NEW_PHONE_TYPE    = 1 << 2;
  static final int NEW_USERNAME_TYPE = 1 << 3;
  static final int RECENT_TYPE       = 1 << 4;
  static final int DIVIDER_TYPE      = 1 << 5;

  public static final String ID_COLUMN           = "id";
  static final String NAME_COLUMN         = "name";
  static final String NUMBER_COLUMN       = "number";
  static final String NUMBER_TYPE_COLUMN  = "number_type";
  static final String LABEL_COLUMN        = "label";
  static final String CONTACT_TYPE_COLUMN = "contact_type";
  static final String ABOUT_COLUMN        = "about";


  TrustedIntroductionContactManager(RecipientId recipientId){
    this.context = ApplicationDependencies.getApplication();
    this.recipientId = recipientId;
  }

  void getValidContacts(@androidx.annotation.NonNull Consumer<List<Recipient>> introducableContacts){
    SignalExecutors.BOUNDED.execute(() -> {
      IdentityDatabase idb = SignalDatabase.identities();
      RecipientDatabase rdb = SignalDatabase.recipients();
      try(RecipientDatabase.RecipientReader reader = rdb.getReader(idb.getTIUnlocked())){
        int count = reader.getCount();
        if (count == 0){
          introducableContacts.accept(Collections.emptyList());
        } else {
          List<Recipient> contacts = new ArrayList<>();
          while(reader.getNext() != null){
            contacts.add(reader.getCurrent());
          }
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
