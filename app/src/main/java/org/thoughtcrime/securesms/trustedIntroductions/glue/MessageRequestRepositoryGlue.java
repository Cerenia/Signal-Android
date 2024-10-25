/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.TI_LOG_TAG;
import static org.webrtc.ApplicationContextProvider.getApplicationContext;

public interface MessageRequestRepositoryGlue {

  String TAG = String.format(TI_LOG_TAG, Log.tag(MessageRequestRepositoryGlue.class));

  String logmsg = "New Recipient: %s had a preexisting 'unknown' introduction.";


  /**
   * // TODO: Do I need some kind of callback to modify the banner as the results come in?
   *
   * When a new conversation is initiated by us, we need to check the introduction database for existing introductions for that service ID.
   * For each introduction that was present, we must shift them from the 'unknown' state to their 'known' counterpart (FSM transitions in TI_Database) &
   * check if they must be turned stale, by comparing the current Identity Key (hits the network) with what we have in the introductions.
   * Iff an accepted Introduction is present at the end of this process, we *may* need to adjust the verification state (call FSM logic in TI_Identity_Database).
   * @param recipient the new conversation recipient
   */
  @WorkerThread
  static void handleNewUnknownRecipient(Recipient recipient){
    ServiceId                    serviceId         = recipient.requireServiceId();
    if(SignalDatabase.tiDatabase().atLeastOneIntroductionIsUnknown(serviceId.toString())){
      // Fetch the identity key of the new recipient
      List<PreKeyBundle> bundles;
      try {
        bundles = AppDependencies.getSignalServiceMessageSender().getPreKeys(new SignalServiceAddress(serviceId), null, SignalServiceAddress.DEFAULT_DEVICE_ID, false);
        TI_Database.State s = SignalDatabase.tiDatabase().handleUnknownIntroductions(serviceId.toString(), TI_Utils.encodeIdentityKey(bundles.get(0).getIdentityKey()));
        // at least one 'unknown' introduction for this service ID existed and this is the highest priority state (as defined in TI_DB handleUnknownIntroductions)
        if (s != null){
          // Now check if the verified status needs to be updated.
          IdentityTableGlue.VerifiedStatus previousVerificationState = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipient.getId());
          SignalDatabase.tiIdentityDatabase().modifyIntroduceeVerification(serviceId.toString(),
                                                                           previousVerificationState,
                                                                           s,
                                                                           String.format(logmsg,
                                                                                         recipient.getDisplayName(getApplicationContext()))
                                                                           );
        }
      } catch (IOException e){
        Log.e(TAG, "Could not fetch keys for recipient %s though there wer preexisting introductions...");
        e.printStackTrace();
        // TODO: This should probably schedule a job that retries this in the background as we may have consistency issues further down the line if it does not happen here.
      }
    }
  }
}
