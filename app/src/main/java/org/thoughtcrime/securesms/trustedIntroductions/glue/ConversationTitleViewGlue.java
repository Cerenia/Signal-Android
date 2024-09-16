package org.thoughtcrime.securesms.trustedIntroductions.glue;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

import android.content.Context;
import android.widget.TextView;

public interface ConversationTitleViewGlue {

  static void setIndividualRecipientTitle(@NonNull Recipient recipient, Context context, TextView title, TextView subtitle, @NonNull Runnable updateVisibility) {
    final String displayName = recipient.getDisplayName(context);
    title.setText(displayName);
    IdentityTableGlue.VerifiedStatus verifiedStatus = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipient.getId());
    switch (verifiedStatus){
      case MANUALLY_VERIFIED:
        subtitle.setText(R.string.ConversationTitleView__manually_verified);
        break;
      case DIRECTLY_VERIFIED:
        subtitle.setText(R.string.ConversationTitleView__directly_verified);
        break;
      case DUPLEX_VERIFIED:
        subtitle.setText(R.string.ConversationTitleView__duplex);
        break;
      case INTRODUCED:
        subtitle.setText(R.string.ConversationTitleView__introduced);
        break;
      case SUSPECTED_COMPROMISE:
        subtitle.setText(R.string.ConversationTitleView__suspected_compromise);
      default:
        subtitle.setText(R.string.ConversationTitleView__unverified); // Should never be visible in this state
        break;
    }
    updateVisibility.run();
  }


}
