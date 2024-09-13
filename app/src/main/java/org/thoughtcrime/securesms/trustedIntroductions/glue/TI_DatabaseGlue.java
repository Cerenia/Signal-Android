package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.thoughtcrime.securesms.database.SQLiteDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;

import java.util.Map;

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

  static TI_DatabaseGlue createSingleton(Context c, SignalDatabase databaseHelper) {
    return new TI_Database(c, databaseHelper);
  }

  static String getCreateTable(){
    return TI_Database.CREATE_TABLE;
  }

  Map<RecipientId, RecipientRecord> fetchRecipientRecord(RecipientId introduceeId);

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

  boolean atLeastOneIntroductionIs(TI_Database.State state, @NotNull String introduceeServiceId);

  void handleDanglingIntroductions(String serviceId, String encodedIdentityKey);
}
