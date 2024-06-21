package org.thoughtcrime.securesms.trustedIntroductions.glue;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

public interface DataMessageProcessorGlue {
  static void handleTIMessage(RecipientId introducer, String message, long timestamp){
    TI_Utils.handleTIMessage(introducer, message, timestamp);
  }
}
