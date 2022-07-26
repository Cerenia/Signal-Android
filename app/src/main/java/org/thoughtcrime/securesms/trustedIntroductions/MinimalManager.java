package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MinimalManager {

  private final RecipientId recipientId;

  MinimalManager(RecipientId recipientId){
    this.recipientId = recipientId;
  }

  void getValidContacts(@NonNull Consumer<List<String>> introducableContacts){
    SignalExecutors.BOUNDED.execute(() -> {
      ArrayList<String> list = new ArrayList<>();
      for(int i = 1; i < 11; i++){
        list.add("Recipient: " + i);
      }
      introducableContacts.accept(list);
    });
  }

  RecipientId getRecipientId(){
    return this.recipientId;
  }
}
