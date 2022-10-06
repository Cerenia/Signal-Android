package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import org.apache.http.impl.io.IdentityInputStreamHC4;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static androidx.camera.core.CameraX.getContext;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.FORGOTTEN;
import static org.webrtc.ContextUtils.getApplicationContext;

public class ManageManager {

  // Introducer ID, or special iff all.
  private final RecipientId recipientId;
  // TODO: Does this cause problems?
  private final RecipientId ALL_INTRODUCERS = RecipientId.from(ManageActivity.ALL_INTRODUCTIONS);

  // Dependency injection
  private final TrustedIntroductionsDatabase tdb;

  ManageManager(@NonNull RecipientId rid, @NonNull TrustedIntroductionsDatabase tdb){
    recipientId = rid;
    this.tdb = tdb;
  }

  void getIntroductions(@NonNull Consumer<List<Pair<TI_Data, ManageViewModel.IntroducerInformation>>> listConsumer){
    SignalExecutors.BOUNDED.execute(() -> {
      // Pull introductions out of the database
      TrustedIntroductionsDatabase.IntroductionReader reader = tdb.getIntroductions(recipientId);
      ArrayList<TI_Data> introductions = new ArrayList<>();
      while(reader.hasNext()){
        introductions.add(reader.getNext());
      }
      // sort by date
      Collections.sort(introductions, Comparator.comparing(TI_Data::getTimestamp));
      ArrayList<Pair<TI_Data, ManageViewModel.IntroducerInformation>> result = new ArrayList<>();
      for (TI_Data d: introductions) {
        ManageViewModel.IntroducerInformation i;
        if(d.getIntroducerId().equals(RecipientId.UNKNOWN) || d.getIntroducerId().toLong() > 10000){ // TODO: uggly hack until I figure out the bug, remove after
          i = new ManageViewModel.IntroducerInformation(FORGOTTEN, FORGOTTEN);
        } else {
          Recipient r = Recipient.live(d.getIntroducerId()).resolve();
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
