package org.thoughtcrime.securesms.trustedIntroductions;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.payments.create.CreatePaymentFragmentArgs;
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity;
import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

/**
 * Utility to display a dialog when the user tries to use the introduction functionality with a contact
 * they have not strongly verified.
 */
public final class CanNotIntroduceDialog {

    private CanNotIntroduceDialog() {
    }

    public static void show(@NonNull Context context, @Nullable IdentityRecord identityRecord) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.CanNotIntroduceDialog__Cant_introduce)
                .setMessage(R.string.CanNotIntroduceDialog__direct_verification_needed_for_trusted_introduction);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.CanNotIntroduceDialog__verify, (dialog, which) -> {
                    dialog.dismiss();
                    if (identityRecord != null){
                        Intent intent = VerifyIdentityActivity.newIntent(context, identityRecord);
                        context.startActivity(intent);
                    } else {
                        // TODO: More sensible error handling? What does it mean if the identityRecord is empty at this point? No longer a contact??
                        Toast.makeText(context, R.string.CanNotIntroduceDialog__Identity_record_empty, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
