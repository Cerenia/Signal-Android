package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Opens an Activity for Managing Trusted Introductions.
 * Will either open just the Introductions made by a specific contact (//TODO: add tab/button for navigating to all?)
 * or all Introductions depending on how you navigated to that screen.
 */
public class ManageActivity extends PassphraseRequiredActivity implements ManageListFragment.onAllNavigationClicked {

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

    // Initialize
    IntroductionScreenType t;
    String introducerName = null;
    if (introducerId.equals(RecipientId.UNKNOWN)){
      t = IntroductionScreenType.ALL;
    } else {
      t = IntroductionScreenType.RECIPIENT_SPECIFIC;
      introducerName = Recipient.live(introducerId).resolve().getDisplayNameOrUsername(this);
    }
    if(savedInstanceState == null){
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.setReorderingAllowed(true);
      ManageListFragment fragment = new ManageListFragment(introducerId, t, introducerName, this);
      fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, fragment, t.toString());
      fragmentTransaction.addToBackStack(null);
      fragmentTransaction.commit();
    }
    initializeToolbar();

    // Observers
    contactFilterView.setOnFilterChangedListener(introductionsFragment);
    contactFilterView.setHint(R.string.ManageIntroductionsActivity__Filter_hint);

  }

  @Override public void goToAll() {
    // New all Fragment
    IntroductionScreenType t = IntroductionScreenType.ALL;
    ManageListFragment fragment = new ManageListFragment(RecipientId.UNKNOWN,  t,null, this);
    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    fragmentTransaction.setReorderingAllowed(true);
    fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, fragment, t.toString());
    fragmentTransaction.addToBackStack(null);
    fragmentTransaction.commit();
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
