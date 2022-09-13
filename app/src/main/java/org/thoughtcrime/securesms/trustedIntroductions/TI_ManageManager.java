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

import java.util.List;

public class TI_ManageManager {

  // Introducer ID, or special iff all.
  private final RecipientId recipientId;
  // TODO: Does this cause problems?
  public final RecipientId ALL_INTRODUCERS = RecipientId.UNKNOWN;

  // Dependency injection
  private final TrustedIntroductionsDatabase tdb;
  // We change verification status based on the users choice to accept an introduction.
  private final IdentityDatabase idb;
  // In case an introduction for an unknown recipient gets accepted
  private final RecipientDatabase rdb;

  TI_ManageManager(@NonNull RecipientId rid, @NonNull TrustedIntroductionsDatabase tdb, @NonNull IdentityDatabase idb, @NonNull RecipientDatabase rdb){
    recipientId = rid;
    this.tdb = tdb;
    this.idb = idb;
    this.rdb = rdb;
  }

  void getIntroductions(@NonNull Consumer<List<TI_Data>> introductions){
    SignalExecutors.BOUNDED.execute(() -> {
      // Pull introductions out of the database
    });
  }

}
