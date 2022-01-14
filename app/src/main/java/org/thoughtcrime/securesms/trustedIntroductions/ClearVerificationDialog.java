package org.thoughtcrime.securesms.trustedIntroductions;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.trustedIntroductions.CanNotIntroduceDialog;
/**
 * Dialog is displayed if a user wants to clear a verification status that is higher than 'manually' verified,
 * since this is an operation that is not trivially undone.
 * Only valid with the verification status DIRECTLY_VERIFIED, INTRODUCED or DUPLEX_VERIFIED
 **/
public class ClearVerificationDialog {

    private static boolean clearVerification = false;

    private static void userInteraction(DialogInterface intf, int which){
        if(which == DialogInterface.BUTTON_POSITIVE){
            clearVerification = true;
        }
        intf.dismiss();
    }

    private ClearVerificationDialog() {
    }


    /**
     * @param context Caller context.
     * @param cb Calling Fragment which implements the Callback.
     * @param status The contacts verification status. Must be strongly verified (@see IdentityDatabase)
     * @return Returns the users decision. If true, clear, otherwise don't.
     */
    public static void show(@NonNull Context context, Callback cb, IdentityDatabase.VerifiedStatus status) {
        assert IdentityDatabase.VerifiedStatus.stronglyVerified(status): "Unsupported Verification status";

        clearVerification = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(R.string.ClearVerificationDialog__Title);
        switch (status){
            case DIRECTLY_VERIFIED:
                builder.setMessage(R.string.ClearVerificationDialog__Clear_directly_verified);
                break;
            case INTRODUCED:
                builder.setMessage(R.string.ClearVerificationDialog__Clear_introduced);
                break;
            case DUPLEX_VERIFIED:
                builder.setMessage(R.string.ClearVerificationDialog__Clear_duplex);
                break;
            default:
                assert false;
        }
        builder.setNegativeButton(android.R.string.cancel, (intf, which) -> intf.dismiss())
                .setPositiveButton(R.string.ClearVerificationDialog__Positive, (intf, which) -> {
                    cb.onClearVerification();
                    intf.dismiss();
                })
                .show();
    }

    public interface Callback {
        void onClearVerification();
    }
}

