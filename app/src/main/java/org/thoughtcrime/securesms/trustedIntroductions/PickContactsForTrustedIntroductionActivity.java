package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;


/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal identity database,
 * and let's the user choose some for the purpose of carrying out trusted introductions.
 */
public class PickContactsForTrustedIntroductionActivity {

  private static final String TAG = org.signal.core.util.logging.Log.tag(PickContactsForTrustedIntroductionActivity.class);

  public static final String RECIPIENT_ID = "recipient_id";

  // when done picking contacts (button)
  private View done;
  private TrustedIntroductionContactsViewModel viewModel;

  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, PickContactsForTrustedIntroductionActivity.class);
    intent.putExtra(RECIPIENT_ID, id.toLong());
    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, ContactsCursorLoader.DisplayMode.FLAG_PUSH);
    return intent;
  }

  @Override protected void onCreate(Bundle icicle, boolean ready) {
    getIntent().putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.trusted_introduction_contacts_picker_activity);
    super.onCreate(icicle, ready);

    TrustedIntroductionContactsViewModel.Factory factory = new TrustedIntroductionContactsViewModel.Factory(getRecipientID());

    done = findViewById(R.id.done);

    viewModel = new ViewModelProvider(this, factory).get(TrustedIntroductionContactsViewModel.class);

    done.setOnClickListener(v ->
                                viewModel.getDialogStateForSelectedContacts(contactsFragment.getSelectedContacts(), this::displayAlertMessage)
    );

    disableDone();
  }

  @Override
  protected void initializeToolbar() {
    getToolbar().setNavigationIcon(R.drawable.ic_arrow_left_24);
    getToolbar().setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    // TODO: needed?
    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    if (contactsFragment.getSelectedContactsCount() < 1) {
      disableDone();
    }
  }

  @Override
  public void onSelectionChanged() {
        // TODO: Dafuq :P
    int selectedContactsCount = contactsFragment.getTotalMemberCount();
    if (selectedContactsCount == 0) {
      getToolbar().setTitle(getString(R.string.PickContactsForTIActivity_introduce_contacts));
    } else {
      assert selectedContactsCount > 0 : "Contacts count below 0!";
      enableDone();
      getToolbar().setTitle(getResources().getQuantityString(R.plurals.PickContactsForTIActivity_d_contacts, selectedContactsCount, selectedContactsCount));
    }
  }

  private RecipientId getRecipientID(){
    return RecipientId.from(getIntent().getLongExtra(RECIPIENT_ID, -1));
  }

  private void enableDone() {
    done.setEnabled(true);
    done.animate().alpha(1f);
  }

  private void disableDone() {
    done.setEnabled(false);
    done.animate().alpha(0.5f);
  }

  private void displayAlertMessage(@NonNull TrustedIntroductionContactsViewModel.IntroduceDialogMessageState state) {
    Recipient recipient = Util.firstNonNull(state.getRecipient(), Recipient.UNKNOWN);

    String message = getResources().getQuantityString(R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s, state.getSelectionCount(),
                                                      recipient.getDisplayName(this), state.getSelectionCount());

    new AlertDialog.Builder(this)
        .setMessage(message)
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
        .setPositiveButton(R.string.PickContactsForTIActivity_introduce, (dialog, which) -> {
          dialog.dismiss();
          onFinishedSelection();
        })
        .setCancelable(true)
        .show();
  }
}
