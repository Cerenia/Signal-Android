package org.thoughtcrime.securesms.trustedIntroductions.receive

import androidx.core.util.Consumer
import androidx.core.util.Pair
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue
import java.util.Collections

class ManageManager(
  private val tdb: TI_DatabaseGlue,
  private val forgottenPlaceholder: String
) {
  companion object {
    private val TAG = String.format(
      TI_Utils.TI_LOG_TAG,
      Log.tag(ManageManager::class.java)
    )
  }

  fun getIntroductions(listConsumer: Consumer<ArrayList<Pair<TI_Data, ManageViewModel.IntroducerInformation>>>) {
    SignalExecutors.BOUNDED.execute {
      // Pull introductions out of the database
      val reader = tdb.getAllDisplayableIntroductions()
      val introductions = ArrayList<TI_Data>()

      while (reader.hasNext()) {
        introductions.add(reader.getNext()!!)
      }

      // Sort by date
      introductions.sortWith(Comparator { d1, d2 ->
        d1.timestamp.compareTo(d2.timestamp)
      })

      val result = ArrayList<Pair<TI_Data, ManageViewModel.IntroducerInformation>>()

      for (d in introductions) {
        val i = if (d.introducerServiceId == TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
          ManageViewModel.IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder)
        } else {
          try {
            val r = Recipient.live(
              TI_Utils.getRecipientIdOrUnknown(
                requireNotNull(d.introducerServiceId)
              )
            ).resolve()

            val number = r.e164.orElse("")
            // TODO: using getApplication context because the context doesn't matter... (22-10-06)
            // It just circularly gets passed around between methods in the Recipient but is never used for anything.
            ManageViewModel.IntroducerInformation(
              r.getDisplayName(AppDependencies.application.applicationContext),
              number
            )
          } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, e.message ?: "Unknown error")
            null
          }
        }

        // TODO: this should not happen
        i?.let {
          result.add(Pair(d, it))
        }
      }

      listConsumer.accept(result)
    }
  }
}