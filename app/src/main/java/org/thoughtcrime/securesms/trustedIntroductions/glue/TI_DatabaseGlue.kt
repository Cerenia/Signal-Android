package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database

interface TI_DatabaseGlue {
  fun fetchRecipientRecord(introduceeId: RecipientId): Map<RecipientId, RecipientRecord>

  fun buildContentValuesForStateUpdate(introduction: TI_Data, newState: TI_Database.State): ContentValues

  fun getSignalWritableDatabase(): SQLiteDatabase

  fun buildContentValuesForInsert(
    state: TI_Database.State,
    introducerServiceId: String,
    introduceeServiceId: String,
    introduceeName: String,
    introduceeNumber: String,
    introduceeIdentityKey: String,
    predictedSecurityNumber: String,
    timestamp: Long
  ): ContentValues

  fun turnAllIntroductionsStale(serviceID: String): Boolean

  fun incomingIntroduction(introduction: TI_Data): Long

  fun deleteIntroduction(introductionId: Long): Boolean

  fun clearIntroducer(introduction: TI_Data): Boolean

  fun getAllDisplayableIntroductions(): TI_Database.IntroductionReader

  fun isRecipientUnknown(serviceID: String): Boolean

  fun acceptIntroduction(introduction: TI_Data): Boolean

  fun rejectIntroduction(introduction: TI_Data): Boolean

  fun atLeastOneIntroductionIs(states: TI_Database.State, introduceeServiceId: String): Boolean

  fun atLeastOneIntroductionIsUnknown(introduceeServiceId: String): Boolean

  fun handleUnknownIntroductions(serviceId: String, encodedIdentityKey: String): TI_Database.State?

  companion object {
    @JvmStatic
    fun executeCreateTable(db: SQLiteDatabase) {
      db.execSQL(TI_Database.CREATE_TABLE)
    }

    @JvmStatic
    fun getTIDatabase(db: SignalDatabase?): TI_DatabaseGlue {
      requireNotNull(db) { "SignalDatabase cannot be null" }
      return TI_Database.instance!!
    }

    @JvmStatic
    fun createSingleton(c: Context, databaseHelper: SignalDatabase): TI_DatabaseGlue {
      return TI_Database(c, databaseHelper)
    }

    @JvmStatic
    fun getCreateTable(): String {
      return TI_Database.CREATE_TABLE
    }
  }
}