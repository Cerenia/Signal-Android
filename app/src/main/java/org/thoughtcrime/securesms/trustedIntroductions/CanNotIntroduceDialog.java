package org.thoughtcrime.securesms.trustedIntroductions;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.model.IdentityRecord;

/**
 * Utility to display a dialog when the user tries to use the introduction functionality with a contact
 * they have not strongly verified, an SMS contact or a group.
 */
public final class CanNotIntroduceDialog {


    public enum ConversationType {
        SMS,
        GROUP,
        SINGLE_SECURE_TEXT;

        private static void setNegativeButtonNotSupported(AlertDialog.Builder b){
            b.setNegativeButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
        }
    }

    private CanNotIntroduceDialog() {
    }

    public static void show(@NonNull Context context, @Nullable IdentityRecord identityRecord, ConversationType conversationType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(R.string.CanNotIntroduceDialog__Cant_introduce);

        switch (conversationType){
            case SMS:
                builder.setMessage(R.string.CanNotIntroduceDialog__SMS_contact_not_applicable);
                ConversationType.setNegativeButtonNotSupported(builder);
                break;
            case GROUP:
                builder.setMessage(R.string.CanNotIntroduceDialog__Group_not_yet_supported);
                ConversationType.setNegativeButtonNotSupported(builder);
                break;
            case SINGLE_SECURE_TEXT: // same as default
                default:
                builder.setMessage(R.string.CanNotIntroduceDialog__direct_verification_needed_for_trusted_introduction);
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
                        });

        }
        builder.show();
    }
}
