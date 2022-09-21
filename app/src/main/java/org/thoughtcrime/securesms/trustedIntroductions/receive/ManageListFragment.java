package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
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

public class ManageListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener, DeleteIntroductionDialog.DeleteIntroduction{

  // TODO: Will probably need that for all screen
  private ProgressWheel showIntroductionsProgress;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private RecyclerView introductionList;
  private ManageActivity.IntroductionScreenType type;
  private String introducerName;

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
   * @param t which type of management screen this fragment was created from.
   */
  public void setScreenState(@NonNull ManageViewModel viewModel, @NonNull ManageActivity.IntroductionScreenType t, @Nullable String introducerName){
    this.introducerName = introducerName;
    type = t;
    this.viewModel = viewModel;
    initializeAdapter(t);
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), users -> {
      List<TI_Data> filtered = getFiltered(users, null);
      adapter.submitList(new ArrayList<>(filtered));
    });
  }

  private void initializeAdapter(ManageActivity.IntroductionScreenType t){
    if(t == ManageActivity.IntroductionScreenType.RECIPIENT_SPECIFIC){
      adapter = new ManageAdapter(requireContext(), new IntroductionClickListener(this::deleteIntroduction));
    } else {
      // TODO: all adapter has different list item layouts + a sticky header.
    }
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

  @Override public void deleteIntroduction(@NonNull Long introductionId) {
    viewModel.deleteIntroduction(introductionId);
  }

  private class IntroductionClickListener implements ManageAdapter.ItemClickListener {

    DeleteIntroductionDialog.DeleteIntroduction f;

    public IntroductionClickListener(DeleteIntroductionDialog.DeleteIntroduction f){
      this.f = f;
    }

    // TODO
    @Override public void onItemClick(ManageListItem item) {
      // Forget Introducer Dialogue
      // cancel, ok
      // -> triggers callback in fragment which forwards task to viewModel (calls Database delete in a different thread)
    }

    @Override public void onItemLongClick(ManageListItem item) {
      Context c = requireContext();
      String itemIntroducerName;
      if(introducerName == null){
        // All screen
        itemIntroducerName = item.getIntroducerName(c);
        // could still be null after iff this introducer information has been cleared.
        itemIntroducerName = (itemIntroducerName == null) ? "forgotten introducer": itemIntroducerName;
      } else {
        itemIntroducerName = introducerName;
      }
      DeleteIntroductionDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), itemIntroducerName, item.getDate(), f);
    }
  }
}
