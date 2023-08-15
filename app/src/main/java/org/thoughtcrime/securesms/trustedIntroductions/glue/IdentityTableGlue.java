package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityStoreRecord;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable;

import java.util.ArrayList;

public interface IdentityTableGlue {
  String TI_ADDRESS_PROJECTION    = IdentityTable.ADDRESS;
  String VERIFIED = IdentityTable.VERIFIED;
  String TABLE_NAME = IdentityTable.TABLE_NAME;

  Cursor getCursorForTIUnlocked();

  /**
   * Throws Exception if called repeatedly
   * @param c
   * @param databaseHelper
   * @return
   */
  static IdentityTableGlue createSingletons(Context c, SignalDatabase databaseHelper){
    IdentityTableGlue    identityTable = new org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable(c, databaseHelper);
    TI_IdentityTable.Singleton.setInstance(identityTable);
    TI_IdentityTable.Singleton.createSignalIdentityTable(c, databaseHelper);
    return identityTable;
  }

  static IdentityTableGlue getInstance(@Nullable SignalDatabase db){
    if (db == null){ // check for nullpointer to equal rest of Kotlin code in Signals Identity table
      throw new NullPointerException();
    }
    return TI_IdentityTable.Singleton.getTI_inst();
  }

  /**
   * Publicly exposes verified status by recipient id.
   * @param id: id of the recipient
   * @return The VerifiedStatus of the recipient or default if the recipient is not in the database.
   */
  TI_IdentityTable.VerifiedStatus getVerifiedStatus(@Nullable RecipientId id);

  enum VerifiedStatus{
    DEFAULT, MANUALLY_VERIFIED, UNVERIFIED, DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED;

    public Integer toInt(){
      switch(this){
        case DEFAULT:
          return 0;
        case MANUALLY_VERIFIED:
          return 1;
        case UNVERIFIED:
          return 2;
        case DIRECTLY_VERIFIED:
          return 3;
        case INTRODUCED:
          return 4;
        case DUPLEX_VERIFIED:
          return 5;
        default:
          return 2; // fail closed
      }
    }

    public static VerifiedStatus forState(Integer state){
      switch(state){
        case 0:
          return DEFAULT;
        case 1:
          return MANUALLY_VERIFIED;
        case 2:
          return UNVERIFIED;
        case 3:
          return DIRECTLY_VERIFIED;
        case 4:
          return INTRODUCED;
        case 5:
          return DUPLEX_VERIFIED;
        default:
          return UNVERIFIED;
      }
    }

    public static Integer toVanilla(Integer status){
      VerifiedStatus s = forState(status);
      switch(s){
        case DEFAULT:
          return IdentityTable.VerifiedStatus.DEFAULT.toInt();
        case DIRECTLY_VERIFIED:
        case INTRODUCED:
        case DUPLEX_VERIFIED:
        case MANUALLY_VERIFIED:
          return IdentityTable.VerifiedStatus.VERIFIED.toInt();
        case UNVERIFIED:
        default:
          return IdentityTable.VerifiedStatus.UNVERIFIED.toInt();
      }
    }

    /**
     * Much of the code relies on checks of the verification status that are not interested in the finer details.
     * This function can now be called instead of doing 4 comparisons manually.
     * Do not use this to decide if trusted introduction is allowed.
     * @return True is verified, false otherwise.
     */
    public static Boolean isVerified(VerifiedStatus status){
      switch (status){
        case DIRECTLY_VERIFIED:
        case INTRODUCED:
        case DUPLEX_VERIFIED:
        case MANUALLY_VERIFIED:
          return true;
        case DEFAULT:
        case UNVERIFIED:
        default:
          return false;
      }
    }

    /**
     * Adding this in order to be able to change my mind easily on what should unlock a TI.
     * For now, only direct verification unlocks forwarding a contact's public key,
     * in order not to propagate malicious verifications further than one connection.
     *
     * @param status the verification status to be checked
     * @return true if strongly enough verified to unlock forwarding this contact as a
     * trusted introduction, false otherwise
     */
    public static Boolean ti_forwardUnlocked(VerifiedStatus status){
      switch(status){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
          return true;
        case INTRODUCED:
        case MANUALLY_VERIFIED:
        case DEFAULT:
        case UNVERIFIED:
        default:
          return false;
      }
    }

    /**
     * A recipient can only receive TrustedIntroductions iff they have previously been strongly verified.
     * This function exists as it's own thing to allow for flexible changes.
     *
     * @param status The verification status of the recipient.
     * @return True if this recipient can receive trusted introductions.
     */
    public static Boolean ti_recipientUnlocked(VerifiedStatus status){
      switch(status){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
          //INTRODUCED: false (if someone is being MiTmed, an introduction could be sensitive data. So you should be sure who you are talking to before you forward)
          //TODO: Both versions of this have their own pros and cons... Which one should it be?
          // for now, opting to unlock also on introduced in order to give more room to play for the study
        case INTRODUCED:
          return true;
        case MANUALLY_VERIFIED:
        case DEFAULT:
        case UNVERIFIED:
        default:
          return false;
      }
    }

    /**
     * Returns true for any non-trivial positive verification status.
     * Used to prompt user when clearing a verification status that is not trivially recoverable and to decide
     * if a channel is secure enough to forward an introduction over.
     */
    public static Boolean stronglyVerified(VerifiedStatus status){
      switch(status){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
        case INTRODUCED:
          return true;
        case MANUALLY_VERIFIED:
        case DEFAULT:
        case UNVERIFIED:
        default:
          return false;
      }
    }
  }
}
