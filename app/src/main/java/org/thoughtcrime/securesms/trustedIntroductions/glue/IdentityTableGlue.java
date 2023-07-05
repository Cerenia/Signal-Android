package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.database.IdentityTableExports;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable;

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
  static void createSingleton(Context c, SignalDatabase databaseHelper){
    IdentityTableGlue    identityTable = new org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable(c, databaseHelper);
    IdentityTableExports exports       = IdentityTable.Companion.TI_export();
    TI_IdentityTable.Singleton.setExportedPrivates(exports);
    TI_IdentityTable.Singleton.setInstance(identityTable);
  }

  static IdentityTableGlue getInstance(){
    return TI_IdentityTable.Singleton.getInst();
  }

  /**
   * Publicly exposes verified status by recipient id.
   * @param id: id of the recipient
   * @return The VerifiedStatus of the recipient or default if the recipient is not in the database.
   */
  IdentityTable.VerifiedStatus getVerifiedStatus(@Nullable RecipientId id);
}
