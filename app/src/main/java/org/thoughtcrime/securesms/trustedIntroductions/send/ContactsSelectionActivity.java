package org.thoughtcrime.securesms.trustedIntroductions.send;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal identity database,
 * and let's the user choose a set of them for the purpose of carrying out a trusted introduction.
 */
public final class ContactsSelectionActivity extends PassphraseRequiredActivity implements ContactsSelectionListFragment.OnContactSelectedListener {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ContactsSelectionActivity.class));

  public static final String RECIPIENT_ID                 = "recipient_id";
  public static final String SELECTED_CONTACTS_TO_FORWARD = "forwarding_contacts";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  // when done picking contacts (button)
  private View                          done;
  private ContactsSelectionListFragment ti_contacts;
  private ContactsSelectionViewModel    viewModel;
  // Alternative text when no contacts are verified
  private TextView                      no_valid_contacts;
  private ContactFilterView                         contactFilterView;
  private Toolbar           toolbar;


  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, ContactsSelectionActivity.class);
    intent.putExtra(RECIPIENT_ID, id.toLong());
    return intent;
  }

  @Override protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.ti_contacts_selection_activity);

    RecipientId recipientId = getRecipientID();

    // Bind references
    toolbar           = findViewById(R.id.toolbar);
    contactFilterView = findViewById(R.id.contact_filter_edit_text);
    no_valid_contacts = findViewById(R.id.ti_no_contacts);
    ti_contacts = (ContactsSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.trusted_introduction_contacts_fragment);
    done = findViewById(R.id.done);

    // Initialize
    initializeToolbar();
    initializeContactFilterView();

    ContactsSelectionViewModel.Factory factory = new ContactsSelectionViewModel.Factory(recipientId);
    viewModel = new ViewModelProvider(this, factory).get(ContactsSelectionViewModel.class);

    // # of valid contacts
    viewModel.getContacts().observe(this, contacts -> {
      if(contacts.size() > 0){
        no_valid_contacts.setVisibility(View.GONE);
      } else {
        no_valid_contacts.setVisibility(View.VISIBLE);
      }
    });



    done.setOnClickListener(v ->
                                viewModel.getDialogStateForSelectedContacts(this::displayAlertMessage)
    );

    ti_contacts.setViewModel(viewModel);
    contactFilterView.setOnFilterChangedListener(ti_contacts);
    contactFilterView.setHint(R.string.PickContactsForTIActivity_filter_hint);

    getSupportFragmentManager().addOnBackStackChangedListener(() -> {
      if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
        contactFilterView.setVisibility(View.VISIBLE);
        contactFilterView.focusAndShowKeyboard();
      } else {
        contactFilterView.setVisibility(View.GONE);
      }
    });
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
    toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24);
    toolbar.setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  private void initializeContactFilterView() {
    this.contactFilterView = findViewById(R.id.contact_filter_edit_text);
  }

  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, @Nullable String number){
    int selectedContactsCount = viewModel.getSelectedContactsCount();
    if (selectedContactsCount == 0) {
      toolbar.setTitle(getString(R.string.PickContactsForTIActivity_introduce_contacts));
      disableDone();
    } if (selectedContactsCount > 0){
      enableDone();
      toolbar.setTitle(getResources().getQuantityString(R.plurals.PickContactsForTIActivity_d_contacts, selectedContactsCount, selectedContactsCount));
    } else {
      assert selectedContactsCount < 0 : "Contacts count below 0!";
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

  private void displayAlertMessage(@NonNull ContactsSelectionViewModel.IntroduceDialogMessageState state) {
    Recipient recipient = Util.firstNonNull(state.getRecipient(), Recipient.UNKNOWN);
    List<Recipient> selection = state.getToIntroduce();
    int count = selection.size();
    if(count == 1){
      displayAlertForSingleIntroduction(recipient, selection.get(0), state);
    } else {
      assert count != 0 : "No contacts selected to introduce!";
      displayAlertForMultiIntroduction(recipient, state);
    }
  }

  private void displayAlertForSingleIntroduction(Recipient recipient, Recipient introducee, @NonNull ContactsSelectionViewModel.IntroduceDialogMessageState state){
    String message = getResources().getQuantityString(R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s, 1,
                                                      introducee.getDisplayName(getApplicationContext()), recipient.getDisplayName(this));
    displayAlert(message, state);
  }

  private void displayAlertForMultiIntroduction(Recipient recipient, @NonNull ContactsSelectionViewModel.IntroduceDialogMessageState state){
    int count = state.getToIntroduce().size();
    String message = getResources().getQuantityString(R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s, count,
                                                      count, recipient.getDisplayName(this));
    displayAlert(message, state);
  }

  private void displayAlert(String message, @NonNull ContactsSelectionViewModel.IntroduceDialogMessageState state){
    new AlertDialog.Builder(this)
        .setMessage(message)
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
        .setPositiveButton(R.string.PickContactsForTIActivity_introduce, (dialog, which) -> {
          dialog.dismiss();
          onFinishedSelection(state);
        })
        .setCancelable(true)
        .show();
  }

  private void onFinishedSelection(@NonNull ContactsSelectionViewModel.IntroduceDialogMessageState state) {
    Intent           resultIntent = getIntent();
    Set<RecipientId> recipientIds = state.getToIntroduce().stream().map(Recipient::getId).collect(Collectors.toSet());

    resultIntent.putParcelableArrayListExtra(SELECTED_CONTACTS_TO_FORWARD, new ArrayList<>(recipientIds));

    setResult(RESULT_OK, resultIntent);
    finish();
  }

}

