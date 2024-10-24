/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.ArrayList;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.TI_LOG_TAG;
import static org.webrtc.ApplicationContextProvider.getApplicationContext;

public interface MessageRequestRepositoryGlue {

  String TAG = String.format(TI_LOG_TAG, Log.tag(MessageRequestRepositoryGlue.class));

  String logmsg = "New Recipient: %s had an %s introduction and was thus marked as %s";


  /**
   *
   * When a new conversation is initiated by someone else, check the introduction database for existing introductions.
   * Adjust verification state if an accepted introduction is present.
   * @param recipient the new conversation recipient
   */
  static void adjustVerificationStatus(Recipient recipient){
    ServiceId                    serviceId         = recipient.requireServiceId();
    if(SignalDatabase.tiDatabase().atLeastOneIntroductionIsUnknown(serviceId.toString())){
      IdentityTableGlue.VerifiedStatus previousVerificationState = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipient.getId());
      SignalDatabase.tiIdentityDatabase().modifyIntroduceeVerification(serviceId,
                                                                       previousVerificationState,
                                                                       TI_Database.State.ACCEPTED,
                                                                       String.format(logmsg,
                                                                                     recipient.getDisplayName(getApplicationContext()),
                                                                                     ??,
                                                                                     IdentityTableGlue.VerifiedStatus.INTRODUCED));
    } else if(SignalDatabase.tiDatabase().atLeastOneIntroductionIs(TI_Database.State.REJECTED, serviceId)){
      IdentityTableGlue.VerifiedStatus previousVerificationState = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipient.getId());
      SignalDatabase.tiIdentityDatabase().modifyIntroduceeVerification(serviceId,
                                                                       previousVerificationState,
                                                                       TI_Database.State.REJECTED);
    }
  }

}
