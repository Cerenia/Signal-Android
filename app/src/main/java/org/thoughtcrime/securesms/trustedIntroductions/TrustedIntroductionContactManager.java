package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;

import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;

import io.reactivex.rxjava3.annotations.NonNull;

final class TrustedIntroductionContactManager {

  private final Context context;
  // TODO: recipient ID for contact that will receive the TI?

  TrustedIntroductionContactManager(){
    this.context = ApplicationDependencies.getApplication();
  }

  @WorkerThread RecipientId getOrCreateRecipientIdForForwardedContact(@NonNull SelectedContact selectedContact){
    return selectedContact.getOrCreateRecipientId(context);
  }
  

}
