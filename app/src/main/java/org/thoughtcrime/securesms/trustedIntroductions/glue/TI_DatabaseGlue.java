package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SQLiteDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;

public interface TI_DatabaseGlue {
  static void executeCreateTable(net.zetetic.database.sqlcipher.SQLiteDatabase db){
    db.execSQL(TI_Database.CREATE_TABLE);
  }
  static TI_DatabaseGlue getTIDatabase(@Nullable SignalDatabase db){
    if (db == null){ // check for nullpointer to equal rest of Kotlin code in Signals Identity table
      throw new NullPointerException();
    }
    return TI_Database.getInstance();
  }

  static TI_DatabaseGlue createSingleton(Context c, SignalDatabase databaseHelper) throws Exception {
    TI_Database tiDatabase = new TI_Database(c, databaseHelper);
    TI_Database.setInstance(tiDatabase);
    return tiDatabase;
  }

  Cursor fetchRecipientDBCursor(RecipientId introduceeId);

  void modifyIntroduceeVerification(String introduceeServiceId, IdentityTable.VerifiedStatus previousIntroduceeVerification, TI_Database.State newState, String format);

  ContentValues buildContentValuesForStateUpdate(TI_Data introduction, TI_Database.State newState);
  SQLiteDatabase getSignalWritableDatabase();

  ContentValues buildContentValuesForInsert(TI_Database.State state, String introducerServiceId, String introduceeServiceId, String introduceeName, String introduceeNumber, String introduceeIdentityKey, String predictedSecurityNumber, long timestamp);

  boolean turnAllIntroductionsStale(String serviceID);

  long incomingIntroduction(TI_Data introduction);

  boolean deleteIntroduction(long introductionId);

  boolean clearIntroducer(TI_Data introduction);

  TI_Database.IntroductionReader getAllDisplayableIntroductions();

  boolean acceptIntroduction(TI_Data introduction);

  boolean rejectIntroduction(TI_Data introduction);
}
