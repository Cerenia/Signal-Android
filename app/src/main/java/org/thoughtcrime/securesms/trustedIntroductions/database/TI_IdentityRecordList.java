package org.thoughtcrime.securesms.trustedIntroductions.database;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class TI_IdentityRecordList {

  public static final org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityRecordList EMPTY = new org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityRecordList(Collections.emptyList());

  public static final long DEFAULT_UNTRUSTED_WINDOW = TimeUnit.SECONDS.toMillis(5);

  private final List<TI_IdentityRecord> identityRecords;
  private final boolean              isVerified;
  private final boolean              isUnverified;

  public TI_IdentityRecordList(@NonNull Collection<TI_IdentityRecord> records) {
    identityRecords = new ArrayList<>(records);
    isVerified      = isVerified(identityRecords);
    isUnverified    = isUnverified(identityRecords);
  }

  public List<TI_IdentityRecord> getIdentityRecords() {
    return Collections.unmodifiableList(identityRecords);
  }

  public boolean isVerified() {
    return isVerified;
  }

  public boolean isUnverified() {
    return isUnverified;
  }

  private static boolean isVerified(@NonNull Collection<TI_IdentityRecord> identityRecords) {
    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (!IdentityTableGlue.VerifiedStatus.isVerified(identityRecord.getVerifiedStatus())) {
        return false;
      }
    }

    return identityRecords.size() > 0;
  }

  private static boolean isUnverified(@NonNull Collection<TI_IdentityRecord> identityRecords) {
    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == IdentityTableGlue.VerifiedStatus.UNVERIFIED) {
        return true;
      }
    }

    return false;
  }

  public boolean isUnverified(boolean excludeFirstUse) {
    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (excludeFirstUse && identityRecord.isFirstUse()) {
        continue;
      }

      if (identityRecord.getVerifiedStatus() == IdentityTableGlue.VerifiedStatus.UNVERIFIED) {
        return true;
      }
    }

    return false;
  }

  public boolean isUntrusted(boolean excludeFirstUse) {
    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (excludeFirstUse && identityRecord.isFirstUse()) {
        continue;
      }

      if (isUntrusted(identityRecord, DEFAULT_UNTRUSTED_WINDOW)) {
        return true;
      }
    }

    return false;
  }

  public @NonNull List<TI_IdentityRecord> getUntrustedRecords() {
    return getUntrustedRecords(DEFAULT_UNTRUSTED_WINDOW);
  }

  public @NonNull List<TI_IdentityRecord> getUntrustedRecords(long untrustedWindowMillis) {
    List<TI_IdentityRecord> results = new ArrayList<>(identityRecords.size());

    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (isUntrusted(identityRecord, untrustedWindowMillis)) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public @NonNull List<Recipient> getUntrustedRecipients() {
    List<Recipient> untrusted = new ArrayList<>(identityRecords.size());

    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (isUntrusted(identityRecord, DEFAULT_UNTRUSTED_WINDOW)) {
        untrusted.add(Recipient.resolved(identityRecord.getRecipientId()));
      }
    }

    return untrusted;
  }

  public @NonNull List<TI_IdentityRecord> getUnverifiedRecords() {
    List<TI_IdentityRecord> results = new ArrayList<>(identityRecords.size());

    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == IdentityTableGlue.VerifiedStatus.UNVERIFIED) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public @NonNull List<Recipient> getUnverifiedRecipients() {
    List<Recipient> unverified = new ArrayList<>(identityRecords.size());

    for (TI_IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == IdentityTableGlue.VerifiedStatus.UNVERIFIED) {
        unverified.add(Recipient.resolved(identityRecord.getRecipientId()));
      }
    }

    return unverified;
  }

  private static boolean isUntrusted(@NonNull TI_IdentityRecord identityRecord, long untrustedWindowMillis) {
    return !identityRecord.isApprovedNonBlocking() &&
           System.currentTimeMillis() - identityRecord.getTimestamp() < untrustedWindowMillis;
  }

}
