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
public class ManageActivity extends PassphraseRequiredActivity{

  private static final String TAG = Log.tag(ManageActivity.class);

  // Used instead of name & number in introductions where introducer information was cleared.
  public static final String FORGOTTEN = "unknown";

  public enum IntroductionScreenType {
    ALL,
    RECIPIENT_SPECIFIC;
  }

  // String
  public static final String INTRODUCER_ID                 = "recipient_id";
  // Instead of passing the RecipientID of the introducer as a string.
  public static final long ALL_INTRODUCTIONS = RecipientId.UNKNOWN.toLong();

  private ManageViewModel    viewModel;
  private ManageListFragment introductionsFragment;
  private Toolbar            toolbar;
  private ContactFilterView contactFilterView;
  private TextView navigationExplanation;
  private TextView            no_introductions;
  private ButtonStripItemView button;


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
    IntroductionScreenType t;
    String introducerName = null;
    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    //introductionsFragment = (ManageListFragment) fragmentManager.findFragmentById(R.id.trusted_introduction_manage_fragment);
    introductionsFragment = new ManageListFragment();
    if (introducerId.equals(RecipientId.UNKNOWN)){
      t = IntroductionScreenType.ALL;
      ManageViewModelAll.Factory factory = new ManageViewModelAll.Factory(introducerId, t, introducerName, this);
      viewModel = new ViewModelProvider(this, factory).get(ManageViewModelAll.class);
      fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, introductionsFragment, IntroductionScreenType.ALL.toString());
    } else {
      t = IntroductionScreenType.RECIPIENT_SPECIFIC;
      introducerName = Recipient.live(introducerId).resolve().getDisplayNameOrUsername(this);
      ManageViewModelSingle.Factory factory = new ManageViewModelSingle.Factory(introducerId, t, introducerName, this);
      viewModel = new ViewModelProvider(this, factory).get(ManageViewModelSingle.class);
      fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, introductionsFragment, IntroductionScreenType.RECIPIENT_SPECIFIC.toString());
    }
    fragmentTransaction.commit();
    viewModel.loadIntroductions();
    initializeToolbar();
    initializeNavigationButton(t);
    introductionsFragment.setViewModel(viewModel);

    // Observers
    final String finalIntroducerName = introducerName;
    contactFilterView.setOnFilterChangedListener(introductionsFragment);
    contactFilterView.setHint(R.string.ManageIntroductionsActivity__Filter_hint);

    viewModel.getIntroductions().observe(this, introductions -> {
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
        navigationExplanation.setVisibility(View.VISIBLE);
        //introductionsFragment.refreshList(); //May cause NullptrException
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

    getSupportFragmentManager().addOnBackStackChangedListener(() -> {
      if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
        contactFilterView.setVisibility(View.VISIBLE);
        contactFilterView.focusAndShowKeyboard();
      } else {
        contactFilterView.setVisibility(View.GONE);
      }
    });
  }

  private void initializeNavigationButton(ManageActivity.IntroductionScreenType t){
    button = findViewById(R.id.navigate_all_button);
    switch(t){
      case RECIPIENT_SPECIFIC:
        button.setVisibility(View.VISIBLE);
        break;
      case ALL:
        button.setVisibility(View.GONE);
        break;
      default:
        throw new AssertionError(TAG + "No such screenType!");
    }
    button.setOnIconClickedListener(new Function0<Unit>() {
      @Override public Unit invoke() {
        finish(); // Make sure the ViewModel is not reused. TODO: There is most likely a more idiomatic way..
        startActivity(ManageActivity.createIntent(context, RecipientId.UNKNOWN));
        return null;
      }
    });
  }

  private RecipientId getIntroducerId(){
    return RecipientId.from(getIntent().getLongExtra(INTRODUCER_ID, ALL_INTRODUCTIONS));
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);
    RecipientId introducer = getIntroducerId();
    if(introducer.toString().equals(ALL_INTRODUCTIONS)){
      toolbar.setTitle(R.string.ManageIntroductionsActivity__Title_Introductions_all);
    } else {
      String name = Recipient.live(introducer).resolve().getDisplayNameOrUsername(this);
      toolbar.setTitle(this.getString(R.string.ManageIntroductionsActivity__Title_Introductions_from, name));
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
