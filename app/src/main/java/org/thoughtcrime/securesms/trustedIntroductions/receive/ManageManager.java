package org.thoughtcrime.securesms.trustedIntroductions.receive;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.jobs.TrustedIntroductionsReceiveJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.webrtc.ContextUtils.getApplicationContext;

public class ManageManager {

  private static final String TAG =  String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageManager.class));

  // Introducer ID, or unknown iff all.
  private final RecipientId recipientId;

  @NonNull private final String forgottenPlaceholder;
  // Dependency injection
  private final TrustedIntroductionsDatabase tdb;

  ManageManager(@NonNull RecipientId rid, @NonNull TrustedIntroductionsDatabase tdb, @NonNull String forgottenPlaceholder){
    recipientId = rid;
    this.tdb = tdb;
    this.forgottenPlaceholder = forgottenPlaceholder;
  }

  void getIntroductions(@NonNull Consumer<List<Pair<TI_Data, ManageViewModel.IntroducerInformation>>> listConsumer){
    SignalExecutors.BOUNDED.execute(() -> {

      String introducerServiceId;
      if(recipientId.equals(RecipientId.UNKNOWN)){
        introducerServiceId = null;
      } else {
        try {
          introducerServiceId = Recipient.live(recipientId).resolve().requireServiceId().toString();
        } catch (Error e){
          // Iff the specific introducer does not have a serviceId, the introduction should most likely not have happened (no secure channel)
          // We simply return in this case without posting anything
          Log.e(TAG, "Service Id for recipient " + recipientId + " did not resolve. Returning without results.");
          return;
        }
      }

      // Pull introductions out of the database
      TrustedIntroductionsDatabase.IntroductionReader reader = tdb.getIntroductions(introducerServiceId);
      ArrayList<TI_Data> introductions = new ArrayList<>();
      while(reader.hasNext()){
        introductions.add(reader.getNext());
      }
      // sort by date
      Collections.sort(introductions, Comparator.comparing(TI_Data::getTimestamp));
      ArrayList<Pair<TI_Data, ManageViewModel.IntroducerInformation>> result = new ArrayList<>();
      for (TI_Data d: introductions) {
        ManageViewModel.IntroducerInformation i;
        if(d.getIntroducerServiceId() == null){
          i = new ManageViewModel.IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder);
        } else {
          Recipient r = Recipient.live(TI_Utils.getRecipientIdOrUnknown(d.getIntroducerServiceId())).resolve();
          String number = r.getE164().orElse("");
          // TODO: using getApplication context because the context doesn't matter... (22-10-06)
          // It just circularly gets passed around between methods in the Recipient but is never used for anything.
          i = new ManageViewModel.IntroducerInformation(r.getDisplayNameOrUsername(getApplicationContext()), number);
        }
        result.add(new Pair<>(d, i));
      }
      listConsumer.accept(result);
    });
  }

}
