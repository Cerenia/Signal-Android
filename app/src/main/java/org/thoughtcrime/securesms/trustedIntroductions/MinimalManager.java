package org.thoughtcrime.securesms.trustedIntroductions;

import org.thoughtcrime.securesms.recipients.RecipientId;

public class MinimalManager {

  private final RecipientId recipientId;

  MinimalManager(RecipientId recipientId){
    this.recipientId = recipientId;
  }

  RecipientId getRecipientId(){
    return this.recipientId;
  }
}
