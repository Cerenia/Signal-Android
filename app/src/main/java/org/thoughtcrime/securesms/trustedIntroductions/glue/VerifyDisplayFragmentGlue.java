package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.trustedIntroductions.ClearVerificationDialog;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.verify.VerifyDisplayFragment;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.ServiceId;

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
    TI_IdentityTable.VerifiedStatus previousStatus = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipientId);
    Log.i(TAG_TI, "Saving identity: " + recipientId);
    if (TI_IdentityTable.VerifiedStatus.stronglyVerified(previousStatus)) {
      // TODO: when would this activity ever be null?
      if (activity != null) {
        // go through user check first.
        ClearVerificationDialog.show(activity, previousStatus, recipientId, remoteIdentity, verifyButton);
      }
    } else if (previousStatus == TI_IdentityTable.VerifiedStatus.MANUALLY_VERIFIED) {
      // manually verified, no user check necessary
      TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, TI_IdentityTable.VerifiedStatus.UNVERIFIED);
      updateVerifyButtonText(false, verifyButton);
    } else {
      // Unverified or default, simply set to manually verified
      TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, TI_IdentityTable.VerifiedStatus.MANUALLY_VERIFIED);
      updateVerifyButtonText(true, verifyButton);
    }
  }

  static void onSuccessfullVerification(RecipientId recipientId, IdentityKey remoteIdentity, Button verifyButton){
    // The fingerprint matched after a QR scann and we can update the users verification status
    TI_Utils.updateContactsVerifiedStatus(recipientId, remoteIdentity, TI_IdentityTable.VerifiedStatus.DIRECTLY_VERIFIED);
    updateVerifyButtonText(true, verifyButton);
  }

  private void updateContactsVerifiedStatus(TI_IdentityTable.VerifiedStatus status, Recipient recipient, IdentityKey remoteIdentity, androidx.fragment.app.FragmentActivity activity) {
    final RecipientId recipientId = recipient.getId();
    ServiceId         sid         = recipient.requireServiceId();
    Log.i(TAG_TI, "Saving identity: " + recipientId);
    SignalExecutors.BOUNDED.execute(() -> {
      try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
        final boolean verified = TI_IdentityTable.VerifiedStatus.isVerified(status);
        if (verified) {
          Log.i(TAG_TI, "Saving identity: " + recipientId);
          AppDependencies.getProtocolStore().aci().identities()
                                 .saveIdentityWithoutSideEffects(recipientId,
                                                                 sid,
                                                                 remoteIdentity,
                                                                 TI_IdentityTable.VerifiedStatus.toVanilla(status),
                                                                 false,
                                                                 System.currentTimeMillis(),
                                                                 true);
        } else {
          AppDependencies.getProtocolStore().aci().identities().setVerified(recipientId, remoteIdentity, TI_IdentityTable.VerifiedStatus.toVanilla(status));
        }

        // For other devices but the Android phone, we map the finer statusses to verified or unverified.
        AppDependencies.getJobManager()
                               .add(new MultiDeviceVerifiedUpdateJob(recipientId,
                                                                     remoteIdentity,
                                                                     TI_IdentityTable.VerifiedStatus.toVanilla(status)));
        StorageSyncHelper.scheduleSyncForDataChange();
        IdentityUtil.markIdentityVerified(activity, recipient, verified, false);
      }
    });
  }

  // TODO: Create a PR for this instead of keeping the change in your codebase. Should be a small enough change to get through.
  /**static void wrapAnimationInTryCatch(){
    // Insert at onAnimationEnd
    try{
      ScaleAnimation scaleAnimation1 = new ScaleAnimation(1, 0, 1, 0,
                                                          ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                          ScaleAnimation.RELATIVE_TO_SELF, 0.5f);

      scaleAnimation1.setInterpolator(new AnticipateInterpolator());
      scaleAnimation1.setDuration(500);
      ViewUtil.animateOut(qrVerified, scaleAnimation1, View.GONE);
      ViewUtil.fadeIn(qrCode, 800);
      qrCodeContainer.setEnabled(true);
      tapLabel.setText(getString(R.string.verify_display_fragment__tap_to_scan));
    } catch (IllegalStateException e){
      // TODO: Maybe create PR for that at some point? Not really problem in normal operation, I encounter that because I am
      // verifying lots of contacts fast by hand
      Log.e(TAG, "Illegal state! Possibly the user navigated back before the feedback was posted.");
      e.printStackTrace();
      // simply do nothing in this case
    }
  }**/
}
