/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionActivity;

public interface ContactSelectionActivityGlue {

  String RECIPIENT_ID                 = "recipient_id";

  static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, ContactsSelectionActivity.class);
    intent.putExtra(RECIPIENT_ID, id.toLong());
    return intent;
  }
}
