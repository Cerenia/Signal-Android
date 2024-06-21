package org.thoughtcrime.securesms.trustedIntroductions.database

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.INTRODUCER_SERVICE_ID
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database.TABLE_NAME

object RecipientServiceIdMigration {
  // Idempotent call on backup files to make sure the datatype is right and constraint removed
  fun migrate(context: Application, db: SQLiteDatabase) {
    val sql = "ALTER TABLE $TABLE_NAME MODIFY COLUMN $INTRODUCER_SERVICE_ID TEXT;"
    db.execSQL(sql)
  }
}