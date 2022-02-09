package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


/**
 * Queries the Contacts Provider for Contacts which match strongly verified contacts in the Signal identity database,
 * and let's the user choose some for the purpose of carrying out trusted introductions.
 */
public class PickContactsForTrustedIntroductionActivity extends PassphraseRequiredActivity implements IntroductionContactsSelectionListFragment.OnContactSelectedListener, LifecycleOwner {

  private static final String TAG = org.signal.core.util.logging.Log.tag(PickContactsForTrustedIntroductionActivity.class);

  public static final String RECIPIENT_ID = "recipient_id";
  public static final String KEY_SELECTED_CONTACTS_TO_FORWARD = "forwarding_contacts";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  // when done picking contacts (button)
  private View done;
  private IntroductionContactsSelectionListFragment ti_contacts;
  private ContactFilterView contactFilterView;
  private Toolbar           toolbar;

  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, PickContactsForTrustedIntroductionActivity.class);
    intent.putExtra(RECIPIENT_ID, id.toLong());
    return intent;
  }

  @Override protected void onCreate(Bundle savedInstanceState, boolean ready) {
    //getIntent().putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.trusted_introduction_contacts_picker_activity);
    super.onCreate(savedInstanceState, ready);

    dynamicTheme.onCreate(this);

    setContentView(R.layout.trusted_introduction_contacts_picker_activity);

    RecipientId recipientId = getRecipientID();

    // Bind references
    toolbar           = findViewById(R.id.toolbar);
    contactFilterView = findViewById(R.id.contact_filter_edit_text);
    ti_contacts         = (IntroductionContactsSelectionListFragment)getSupportFragmentManager().findFragmentById(R.id.trusted_introduction_contacts_fragment);
    ti_contacts.setRecipientId(recipientId);
    done = findViewById(R.id.done);

    // Initialize
    initializeToolbar();
    initializeContactFilterView();

    TrustedIntroductionContactsViewModel.Factory factory = new TrustedIntroductionContactsViewModel.Factory(recipientId);
    // TODO: Still not sure who the owner of the viewModel should be, Fragment or Activity?
    TrustedIntroductionContactsViewModel viewModel = new ViewModelProvider(this, factory).get(TrustedIntroductionContactsViewModel.class);

    viewModel.getSelectedContacts().observe(this, selected -> {
      if (selected.size() > 0){
        enableDone();
      } else {
        disableDone();
      }
    });

    done.setOnClickListener(v ->
                                viewModel.getDialogStateForSelectedContacts(ti_contacts.getSelectedContacts(), this::displayAlertMessage)
    );

    ti_contacts.setViewModel(viewModel);

    // TODO: Does is load if this is commented?
    /**contactFilterView.setOnFilterChangedListener(query -> {
      if (ti_contacts != null) {
        ti_contacts.setQueryFilter(query);
      }
    });**/

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

  protected void initializeToolbar() {
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
  public void onContactSelected(Optional<RecipientId> recipientId, String number){
    int selectedContactsCount = ti_contacts.getSelectedContactsCount();
    if (selectedContactsCount == 0) {
      toolbar.setTitle(getString(R.string.PickContactsForTIActivity_introduce_contacts));
      disableDone();
    } else {
      assert selectedContactsCount > 0 : "Contacts count below 0!";
      // TODO: 1 vs. many?
      enableDone();
      toolbar.setTitle(getResources().getQuantityString(R.plurals.PickContactsForTIActivity_d_contacts, selectedContactsCount, selectedContactsCount));
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

  protected final void onFinishedSelection() {
    Intent                resultIntent     = getIntent();
    List<SelectedContact> selectedContacts = ti_contacts.getSelectedContacts();
    List<RecipientId>     recipients       = Stream.of(selectedContacts).map(sc -> sc.getOrCreateRecipientId(this)).toList();

    resultIntent.putParcelableArrayListExtra(KEY_SELECTED_CONTACTS_TO_FORWARD, new ArrayList<>(recipients));

    setResult(RESULT_OK, resultIntent);
    finish();
  }
}
