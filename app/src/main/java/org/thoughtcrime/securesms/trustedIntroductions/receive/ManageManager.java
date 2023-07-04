package org.thoughtcrime.securesms.trustedIntroductions.receive;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.webrtc.ContextUtils.getApplicationContext;

public class ManageManager {

  private static final String TAG =  String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageManager.class));

  @NonNull private final String      forgottenPlaceholder;
  // Dependency injection
  private final          TI_Database tdb;

  ManageManager(@NonNull TI_Database tdb, @NonNull String forgottenPlaceholder){
    this.tdb = tdb;
    this.forgottenPlaceholder = forgottenPlaceholder;
  }

  void getIntroductions(@NonNull Consumer<List<Pair<TI_Data, ManageViewModel.IntroducerInformation>>> listConsumer){
    SignalExecutors.BOUNDED.execute(() -> {

      // Pull introductions out of the database
      TI_Database.IntroductionReader reader        = tdb.getAllDisplayableIntroductions();
      ArrayList<TI_Data>             introductions = new ArrayList<>();
      while(reader.hasNext()){
        introductions.add(reader.getNext());
      }
      // sort by date
      Collections.sort(introductions, Comparator.comparing(TI_Data::getTimestamp));
      ArrayList<Pair<TI_Data, ManageViewModel.IntroducerInformation>> result = new ArrayList<>();
      ManageViewModel.IntroducerInformation i = null;
      for (TI_Data d: introductions) {
        if(d.getIntroducerServiceId() == null){
          i = new ManageViewModel.IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder);
        } else {
          try {
            Recipient r      = Recipient.live(TI_Utils.getRecipientIdOrUnknown(d.getIntroducerServiceId())).resolve();
            String    number = r.getE164().orElse("");
            // TODO: using getApplication context because the context doesn't matter... (22-10-06)
            // It just circularly gets passed around between methods in the Recipient but is never used for anything.
            i = new ManageViewModel.IntroducerInformation(r.getDisplayNameOrUsername(getApplicationContext()), number);
          } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
          }
        } // TODO: this should not happen
        if (i != null) {
          result.add(new Pair<>(d, i));
        }
      }
      listConsumer.accept(result);
    });
  }

}
