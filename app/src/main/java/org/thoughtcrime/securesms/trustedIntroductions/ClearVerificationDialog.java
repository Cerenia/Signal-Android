package org.thoughtcrime.securesms.trustedIntroductions;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Button;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.glue.VerifyDisplayFragmentGlue;

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
     *
     * @param context Caller context.
     * @param status The contacts verification status. Must be strongly verified (@see IdentityTable)
     * @param recipientId Our remote recipient ID
     * @param remoteIdentity Remote identity key
     * @param verifyButton The button to be updated after the clearing operation.
     */
    public static void show(@NonNull Context context, IdentityTable.VerifiedStatus status, RecipientId recipientId, IdentityKey remoteIdentity, Button verifyButton) {
        assert IdentityTable.VerifiedStatus.stronglyVerified(status): "Unsupported Verification status";

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
                    onClearVerification(recipientId, remoteIdentity, verifyButton);
                    intf.dismiss();
                })
                .show();
    }

    static void onClearVerification(RecipientId recipientId, IdentityKey remoteIdentity, Button verifyButton){
        TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, IdentityTable.VerifiedStatus.UNVERIFIED);
        VerifyDisplayFragmentGlue.updateVerifyButtonText(false, verifyButton);
    }
}

