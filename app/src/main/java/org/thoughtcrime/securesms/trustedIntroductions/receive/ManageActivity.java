package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionActivity;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Opens an Activity for Managing Trusted Introductions.
 * Will either open just the Introductions made by a specific contact (//TODO: add tab/button for navigating to all?)
 * or all Introductions depending on how you navigated to that screen.
 */
public class ManageActivity extends PassphraseRequiredActivity{

  private static final String TAG = Log.tag(ManageActivity.class);

  // String
  public static final String INTRODUCER_ID                 = "recipient_id";
  // Instead of passing the RecipientID of the introducer as a string.
  public static final String ALL_INTRODUCTIONS = RecipientId.UNKNOWN.toString();

  private ManageViewModel viewModel;
  private ManageListFragment introductions;
  private Toolbar toolbar;
  private ContactFilterView contactFilterView;
  private TextView navigationExplanation;
  private TextView        no_introductions;

  // TODO: may want an action bar for button
  private final DynamicTheme dynamicTheme = new DynamicTheme();

  /**
   * @param id Pass unknown to get the view for all introductions.
   */
  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, ManageActivity.class);
    intent.putExtra(INTRODUCER_ID, id.toString());
    return intent;
  }

  @Override protected void onCreate(Bundle savedInstanceState, boolean ready){
    super.onCreate(savedInstanceState, ready);

    dynamicTheme.onCreate(this);
    setContentView(R.layout.ti_manage_activity);

    RecipientId introducerId = getIntroducerId();

    // Bind views
    toolbar = findViewById(R.id.toolbar);
    contactFilterView = findViewById(R.id.introduction_filter_edit_text);
    no_introductions = findViewById(R.id.no_introductions_found);
    navigationExplanation = findViewById(R.id.navigation_explanation);
    introductions = (ManageListFragment) getSupportFragmentManager().findFragmentById(R.id.trusted_introduction_manage_fragment);

    // Initialize
    initializeToolbar();
    ManageViewModel.Factory factory = new ManageViewModel.Factory(introducerId);
    viewModel = new ViewModelProvider(this, factory).get(ManageViewModel.class);
    introductions.setViewModel(viewModel);

    // Observers
    viewModel.getIntroductions().observe(this, introductions -> {
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
      } else {
        no_introductions.setVisibility(View.VISIBLE);
      }
    });
    contactFilterView.setOnFilterChangedListener(introductions);
    contactFilterView.setHint(R.string.ManageIntroductionsActivity__Filter_hint);

    getSupportFragmentManager().addOnBackStackChangedListener(() -> {
      if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
        contactFilterView.setVisibility(View.VISIBLE);
        contactFilterView.focusAndShowKeyboard();
      } else {
        contactFilterView.setVisibility(View.GONE);
      }
    });

  }

  private RecipientId getIntroducerId(){
    return RecipientId.from(getIntent().getStringExtra(INTRODUCER_ID));
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);
    RecipientId introducer = getIntroducerId();
    if(introducer.toString().equals(ALL_INTRODUCTIONS)){
      toolbar.setTitle(R.string.ManageIntroductionsActivity__Title_Introductions_all);
    } else {
      toolbar.setTitle(Recipient.live(introducer).resolve().getDisplayNameOrUsername(getApplicationContext()));
    }
    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
    toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24);
    toolbar.setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }
}
