package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.IntroductionScreenType.ALL;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.IntroductionScreenType.RECIPIENT_SPECIFIC;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.ButtonStripItemView;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.components.settings.models.Button;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class ManageListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener, DeleteIntroductionDialog.DeleteIntroduction, ForgetIntroducerDialog.ForgetIntroducer {

  private String TAG = Log.tag(ManageListFragment.class);

  // TODO: Will probably need that for all screen
  private ProgressWheel showIntroductionsProgress;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private RecyclerView introductionList;
  private TextView no_introductions;
  private TextView navigationExplanation;
  private TextView from_title_view;
  private final RecipientId recipient;
  private final ManageActivity.IntroductionScreenType type;
  private final String name;
  private final Context managerContext;

  public ManageListFragment(){
    recipient = RecipientId.UNKNOWN;
    type = ALL;
    name = null;
    managerContext = getActivity();
  }

  public ManageListFragment(RecipientId id, ManageActivity.IntroductionScreenType t, @Nullable String introducerName, Context context){
    super(R.layout.ti_manage_fragment);
    recipient = id;
    type = t;
    name = introducerName;
    managerContext = context;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
    ManageViewModel.Factory factory = new ManageViewModel.Factory(recipient, type, name, managerContext);
    viewModel = new ViewModelProvider(this, factory).get(ManageViewModel.class);
    viewModel.loadIntroductions();
    introductionList = view.findViewById(R.id.recycler_view);
    introductionList.setClipToPadding(true);
    introductionList.setAdapter(adapter);
    introductionList.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          // TODO: Add scrollCallback if needed, determined during Integration testing
        }
      }
    });
    initializeAdapter(type);
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), users -> {
      List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered = getFiltered(users, null);
      if(adapter != null){
        adapter.submitList(new ArrayList<>(filtered));
      }
    });
    initializeNavigationButton(view);
    no_introductions = view.findViewById(R.id.no_introductions_found);
    navigationExplanation = view.findViewById(R.id.navigation_explanation);
    // Observer
    final String finalIntroducerName = name;
    viewModel.getIntroductions().observe(getViewLifecycleOwner(), introductions -> {
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
        navigationExplanation.setVisibility(View.VISIBLE);
        from_title_view.setVisibility(View.VISIBLE);
        refreshList();
      } else {
        no_introductions.setVisibility(View.VISIBLE);
        navigationExplanation.setVisibility(View.GONE);
        from_title_view.setVisibility(View.GONE);
        if(finalIntroducerName == null){
          no_introductions.setText(R.string.ManageIntroductionsActivity__No_Introductions_all);
        } else {
          no_introductions.setText(this.getString(R.string.ManageIntroductionsActivity__No_Introductions_from, finalIntroducerName));
        }
      }
    });
    from_title_view = view.findViewById(R.id.introduction_title_view);
    if (name == null){
      from_title_view.setVisibility(View.GONE);
    } else {
      from_title_view.setText(String.format(getString(R.string.ManageIntroductionsActivity__Title_Introductions_from), name));
      from_title_view.setVisibility(View.VISIBLE);
    }
  }


  // Make sure the Fragment has been inflated before calling this!
  private void initializeAdapter(ManageActivity.IntroductionScreenType t){
    if(t == RECIPIENT_SPECIFIC){
      adapter = new ManageAdapter(requireContext(), new IntroductionClickListener(this, this), t);
    } else {
      // TODO: all adapter has different list item layouts + a sticky header.
    }
    // TODO: Race condition? Iff not, at least factor out.
    introductionList.setAdapter(adapter);
    introductionList.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          // TODO: Add scrollCallback if needed, determined during Integration testing
        }
      }
    });
  }

  private void initializeNavigationButton(@NonNull View view){
    ButtonStripItemView                   button = view.findViewById(R.id.navigate_all_button);
    ManageActivity.IntroductionScreenType t      = viewModel.getScreenType();
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
        ((ManageActivity) getActivity()).goToAll();
        return null;
      }
    });
  }

  private List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> getFiltered(List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> introductions, @Nullable String filter){
    List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered = introductions != null ? new ArrayList<>(introductions) : new ArrayList<>();
    if(filter != null){
      if(!filter.isEmpty() && filter.compareTo("") != 0){
        Pattern filterPattern = Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE);
        for (Pair<TI_Data, ManageViewModel.IntroducerInformation> p: introductions){
          TI_Data d = p.first;
          if (!filterPattern.matcher(INTRODUCTION_DATE_PATTERN.format(d.getTimestamp())).find() &&
              !filterPattern.matcher(d.getIntroduceeName()).find() &&
              !filterPattern.matcher(d.getIntroduceeNumber()).find() &&
              !filterPattern.matcher(d.getState().toString()).find()){
            filtered.remove(p);
          }
        }
      }
    }
    return filtered;
  }

  void refreshList(){
    if(adapter != null){
      adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), viewModel.getFilter().getValue()));
    }
  }

  @Override public void onFilterChanged(String filter) {
    viewModel.setQueryFilter(filter);
    adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), filter));
  }

  @Override public void deleteIntroduction(@NonNull Long introductionId) {
    viewModel.deleteIntroduction(introductionId);
  }

  @Override public void forgetIntroducer(@NonNull Long introductionId) {
    viewModel.forgetIntroducer(introductionId);
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

    private String getIntroducerName(ManageListItem item){
      String itemIntroducerName;
      if(name == null){
        // All screen
        itemIntroducerName = item.getIntroducerName(c);
        // could still be null after iff this introducer information has been cleared.
        itemIntroducerName = (itemIntroducerName == null) ? "forgotten introducer": itemIntroducerName;
      } else {
        itemIntroducerName = name;
      }
      return itemIntroducerName;
    }

    // TODO
    @Override public void onItemClick(ManageListItem item) {
      ForgetIntroducerDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), getIntroducerName(item), item.getDate(), forgetHandler, type);
    }

    @Override public void onItemLongClick(ManageListItem item) {
      DeleteIntroductionDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), getIntroducerName(item), item.getDate(), deleteHandler);
    }
  }

  public interface onAllNavigationClicked{
    public void goToAll();
  }
}
