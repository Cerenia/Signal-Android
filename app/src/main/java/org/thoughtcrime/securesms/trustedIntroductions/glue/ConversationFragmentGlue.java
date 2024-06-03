/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCallerKt;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.signal.glide.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TrustedIntroductionSendJob;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionActivity;

import java.util.ArrayList;
import java.util.HashSet;

import kotlin.Unit;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;

public interface ConversationFragmentGlue {

  static final String TAG = String.format(TI_Utils.TI_LOG_TAG, org.signal.core.util.logging.Log.tag(ConversationFragmentGlue.class));

  // TI Contact Picker Activity Launcher
  // TODO: Check and rewrite according to this. StartActivityForResult has been deprecated for a while.
  // https://developer.android.com/training/basics/intents/result
  static ActivityResultLauncher<Intent> tiLaunch = ActivityResultCallerKt.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (ActivityResultCallback<ActivityResult>) result -> {
    if (result.getResultCode() == Activity.RESULT_OK) {
      Intent intent = result.getData();
      assert intent != null; // Programming error.
      // Start TI Job with the id arrayList
      RecipientId            recipientId   = RecipientId.from(intent.getLongExtra(ContactSelectionActivityGlue.RECIPIENT_ID, -1));
      ArrayList<RecipientId> introduceeIds = intent.getParcelableArrayListExtra(ContactSelectionActivityGlue.SELECTED_CONTACTS_TO_FORWARD);
      HashSet<RecipientId>   idSet         = new HashSet<>(introduceeIds);
      ApplicationDependencies.getJobManager().add(new TrustedIntroductionSendJob(recipientId, idSet));
    } else {
      Log.e(TAG, "PickContactsForTrustedIntroductionsActivity did not return with RESULT_OK!");
    }
  });
}