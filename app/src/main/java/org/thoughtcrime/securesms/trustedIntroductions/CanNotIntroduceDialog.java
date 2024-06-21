package org.thoughtcrime.securesms.trustedIntroductions;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Utility to display a dialog when the user tries to use the introduction functionality with a contact
 * they have not strongly verified.
 */
public final class CanNotIntroduceDialog {

    private CanNotIntroduceDialog() {
    }

    public static void show(@NonNull Context context, @NonNull RecipientId recipientId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.CanNotIntroduceDialog__Cant_introduce)
                .setMessage(R.string.CanNotIntroduceDialog__direct_verification_needed_for_trusted_introduction);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.CanNotIntroduceDialog__verify, (dialog, which) -> {
                    dialog.dismiss();
                    // TODO: Open correct activity
                })
                .show();
    }
}
