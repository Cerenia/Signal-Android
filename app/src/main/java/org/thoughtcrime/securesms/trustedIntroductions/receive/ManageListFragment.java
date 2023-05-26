package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.RecyclerView;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.splitIntroductionDate;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.ActiveTab.NEW;

import com.google.android.material.button.MaterialButton;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import androidx.tracing.Trace;

public class ManageListFragment extends Fragment implements DeleteIntroductionDialog.DeleteIntroduction, ForgetIntroducerDialog.ForgetIntroducer, ManageListItem.SwitchClickListener {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageListFragment.class));

  // TODO: Needed?
  private ProgressWheel showIntroductionsProgress;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private TextView no_introductions;
  private View                           all_header;
  private ManageActivity.ActiveTab tab = NEW;
  private MaterialButton showConflicting;
  // Don't refresh list 5 times on setup.
  private boolean sCisFirstInit = true;

  private MaterialButton showAccepted;
  private boolean sAisFirstInit = true;

  private MaterialButton showRejected;
  private boolean sRisFirstInit = true;

  private MaterialButton showStale;
  private boolean sSisFirstInit = true;

  // Because final onCreate in AppCompat dissalows me from using a Fragment Factory, I need to use a Bundle for Arguments.
  static String TYPE_KEY = "type_key";

  static String FORGOTTEN_INTRODUCER;

  public ManageListFragment(){
    super();
  }

  public ManageListFragment(@NonNull ViewModelStoreOwner owner, @NonNull ManageActivity.ActiveTab type){
    this.tab = type;
  }

  @Override
  public void onCreate(Bundle b){
    super.onCreate(b);
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.ti_manage_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
    super.onViewCreated(view, savedInstanceState);
    ManageViewModel.Factory factory = new ManageViewModel.Factory(FORGOTTEN_INTRODUCER);
    viewModel = new ViewModelProvider(getActivity(), factory).get(ManageViewModel.class);
    if(!viewModel.introductionsLoaded()){
      viewModel.loadIntroductions();
    }
    adapter = new ManageAdapter(requireContext(), new IntroductionClickListener(this, this), this);
    RecyclerView introductionList = view.findViewById(R.id.recycler_view);
    introductionList.setClipToPadding(true);
    introductionList.setAdapter(adapter);
    no_introductions = view.findViewById(R.id.no_introductions_found);
    all_header = view.findViewById(R.id.manage_fragment_header);
    // Filter state
    showConflicting = view.findViewById(R.id.conflictingFilter);
    showStale = view.findViewById(R.id.staleFilter);
    showAccepted = view.findViewById(R.id.acceptedFilter);
    showRejected = view.findViewById(R.id.rejectedFilter);
    if(savedInstanceState != null && savedInstanceState.getString(TYPE_KEY)!=null){
      tab = ManageActivity.ActiveTab.fromString(savedInstanceState.getString(TYPE_KEY));
    }
    showConflicting.setVisibility(View.VISIBLE);
    showStale.setVisibility(View.VISIBLE);
    if(viewModel != null){
      // viewModel has default values.
      showConflicting.setChecked(viewModel.showConflicting().getValue());
      showStale.setChecked(viewModel.showStale().getValue());
    } else {
      showConflicting.setChecked(true);
      showStale.setChecked(true);
    }
    showConflicting.addOnCheckedChangeListener((button, isChecked) ->  {
      viewModel.setShowConflicting(isChecked);
    });
    showStale.addOnCheckedChangeListener((button, isChecked) ->{
      viewModel.setShowStale(isChecked);
    });
    switch(tab){
      case NEW:
        // Accepted, Rejected and stale don't show up
        showAccepted.setVisibility(View.GONE);
        showRejected.setVisibility(View.GONE);
        showStale.setVisibility(View.GONE);
        break;
      case LIBRARY:
        showAccepted.setVisibility(View.VISIBLE);
        showRejected.setVisibility(View.VISIBLE);
        if(viewModel != null){
          showAccepted.setChecked(viewModel.showAccepted().getValue());
          showRejected.setChecked(viewModel.showRejected().getValue());
        } else {
          showAccepted.setChecked(true);
          showRejected.setChecked(true);
        }
        showAccepted.addOnCheckedChangeListener((button, isChecked) ->{
          viewModel.setShowAccepted(isChecked);
        });
        showRejected.addOnCheckedChangeListener((button, isChecked) ->{
          viewModel.setShowRejected(isChecked);
        });
    }
    // Filter state Obvservers
    viewModel.showConflicting().observe(getViewLifecycleOwner(), state ->{
      sCisFirstInit = onFilterStateChanged(showConflicting, state, sCisFirstInit);
    });
    viewModel.showStale().observe(getViewLifecycleOwner(), state ->{
      sSisFirstInit = onFilterStateChanged(showStale, state, sSisFirstInit);
    });
    viewModel.showAccepted().observe(getViewLifecycleOwner(), state ->{
      sAisFirstInit = onFilterStateChanged(showAccepted, state, sAisFirstInit);
    });
    viewModel.showRejected().observe(getViewLifecycleOwner(), state->{
      sRisFirstInit = onFilterStateChanged(showRejected, state, sRisFirstInit);
    });
    // Introduction Observer
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), introductions -> {
      // Screen layout
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
        all_header.setVisibility(View.VISIBLE);
      } else {
        no_introductions.setVisibility(View.VISIBLE);
        all_header.setVisibility(View.GONE);
        no_introductions.setText(R.string.ManageIntroductionsFragment__No_Introductions_all);
      }
      refreshList();
    });
  }

  /**
   *
   * @param b which button to set.
   * @param newCheckState the new newCheckState.
   * @param isFirstInit global indicating if the button was ever set before.
   * @return false, the value to be assigned to the global xXisFirstInit of the button if the method succeeds.
   */
  private boolean onFilterStateChanged(MaterialButton b, Boolean newCheckState, Boolean isFirstInit){
    if(b.isChecked() != newCheckState){
      b.setChecked(newCheckState);
    }
    if(!isFirstInit){
      refreshList();
    }
    return false;
  }

  @Override public void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putString(TYPE_KEY, tab.toString());
    super.onSaveInstanceState(outState);
  }

  /**
   * Decides if this datum must be displayed in the fragment. Depends on tabtype and active filters.
   * @param p the datum to be evaluated.
   * @return true if displayed, false if not.
   */
  private boolean isDisplayed(Pair<TI_Data, ManageViewModel.IntroducerInformation> p){
    TrustedIntroductionsDatabase.State s = p.first.getState();
    switch(tab){
      case NEW:
        // Only display pending and conflicting
        if(!(s == TrustedIntroductionsDatabase.State.PENDING || s == TrustedIntroductionsDatabase.State.CONFLICTING)){
          return false;
        }
        if(userFiltered(s)){
          return false;
        }
        break;
      case LIBRARY:
        // Display everything but pending
        if((s == TrustedIntroductionsDatabase.State.PENDING)){
          return false;
        }
        if(userFiltered(s)){
          return false;
        }
        break;
      default:
        // fail open
        return true;
    }
    return true;
  }

  /**
   * Checks the state of the introduction against the user filters.
   * @return true if filtered, false otherwise
   */
  private boolean userFiltered(TrustedIntroductionsDatabase.State s){
    switch(s){
      case ACCEPTED:
        if(!viewModel.showAccepted().getValue()){
          return true;
        }
        break;
      case REJECTED:
        if(!viewModel.showRejected().getValue()){
          return true;
        }
        break;
      case CONFLICTING:
        if(!viewModel.showConflicting().getValue()){
          return true;
        }
        break;
      case STALE_PENDING:
        if(!viewModel.showStale().getValue()){
          return true;
        }
        break;
      case STALE_REJECTED:
        if(!viewModel.showStale().getValue() && !viewModel.showStale().getValue()){
          return true;
        }
        break;
      case STALE_ACCEPTED:
        if(!viewModel.showAccepted().getValue() && !viewModel.showStale().getValue()){
          return true;
        }
        break;
      case STALE_CONFLICTING:
        if(!viewModel.showConflicting().getValue() && !viewModel.showStale().getValue()){
          return true;
        }
        break;
      default: // fail open
        return false;
    }
    return false;
  }

  /**
   * Filters complete introduction list by Tab type, search filter, button selectors and finally sorts.
   * @param introductions All introductions returned by ViewModel.
   * @param filter The user provided filter.
   * @return the filtered list appropriate for the fragment depending on tab, button selectors and filter.
   */
  private List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> getFiltered(List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> introductions, @Nullable String filter){
    List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered = new ArrayList<>();
    for (Pair<TI_Data, ManageViewModel.IntroducerInformation> p: introductions) {
      if(isDisplayed(p)){
        filtered.add(p);
      }
    }
    if(filter != null){
      if(!filter.isEmpty() && filter.compareTo("") != 0){
        Pattern filterPattern = Pattern.compile("\\A" + filter + ".*", Pattern.CASE_INSENSITIVE);
        for (Pair<TI_Data, ManageViewModel.IntroducerInformation> p: introductions){
          TI_Data                     d = p.first;
          TI_Utils.TimestampDateParts timestampParts = splitIntroductionDate(d.getTimestamp());
          // Split up for debugging. May be one big expr.
          boolean matchYear = filterPattern.matcher(timestampParts.year).find();
          boolean matchMonth = filterPattern.matcher(timestampParts.month).find();
          boolean matchDay = filterPattern.matcher(timestampParts.day).find();
          boolean matchHours = filterPattern.matcher(timestampParts.hours).find();
          boolean matchMinutes = filterPattern.matcher(timestampParts.minutes).find();
          boolean matchSeconds = filterPattern.matcher(timestampParts.seconds).find();
          boolean matchIntroduceeName = filterPattern.matcher(d.getIntroduceeName()).find();
          boolean matchIntroduceeNumber = filterPattern.matcher(d.getIntroduceeNumber()).find();
          boolean matchIntroducerName = filterPattern.matcher(p.second.name).find();
          boolean matchIntroducerNumber = filterPattern.matcher(p.second.number).find();
          if (!(matchYear || matchMonth || matchDay || matchHours || !matchMinutes || matchSeconds || matchIntroduceeName || matchIntroduceeNumber
                || matchIntroducerName || matchIntroducerNumber)){
            filtered.remove(p);
          }
        }
      }
    }
    return sortIntroductions(filtered);
  }

  private static Function<Pair<TI_Data, ManageViewModel.IntroducerInformation>, Long> dateExtractor = p -> p.first.getTimestamp();
  private static Function<Pair<TI_Data, ManageViewModel.IntroducerInformation>, String> introduceeNameExtractor = p -> p.first.getIntroduceeName();
  private static Function<Pair<TI_Data, ManageViewModel.IntroducerInformation>, Long> stateExtractor = p -> (long)p.first.getState().toInt();

  /**
   * Sorts introductions depending on the tab type.
   * NEW: by date
   * LIBRARY: by introducee
   * ALL: by introducer
   * The sorted list.
   */
  private List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> sortIntroductions(List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered){
    switch(tab){
      case NEW:
        filtered.sort(Comparator.comparing(dateExtractor));
        return filtered;
      case LIBRARY:
        // First by state, then introducee, then date
        filtered.sort(Comparator.comparing(stateExtractor).thenComparing(introduceeNameExtractor).thenComparing(dateExtractor));
        return filtered;
      default:
        throw new AssertionError(TAG +"Unknown tab type!");
    }
  }

  void refreshList(){
    Trace.beginSection("refreshList:" + tab.toString());
      if(adapter != null){
        List l = viewModel.getIntroductions().getValue();
        if(l == null){
          Log.e(TAG, "Introductions list not yet loaded when calling refreshList!");
          return;
        }
        adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), viewModel.getTextFilter().getValue()));
      }
    Trace.endSection();
  }

  public void onFilterChanged(String filter) {
    if(adapter != null){
      viewModel.setTextFilter(filter);
      List l = viewModel.getIntroductions().getValue();
      if(l == null){
        Log.e(TAG, "Introductions list not yet loaded when calling onFilterChanged!");
        return;
      }
      adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), filter));
    }
  }

  @Override public void deleteIntroduction(@NonNull Long introductionId) {
    viewModel.deleteIntroduction(introductionId);
  }

  @Override public void forgetIntroducer(@NonNull Long introductionId) {
    viewModel.forgetIntroducer(introductionId);
  }


  @Override public void accept(@NonNull Long introductionId) {
    viewModel.acceptIntroduction(introductionId);
  }

  @Override public void reject(@NonNull Long introductionId) {
    viewModel.rejectIntroduction(introductionId);
  }

  private class IntroductionClickListener implements ManageAdapter.ItemClickListener {

    DeleteIntroductionDialog.DeleteIntroduction deleteHandler;
    ForgetIntroducerDialog.ForgetIntroducer     forgetHandler;
    Context c;

    public IntroductionClickListener(DeleteIntroductionDialog.DeleteIntroduction d, ForgetIntroducerDialog.ForgetIntroducer f){
      this.deleteHandler = d;
      this.forgetHandler = f;
      c = requireContext();
    }


    // TODO
    @Override public void onItemClick(ManageListItem item) {
      String name = item.getIntroducerName(requireContext());
      if(!name.equals(getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer))){
        ForgetIntroducerDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), item.getIntroducerName(requireContext()), item.getDate(), forgetHandler);
        return;
      }
      // All screen
      Toast.makeText(c, R.string.ManageIntroductionsFragment__already_erased_toast, Toast.LENGTH_LONG).show();
    }

    @Override public void onItemLongClick(ManageListItem item) {
      TrustedIntroductionsDatabase.State s = item.getState();
      /*
      if(s.equals(TrustedIntroductionsDatabase.State.CONFLICTING)){
        // TODO: Proper go through managing conflicts path, to implement later.
        Toast.makeText(c, R.string.ManageIntroductionsFragment__conflict_resolution_todo, Toast.LENGTH_LONG).show();
      }*/
      DeleteIntroductionDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), item.getIntroducerName(requireContext()), item.getDate(), deleteHandler);
    }
  }
}
