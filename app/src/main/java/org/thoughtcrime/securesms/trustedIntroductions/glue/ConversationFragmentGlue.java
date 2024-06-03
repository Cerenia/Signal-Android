/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

public interface ConversationFragmentGlue {

  static final String TAG = String.format(TI_Utils.TI_LOG_TAG, org.signal.core.util.logging.Log.tag(ConversationFragmentGlue.class));

  ActivityResultLauncher makeContactSelectionActivityLauncher(Context context);

}