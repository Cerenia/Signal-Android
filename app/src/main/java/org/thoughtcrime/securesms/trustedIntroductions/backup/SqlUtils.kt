/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.LinkedList

class SqlUtils {

  companion object {
    @JvmStatic
    fun getAllTablesTI(db: SupportSQLiteDatabase): List<String> {
      val tables: MutableList<String> = LinkedList()
      db.query("SELECT name FROM sqlite_master WHERE type=? and (name LIKE 'TI_%' or name LIKE 'trusted_%')", arrayOf("table")).use { cursor ->
        while (cursor.moveToNext()) {
          tables.add(cursor.getString(0))
        }
      }
      return tables
    }
  }

}