/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.send

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import org.signal.glide.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientId.from
import org.thoughtcrime.securesms.trustedIntroductions.glue.ContactSelectionActivityGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.ConversationFragmentGlue
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TrustedIntroductionSendJob

abstract class PickContactsToIntroduce: ActivityResultContract<RecipientId, Unit>() {
  override fun createIntent(context: Context, id: RecipientId): Intent {
    val intent = Intent(context, ContactsSelectionActivity::class.java)
    intent.putExtra(ContactSelectionActivityGlue.RECIPIENT_ID, id.toLong())
    return intent
  }


  override fun parseResult(resultCode: Int, intent: Intent?): Unit {
    if (resultCode == Activity.RESULT_OK && intent != null) {
      val recipientId = from(intent.getLongExtra(ContactSelectionActivityGlue.RECIPIENT_ID, -1))
      val listOfIntroduceeIds = (intent.getParcelableExtra(ContactSelectionActivityGlue.SELECTED_CONTACTS_TO_FORWARD)) as ArrayList<RecipientId>
      val idSet: HashSet<RecipientId> = HashSet(listOfIntroduceeIds)
      val sendJob = TrustedIntroductionSendJob(recipientId, idSet)
      ApplicationDependencies.getJobManager().add(sendJob)
    } else {
      Log.e(ConversationFragmentGlue.TAG, "PickContactsForTrustedIntroductionsActivity did not return with RESULT_OK!")
    }
  }
}