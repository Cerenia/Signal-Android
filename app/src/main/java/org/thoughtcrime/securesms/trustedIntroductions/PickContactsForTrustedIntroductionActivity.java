package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal identity database,
 * and let's the user choose a set of them for the purpose of carrying out a trusted introduction.
 */
public final class PickContactsForTrustedIntroductionActivity extends PassphraseRequiredActivity implements ContactsSelectionListFragment.OnContactSelectedListener {

  private static final String TAG = Log.tag(PickContactsForTrustedIntroductionActivity.class);

  public static final String RECIPIENT_ID                 = "recipient_id";
  public static final String SELECTED_CONTACTS_TO_FORWARD = "forwarding_contacts";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  // when done picking contacts (button)
  private View done;
  private ContactsSelectionListFragment ti_contacts;
  private MinimalViewModel viewModel;
  // Alternative text when no contacts are verified
  private TextView                                  no_valid_contacts;
  private ContactFilterView                         contactFilterView;
  private Toolbar           toolbar;


  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, PickContactsForTrustedIntroductionActivity.class);
    intent.putExtra(RECIPIENT_ID, id.toLong());
    return intent;
  }

  @Override protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.trusted_introduction_contacts_picker_activity);

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

    MinimalViewModel.Factory factory = new MinimalViewModel.Factory(recipientId);
    viewModel = new ViewModelProvider(this, factory).get(MinimalViewModel.class);

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

  
  private void displayAlertMessage(@NonNull MinimalViewModel.IntroduceDialogMessageState state) {
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

  private void displayAlertForSingleIntroduction(Recipient recipient, Recipient introducee, @NonNull MinimalViewModel.IntroduceDialogMessageState state){
    String message = getResources().getQuantityString(R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s, 1,
                                                      introducee.getDisplayNameOrUsername(getApplicationContext()), recipient.getDisplayName(this));
    displayAlert(message, state);
  }

  private void displayAlertForMultiIntroduction(Recipient recipient, @NonNull MinimalViewModel.IntroduceDialogMessageState state){
    int count = state.getToIntroduce().size();
    String message = getResources().getQuantityString(R.plurals.PickContactsForTIActivity__introduce_d_contacts_to_s, count,
                                                      count, recipient.getDisplayName(this));
    displayAlert(message, state);
  }

  private void displayAlert(String message, @NonNull MinimalViewModel.IntroduceDialogMessageState state){
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

  private void onFinishedSelection(@NonNull MinimalViewModel.IntroduceDialogMessageState state) {
    Intent           resultIntent = getIntent();
    Set<RecipientId> recipientIds = state.getToIntroduce().stream().map(Recipient::getId).collect(Collectors.toSet());

    resultIntent.putParcelableArrayListExtra(SELECTED_CONTACTS_TO_FORWARD, new ArrayList<>(recipientIds));

    // TODO: Will be in the job, just testing
    // TODO: Remove once job works
    try{
      String message = TrustedIntroductionsStringUtils.buildMessageBody(state.getRecipient().getId(), recipientIds);
      Log.i(TAG, "\n" + message);
    } catch (JSONException je){
      Log.e(TAG, "Something went wrong while building the json Introduction data! " + je.toString());
    } catch (IOException eio){
      Log.e(TAG, "Something went wrong while trying to generate the safety number! " + eio.toString());
    } catch (InvalidKeyException ike){
      Log.e(TAG, "A key was invalid while trying to generate the safety number! " + ike.toString());
    }

    setResult(RESULT_OK, resultIntent);
    finish();
  }

}

