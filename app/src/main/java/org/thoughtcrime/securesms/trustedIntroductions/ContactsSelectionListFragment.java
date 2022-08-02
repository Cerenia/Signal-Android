package org.thoughtcrime.securesms.trustedIntroductions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.SelectedContacts;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import kotlin.Unit;

/**
 * In order to keep the tight coupling to a minimum, such that we can continue syncing against the upstream repo as it evolves, we opted to
 * copy some of the code instead of adapting the originals in the repo, which would more readily lead to merge conflicts down the line.
 * This is an adaptation of ContactSelectionListFragment, but it's always a multiselect and the data is loaded from an external cursor
 * instead of using DisplayMode.
 */
public class ContactsSelectionListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener {

  private static final String TAG = Log.tag(ContactsSelectionListFragment.class);

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 1;
  private static final int CHIP_GROUP_REVEAL_DURATION_MS = 150;

  public static final String DISPLAY_CHIPS     = "display_chips";

  // TODO: have a progress wheel for more substantial data? (cosmetic, not super important)
  private ProgressWheel                        showContactsProgress;
  private LinearLayout                         linearLayout;
  //private TrustedIntroductionContactsViewModel viewModel;
  private MinimalViewModel viewModel;
  private MinimalAdapter                            TIRecyclerViewAdapter;
  private RecyclerView                                           TIContactsRecycler;
  private RecyclerView                                           chipRecycler;
  private MappingAdapter contactChipAdapter;

  private GlideRequests    glideRequests;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Reusing this layout, many components in it are never referenced for TI purposes.
    Log.e(TAG, "BlubBlub");
    View view = inflater.inflate(R.layout.ti_contact_selection_fragment, container, false);

    TIContactsRecycler   = view.findViewById(R.id.recycler_view);
    chipRecycler = view.findViewById(R.id.chipRecycler);
    linearLayout = view.findViewById(R.id.container);

    LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
    layoutManager.setOrientation(LinearLayoutManager.VERTICAL); // TODO: Still more fiddling..
    TIContactsRecycler.setLayoutManager(layoutManager);
    // TODO
    /*TIContactsRecycler.setItemAnimator(new DefaultItemAnimator() {
      @Override
      public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
      }
    });*/
    TIContactsRecycler.setItemAnimator(null);

    contactChipAdapter = new MappingAdapter();
    // TODO, can I just use their selected contacts class? if this breaks, build your own
    SelectedContacts.register(contactChipAdapter, this::onChipCloseIconClicked);
    chipRecycler.setAdapter(contactChipAdapter);

    // TODO
    //Disposable disposable = contactChipViewModel.getState().subscribe(this::handleSelectedContactsChanged);

    // Default values for now
    boolean recyclerViewClipping  = true;

    TIContactsRecycler.setClipToPadding(recyclerViewClipping);

    return view;
  }


  @MainThread
  @CallSuper
  public void onViewStateRestored(@Nullable Bundle savedInstanceState){
    super.onViewStateRestored(savedInstanceState);
    loadSelection();
  }

  /**
   * Called by activity containing the Fragment.
   * @param viewModel The underlying persistent data storage (throughout Activity and Fragment Lifecycle).
   */
  public void setViewModel(MinimalViewModel viewModel){
    this.viewModel = viewModel;
    initializeAdapter();
    this.viewModel.getContacts().observe(getViewLifecycleOwner(), users -> {
      List<Recipient> filtered = getFiltered(users, null);
      TIRecyclerViewAdapter.submitList(new ArrayList<>(filtered));
    });
  }

  private @NonNull Bundle safeArguments() {
    return getArguments() != null ? getArguments() : new Bundle();
  }

  private void initializeAdapter() {
    glideRequests = GlideApp.with(this);
    // Not directly passing a cursor, instead submitting a list to ContactsAdapter
    /*TIRecyclerViewAdapter = new IntroducableContactsAdapter(requireContext(),
                                                            glideRequests,
                                                            new ListClickListener());*/
    TIRecyclerViewAdapter = new MinimalAdapter(requireContext(), glideRequests, new StringListClickListener());

    TIContactsRecycler.setAdapter(TIRecyclerViewAdapter);
    TIContactsRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          // TODO: Add scrollCallback if needed
          // => will be figured out with unittests and injection of fake long list of introducable contacts.
          /*if (scrollCallback != null) {
            scrollCallback.onBeginScroll();
          }*/
        }
      }
    });
  }

  /**
   * Saved state to be restored from viewModel.
   */
  private void loadSelection(){
    if(this.viewModel != null){
      updateChips();
    } // should never happen, but if ViewModel does not exist, don't load anything.
  }

  // TODO: Unhappy that this is here and not in the viewmodel. But the display or username is context dependant so not sure how/if to decouple.
  private List<Recipient> getFiltered(List<Recipient> contacts, @Nullable String filter){
    List<Recipient> filtered = new ArrayList<>(contacts);
    filter = (filter==null)? Objects.requireNonNull(viewModel.getFilter().getValue()): filter;
    if (!filter.isEmpty() && filter.compareTo("") != 0){
      for (Recipient c: contacts) {
        // Choose appropriate string representation
        if(!c.getDisplayNameOrUsername(requireContext()).contains(filter)){
          filtered.remove(c);
        }
      }
    }
    return filtered;
  }

  @Override public void onFilterChanged(String filter) {
    viewModel.setQueryFilter(filter);
    //TIRecyclerViewAdapter.submitList(getFiltered(viewModel.getContacts().getValue(), filter)); // TODO
  }


  private class StringListClickListener implements MinimalAdapter.ItemClickListener {

    @Override public void onItemClick(MinimalContactSelectionListItem item) {
      if (viewModel.isSelectedContact(item.getRecipient())) {
        markContactUnselected(item.getRecipient());
      } else {
        markContactSelected(item.getRecipient());
      }
    }
  }

  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  interface OnContactSelectedListener {
    void onContactSelected(Optional<RecipientId> recipientId, @Nullable String number);
  }

  private void updateChips() {
    contactChipAdapter.submitList(new MappingModelList(viewModel.listSelectedContacts()), this::smoothScrollChipsToEnd);
    int selectedCount = viewModel.getSelectedContactsCount();
    if (selectedCount == 0) {
      setChipGroupVisibility(ConstraintSet.GONE);
    } else {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }
  }

  private Unit onChipCloseIconClicked(SelectedContacts.Model m) {
    markContactUnselected(m.getRecipient());
    return null;
  }

  private void setChipGroupVisibility(int visibility) {
    chipRecycler.setVisibility(visibility);
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipRecycler) ? chipRecycler.getWidth() : 0;
    chipRecycler.smoothScrollBy(x, 0);
  }

  private void markContactUnselected(@NonNull Recipient selectedContact) {
    int removed = viewModel.removeSelectedContact(selectedContact);
    if(removed < 0){
      Log.w(TAG, String.format(Locale.US,"%s could not be removed from selection!", selectedContact));
    } else {
      Log.i(TAG, String.format(Locale.US,"%s was removed from selection.", selectedContact));
      // TODO: I used to call TIRecycleViewAdapter.NotifyItemRanceChanged, still necessary?
      updateChips();
    }
  }

  private void markContactSelected(@NonNull Recipient selectedContact) {
    boolean added = viewModel.addSelectedContact(selectedContact);
    if(!added){
      Log.i(TAG, String.format("Contact %s was already part of the selection.", selectedContact));
    } else {
      updateChips();
      // TODO: I used to call TIRecycleViewAdapter.NotifyItemRanceChanged, still necessary?
    }
  }

}
