package org.thoughtcrime.securesms.trustedIntroductions.glue;

import org.thoughtcrime.securesms.database.MessageType;
import org.thoughtcrime.securesms.mms.IncomingMessage;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

public interface MessageTableGlue {

  static String incompleteTIMessage = "";

  static void handleTIMessage(IncomingMessage retreived){
    // Group introductions are not supported.
    if(!(retreived.getType() == MessageType.NORMAL) || retreived.isGroupMessage()) return;
    String message = retreived.getBody();
    if (message == null) return;
    RecipientId introducer = retreived.getFrom();
    long timestamp = retreived.getReceivedTimeMillis();
    TI_Utils.handleTIMessage(introducer, message, timestamp);
  }
}
