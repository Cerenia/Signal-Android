/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.send

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientId.from
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TrustedIntroductionSendJob

class PickContactsToIntroduceContract {
  
  object PickContacts : ActivityResultContract<RecipientId, Pair<RecipientId?, ArrayList<RecipientId>?>>() {

    private val TAG = String.format(TI_Utils.TI_LOG_TAG, tag(PickContactsToIntroduceContract::class.java))

    override fun createIntent(context: Context, id: RecipientId): Intent {
      val intent = Intent(context, ContactsSelectionActivity::class.java)
      intent.putExtra(ContactsSelectionActivity.RECIPIENT_ID, id.toLong())
      return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<RecipientId?, ArrayList<RecipientId>?> {
      if (resultCode == Activity.RESULT_OK && intent != null) {
      val recipientId = from(intent.getLongExtra(ContactsSelectionActivity.RECIPIENT_ID, -1))
      val listOfIntroduceeIds = (intent.getParcelableArrayExtra(ContactsSelectionActivity.SELECTED_CONTACTS_TO_FORWARD)) as ArrayList<RecipientId>
      val idSet: HashSet<RecipientId> = HashSet(listOfIntroduceeIds)
      val sendJob = TrustedIntroductionSendJob(recipientId, idSet)
      ApplicationDependencies.getJobManager().add(sendJob)
      return Pair(recipientId, listOfIntroduceeIds)
      } else {
       Log.e(TAG, "PickContactsForTrustedIntroductionsActivity did not return with RESULT_OK!")
       return Pair(null, null)
      }
    }
  }
}