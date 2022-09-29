package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ButtonStripItemView;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * Opens an Activity for Managing Trusted Introductions.
 * Will either open just the Introductions made by a specific contact (//TODO: add tab/button for navigating to all?)
 * or all Introductions depending on how you navigated to that screen.
 */
public class ManageActivity extends PassphraseRequiredActivity implements ManageListFragment.onAllNavigationClicked {

  private static final String TAG = Log.tag(ManageActivity.class);

  // Used instead of name & number in introductions where introducer information was cleared.
  public static final String FORGOTTEN = "unknown";

  @Override public void goToAll() {
    // TODO
  }

  public enum IntroductionScreenType {
    ALL,
    RECIPIENT_SPECIFIC;
  }

  // String
  public static final String INTRODUCER_ID                 = "recipient_id";
  // Instead of passing the RecipientID of the introducer as a string.
  public static final long ALL_INTRODUCTIONS = RecipientId.UNKNOWN.toLong();

  private ManageListFragment introductionsFragment;
  private Toolbar            toolbar;
  private ContactFilterView contactFilterView;


  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();
  private final Context context = this;

  /**
   * @param id Pass unknown to get the view for all introductions.
   */
  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, ManageActivity.class);
    intent.putExtra(INTRODUCER_ID, id.toLong());
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

    // Initialize
    FragmentManager fragmentManager = getSupportFragmentManager();
    introductionsFragment = (ManageListFragment) fragmentManager.findFragmentById(R.id.trusted_introduction_manage_fragment);

    IntroductionScreenType t;
    String introducerName = null;
    if (introducerId.equals(RecipientId.UNKNOWN)){
      t = IntroductionScreenType.ALL;

    } else {
      t = IntroductionScreenType.RECIPIENT_SPECIFIC;
      introducerName = Recipient.live(introducerId).resolve().getDisplayNameOrUsername(this);
      // Ordering important. ViewModel must be set before the Fragment is inflated!
      introductionsFragment.setViewModel(viewModel);
    }
    viewModel.loadIntroductions();
    if(savedInstanceState == null){
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.setReorderingAllowed(true);
      //introductionsFragment = (ManageListFragment) fragmentManager.findFragmentById(R.id.trusted_introduction_manage_fragment);
      // TODO: don't do that...
      introductionsFragment = new ManageListFragment();
      // Ordering important. ViewModel must be set before the Fragment is inflated! TODO: when?
      introductionsFragment.setViewModel(viewModel);
      if(t.equals(IntroductionScreenType.ALL)){
        fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, introductionsFragment, IntroductionScreenType.ALL.toString());
      } else {
        fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, introductionsFragment, IntroductionScreenType.RECIPIENT_SPECIFIC.toString());
      }
      fragmentTransaction.commit();
    }

    initializeToolbar();
    initializeNavigationButton(t);

    // Initialize
    initializeToolbar();
    initializeNavigationButton(t);
    ManageViewModel.Factory factory = new ManageViewModel.Factory(introducerId, t, introducerName, this);
    viewModel = new ViewModelProvider(this, factory).get(ManageViewModel.class);
    viewModel.loadIntroductions();
    introductionsFragment.setViewModel(viewModel);

    // Observers
    final String finalIntroducerName = introducerName;
    viewModel.getIntroductions().observe(this, introductions -> {
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
        navigationExplanation.setVisibility(View.VISIBLE);
        introductionsFragment.refreshList();
      } else {
        no_introductions.setVisibility(View.VISIBLE);
        navigationExplanation.setVisibility(View.GONE);
        if(finalIntroducerName == null){
          no_introductions.setText(R.string.ManageIntroductionsActivity__No_Introductions_all);
        } else {
          no_introductions.setText(this.getString(R.string.ManageIntroductionsActivity__No_Introductions_from, finalIntroducerName));
        }
      }
    });
    contactFilterView.setOnFilterChangedListener(introductionsFragment);
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
    return RecipientId.from(getIntent().getLongExtra(INTRODUCER_ID, ALL_INTRODUCTIONS));
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);
    toolbar.setTitle(R.string.ManageIntroductionsActivity__Toolbar_Title);
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
