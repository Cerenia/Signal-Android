package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageListFragment.ID_KEY;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageListFragment.TYPE_KEY;

/**
 * Opens an Activity for Managing Trusted Introductions.
 * Will either open just the Introductions made by a specific contact
 * or all Introductions depending on how you navigated to that screen.
 */
public class ManageActivity extends PassphraseRequiredActivity implements ManageListFragment.onAllNavigationClicked {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageActivity.class));

  private long introducerId;

  public enum ActiveTab {
    NEW,
    LIBRARY,
    ALL;

    public static ActiveTab fromString(String state) {
      if(state.equals(ALL.toString())) return ALL;
      if(state.equals(LIBRARY.toString())) return LIBRARY;
      if(state.equals(NEW.toString())) return NEW;
      else{
        throw new AssertionError("No such screen state!");
      }
    }

    public static ActiveTab fromInt(int position){
      switch(position) {
        case 0:
          return NEW;
        case 1:
          return LIBRARY;
        case 2:
          return ALL;
        default:
          throw new AssertionError("Invalid Tab position!");
      }
    }

    public static int toInt(ActiveTab tab){
      switch(tab){
        case NEW:
          return 0;
        case LIBRARY:
          return 1;
        case ALL:
          return 2;
        default:
          throw new AssertionError("Unknown Tab!");
      }
    }

  }

  // String
  public static final String INTRODUCER_ID                 = "recipient_id";
  // Instead of passing the RecipientID of the introducer as a string.
  public static final long ALL_INTRODUCTIONS = RecipientId.UNKNOWN.toLong();

  private Toolbar            toolbar;
  private ContactFilterView contactFilterView;



  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  /**
   * @param id Pass unknown to get the view for all introductions.
   */
  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull RecipientId id){
    Intent intent = new Intent(context, ManageActivity.class);
    intent.putExtra(INTRODUCER_ID, id.toLong());
    return intent;
  }


  // TODO: You are probably overriding the wrong function... onCreate(Bundle savedInstanceState) is final..
  @Override protected void onCreate(Bundle savedInstanceState, boolean ready){
    //Decide what kind of screen must be instantiated
    RecipientId introducerId = setIntroducerId(savedInstanceState);
    ActiveTab   t;
    // TODO: Differentiation from savedInstanceState?
    t = ActiveTab.ALL;
    super.onCreate(savedInstanceState, ready);

    dynamicTheme.onCreate(this);
    setContentView(R.layout.ti_manage_activity);

    // Bind views
    toolbar = findViewById(R.id.toolbar);
    contactFilterView = findViewById(R.id.introduction_filter_edit_text);

    // Initialize
    ManageListFragment fragment;
    if(savedInstanceState == null){
      fragment = new ManageListFragment();
      Bundle fragmentBundle = new Bundle();
      fragmentBundle.putLong(ID_KEY, introducerId.toLong());
      fragmentBundle.putString(TYPE_KEY, t.toString());
      fragment.setArguments(fragmentBundle);
      FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
      fragmentTransaction.setReorderingAllowed(true);
      fragmentTransaction.addToBackStack(t.toString());
      fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, fragment, t.toString());
      fragmentTransaction.commit();
    } else {
      fragment = (ManageListFragment) getSupportFragmentManager().findFragmentByTag(t.toString());
    }
    initializeToolbar();

    // Observers
    contactFilterView.setOnFilterChangedListener(fragment);
    contactFilterView.setHint(R.string.ManageIntroductionsActivity__Filter_hint);
  }

  @Override public void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putLong(INTRODUCER_ID, introducerId);
    super.onSaveInstanceState(outState);
  }

  @Override public void goToAll() {
    // New all Fragment
    ActiveTab          t        = ActiveTab.ALL;
    ManageListFragment fragment = new ManageListFragment();
    Bundle fragmentBundle = new Bundle();
    fragmentBundle.putLong(ID_KEY, ALL_INTRODUCTIONS);
    fragmentBundle.putString(TYPE_KEY, t.toString());
    fragment.setArguments(fragmentBundle);
    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    fragmentTransaction.setReorderingAllowed(true);
    fragmentTransaction.addToBackStack(t.toString());
    // TODO??
    //fragmentTransaction.detach((ManageListFragment) getSupportFragmentManager().findFragmentByTag(ActiveTab.RECIPIENT_SPECIFIC.toString()));
    fragmentTransaction.add(R.id.trusted_introduction_manage_fragment, fragment, t.toString());
    fragmentTransaction.commit();
    contactFilterView.setOnFilterChangedListener(fragment);
    // clearing to avoid trailing text that does not filter
    contactFilterView.clear();
  }

  /**
   * @param savedInstanceState bundle that may hold instance state.
   * @return resolved introducerId
   */
  private RecipientId setIntroducerId(@Nullable Bundle savedInstanceState){
    if(savedInstanceState == null){
      introducerId = getIntent().getLongExtra(INTRODUCER_ID, ALL_INTRODUCTIONS);
    } else{
      introducerId = savedInstanceState.getLong(INTRODUCER_ID);
    }
    return RecipientId.from(introducerId);
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);
    toolbar.setTitle(R.string.ManageIntroductionsActivity__Toolbar_Title_Recipient);
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
