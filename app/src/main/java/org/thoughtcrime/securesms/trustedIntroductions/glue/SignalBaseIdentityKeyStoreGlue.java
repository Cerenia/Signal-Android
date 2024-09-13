package org.thoughtcrime.securesms.trustedIntroductions.glue;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

public interface SignalBaseIdentityKeyStoreGlue {

  String TAG                       = String.format(TI_Utils.TI_LOG_TAG, org.signal.core.util.logging.Log.tag(SignalBaseIdentityKeyStoreGlue.class));

  static void turnAllIntroductionsStale(RecipientId recipientId) {
    // Security nr. changed, change all introductions for this introducee to stale
    SignalExecutors.BOUNDED.execute(() -> {
      Recipient recipient = Recipient.resolved(recipientId);
      boolean   res       = SignalDatabase.tiDatabase().turnAllIntroductionsStale(recipient.requireServiceId().toString());
      if (!res) {
        Log.e(TAG, "Error occured while turning all introductions stale for recipient: " + recipientId);
      }
    });
  }

  static void handleDanglingIntroductions(String serviceID, String encodedIdentityKey) {
    SignalExecutors.BOUNDED.execute(() -> {
      SignalDatabase.tiDatabase().handleDanglingIntroductions(serviceID, encodedIdentityKey);
    });
  }
}
