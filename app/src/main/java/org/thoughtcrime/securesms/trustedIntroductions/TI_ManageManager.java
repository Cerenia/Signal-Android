package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.apache.http.impl.io.IdentityInputStreamHC4;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TI_ManageManager {

  // Introducer ID, or special iff all.
  private final RecipientId recipientId;
  // TODO: Does this cause problems?
  public final RecipientId ALL_INTRODUCERS = RecipientId.UNKNOWN;

  // Dependency injection
  private final TrustedIntroductionsDatabase tdb;

  TI_ManageManager(@NonNull RecipientId rid, @NonNull TrustedIntroductionsDatabase tdb){
    recipientId = rid;
    this.tdb = tdb;
  }

  void getIntroductions(@NonNull Consumer<List<TI_Data>> listConsumer){
    SignalExecutors.BOUNDED.execute(() -> {
      // Pull introductions out of the database
      TrustedIntroductionsDatabase.IntroductionReader reader = tdb.getIntroductions(recipientId);
      ArrayList<TI_Data> introductions = new ArrayList<>();
      while(reader.hasNext()){
        introductions.add(reader.getNext());
      }
      // sort by date
      Collections.sort(introductions, Comparator.comparing(TI_Data::getTimestamp));
      listConsumer.accept(introductions);
    });
  }

}
