package org.thoughtcrime.securesms.trustedIntroductions.glue;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionViewModel;

import android.content.Context;
import android.widget.TextView;

public interface ConversationTitleViewGlue {

  static void setIndividualRecipientTitle(@NonNull Recipient recipient, Context context, TextView title, TextView subtitle, @NonNull Runnable updateVisibility) {
    final String displayName = recipient.getDisplayNameOrUsername(context);
    title.setText(displayName);
    IdentityTable.VerifiedStatus verifiedStatus = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(recipient.getId());
    switch (verifiedStatus){
      case MANUALLY_VERIFIED:
        subtitle.setText(R.string.ConversationTitleView_manually_verified);
        break;
      case DIRECTLY_VERIFIED:
        subtitle.setText(R.string.ConversationTitleView_directly_verified);
        break;
      case DUPLEX_VERIFIED:
        subtitle.setText(R.string.ConversationTitleView_duplex);
        break;
      case INTRODUCED:
        subtitle.setText(R.string.ConversationTitleView_introduced);
        break;
      default:
        subtitle.setText(R.string.ConversationTitleView_unverified); // Should never be visible in this state
        break;
    }
    updateVisibility.run();
  }


}
