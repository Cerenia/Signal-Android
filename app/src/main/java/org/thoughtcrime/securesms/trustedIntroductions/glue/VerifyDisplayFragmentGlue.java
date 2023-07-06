package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.os.Bundle;
import android.widget.Button;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.ClearVerificationDialog;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.verify.VerifyDisplayFragment;

public interface VerifyDisplayFragmentGlue {

  final String VERIFIED_STATE  = "verified_state";
  final String TAG_TI = String.format(TI_Utils.TI_LOG_TAG, Log.tag(VerifyDisplayFragment.class));

  static void initializeVerifyButton(boolean verified, Button verifyButton, RecipientId recipientId, androidx.fragment.app.FragmentActivity activity, IdentityKey remoteIdentity){
    updateVerifyButtonText(verified, verifyButton);
    verifyButton.setOnClickListener((button -> updateVerifyButtonLogic((Button)button, recipientId, activity, remoteIdentity)));
  }

  static void extendBundle(Bundle extras, boolean verifiedState){
    extras.putBoolean(VERIFIED_STATE, verifiedState);
  }

  static void updateVerifyButtonText(boolean verified, Button verifyButton) {
    if (verified) {
      verifyButton.setText(R.string.verify_display_fragment__clear_verification);
    } else {
      verifyButton.setText(R.string.verify_display_fragment__mark_as_verified);
    }
  }

  static void updateVerifyButtonLogic(Button verifyButton, RecipientId recipientId, androidx.fragment.app.FragmentActivity activity, IdentityKey remoteIdentity) {
    // TODO: This needs a good refactoring since I want to be close to the original. I completely mangled this class.
    // Check the current verification status
    IdentityTable.VerifiedStatus previousStatus = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipientId);
    Log.i(TAG_TI, "Saving identity: " + recipientId);
    if (IdentityTable.VerifiedStatus.stronglyVerified(previousStatus)) {
      // TODO: when would this activity ever be null?
      if (activity != null) {
        // go through user check first.
        ClearVerificationDialog.show(activity,  previousStatus, recipientId, remoteIdentity, verifyButton);
      }
    } else if (previousStatus == IdentityTable.VerifiedStatus.MANUALLY_VERIFIED) {
      // manually verified, no user check necessary
      TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, IdentityTable.VerifiedStatus.UNVERIFIED);
      updateVerifyButtonText(false, verifyButton);
    } else {
      // Unverified or default, simply set to manually verified
      TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, IdentityTable.VerifiedStatus.MANUALLY_VERIFIED);
      updateVerifyButtonText(true, verifyButton);
    }
  }
}
