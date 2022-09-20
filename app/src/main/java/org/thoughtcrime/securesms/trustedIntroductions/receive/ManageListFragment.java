package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class ManageListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener {

  // TODO: Will probably need that for all screen
  private ProgressWheel showIntroductionsProgress;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private RecyclerView introductionList;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
    View view = inflater.inflate(R.layout.ti_manage_fragment, container, false);
    introductionList = view.findViewById(R.id.recycler_view);
    introductionList.setClipToPadding(true);
    // Iff some state restauration is necessary, add an onPreDrawListener to the recycle view @see ContactsSelectionListFragment
    return view;
  }

  /**
   * Called by activity containing the Fragment.
   * @param viewModel The underlying persistent data storage (throughout Activity and Fragment Lifecycle).
   */
  public void setViewModel(ManageViewModel viewModel){
    this.viewModel = viewModel;
    initializeAdapter();
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), users -> {
      List<TI_Data> filtered = getFiltered(users, null);
      adapter.submitList(new ArrayList<>(filtered));
    });
  }

  private void initializeAdapter(){
    adapter = new ManageAdapter(requireContext(), new IntroductionClickListener());
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

  private List<TI_Data> getFiltered(List<TI_Data> introductions, @Nullable String filter){
    List<TI_Data> filtered = new ArrayList<>(introductions);
    filter = (filter==null) ? Objects.requireNonNull(viewModel.getFilter().getValue()) : filter;
    if(!filter.isEmpty() && filter.compareTo("") != 0){
      Pattern filterPattern = Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE);
      for (TI_Data d: introductions){
        if (!filterPattern.matcher(INTRODUCTION_DATE_PATTERN.format(d.getTimestamp())).find() &&
            !filterPattern.matcher(d.getIntroduceeName()).find() &&
            !filterPattern.matcher(d.getIntroduceeNumber()).find() &&
            !filterPattern.matcher(d.getState().toString()).find()){
          filtered.remove(d);
        }
      }
    }
    return filtered;
  }

  @Override public void onFilterChanged(String filter) {
    viewModel.setQueryFilter(filter);
    adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), filter));
  }

  private class IntroductionClickListener implements ManageAdapter.ItemClickListener {

    // TODO
    @Override public void onItemClick(ManageListItem item) {

    }

    @Override public void onItemLongClick(ManageListItem item) {

    }
  }
}
