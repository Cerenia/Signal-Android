package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;

import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;

import io.reactivex.rxjava3.annotations.NonNull;

final class TrustedIntroductionContactManager {

  private final Context context;
  // This is the person which will receive the security numbers of the selcted contacts through
  //  a secure introduction.
  private final RecipientId recipientId;


  TrustedIntroductionContactManager(RecipientId recipientId){
    this.context = ApplicationDependencies.getApplication();
    this.recipientId = recipientId;
  }

  @WorkerThread RecipientId getOrCreateRecipientIdForForwardedContact(@NonNull SelectedContact selectedContact){
    return selectedContact.getOrCreateRecipientId(context);
  }


}
