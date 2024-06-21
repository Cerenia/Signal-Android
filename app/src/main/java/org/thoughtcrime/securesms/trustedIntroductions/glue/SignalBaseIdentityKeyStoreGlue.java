package org.thoughtcrime.securesms.trustedIntroductions.glue;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

public interface SignalBaseIdentityKeyStoreGlue {

  static void turnAllIntroductionsStale(RecipientId recipientId, String TAG){
    // Security nr. changed, change all introductions for this introducee to stale
    SignalExecutors.BOUNDED.execute(() -> {
      Recipient recipient = Recipient.resolved(recipientId);
      boolean   res       = SignalDatabase.tiDatabase().turnAllIntroductionsStale(recipient.requireServiceId().toString());
      if(!res){
        Log.e(TAG, "Error occured while turning all introductions stale for recipient: " + recipientId);
      }
    });
  }
}
