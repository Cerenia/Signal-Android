package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
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
import java.util.function.Consumer;

import kotlin.Unit;

/**
 * In order to keep the tight coupling to a minimum, such that we can continue syncing against the upstream repo as it evolves, we opted to
 * copy some of the code instead of adapting the originals in the repo, which would more readily lead to merge conflicts down the line.
 * This is an adaptation of ContactSelectionListFragment, but it's always a multiselect and the data is loaded from an external cursor
 * instead of using DisplayMode.
 */
public class IntroductionContactsSelectionListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener {

  private static final String TAG = Log.tag(IntroductionContactsSelectionListFragment.class);

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
    /*TIContactsRecycler.setItemAnimator(new DefaultItemAnimator() {
      @Override
      public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
      }
    });*/
    TIContactsRecycler.setItemAnimator(null);

    //contactChipViewModel = new ViewModelProvider(this).get(ContactChipViewModel.class);
    contactChipViewModel = new ViewModelProvider(this).get(MinimalChipViewModel.class);
    contactChipAdapter   = new MappingAdapter();

    SelectedStrings.register(contactChipAdapter, this::onChipCloseIconClicked);
    chipRecycler.setAdapter(contactChipAdapter);

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
    // Observe both mutable data sources
    this.viewModel.getContacts().observe(getViewLifecycleOwner(), users -> {
      //List<Recipient> filtered = getFiltered(users, null);
      //TIRecyclerViewAdapter.submitList(new ArrayList<>(filtered));
      //ArrayList<String> list = new ArrayList<String>();
      //for(int i = 1; i < 11; i++){
       // list.add("Recipient: " + i);
      //}
      //TIRecyclerViewAdapter.submitList(list);
      TIRecyclerViewAdapter.submitList(users);
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
    // TODO: Trying to get this stupid list to update.. (requestLayout() was breaking in function triggerUpdateProcessor() of RecyclerView) => Still doesn't work.
    //recyclerView.setHasFixedSize(true);
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
      List<String> selection = this.viewModel.listSelectedContacts();
      for(String current: selection){
        addChipForSelectedContact(current);
      }
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

    @Override public void onItemClick(ListItemSimple item) {
      if (viewModel.isSelectedContact(item.get())) {
        markContactUnselected(item.get());
      } else {
        markContactSelected(item.get());
      }
    }

  }

  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  /**
  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {
      String selectedContact = contact.isUsernameType() ? SelectedContact.forUsername(contact.getRecipientId().orElse(null), contact.getNumber())
                                                                 : SelectedContact.forPhone(contact.getRecipientId().orElse(null), contact.getNumber());
      if (viewModel.isSelectedContact(selectedContact)) {
        markContactUnselected(selectedContact);
      } else {
        markContactSelected(selectedContact);
      }
    }
  }
**/

  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  interface OnContactSelectedListener {
    void onContactSelected(Optional<RecipientId> recipientId, @Nullable String number);
  }

  /**
  private void addChipForSelectedContact(@NonNull SelectedContact selectedContact) {
    // TODO: This change made the chips appear correctly when restoring state from the ViewModel. Was this a bug in the first place?
    // or have I broken some other things through this change?
    //Lifecycle state = getViewLifecycleOwner().getLifecycle();
    Lifecycle state = getLifecycle();
    SimpleTask.run(state,
                   ()       -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                   resolved -> contactChipViewModel.add(selectedContact));
  }

  private void addChipForSelectedContact(@NonNull SelectedContact selectedContact) {
    // TODO: This change made the chips appear correctly when restoring state from the ViewModel. Was this a bug in the first place?
    // or have I broken some other things through this change?
    //Lifecycle state = getViewLifecycleOwner().getLifecycle();
    Lifecycle state = getLifecycle();
    SimpleTask.run(state,
                   ()       -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                   resolved -> contactChipViewModel.add(selectedContact));
  }
   private Unit onChipCloseIconClicked(SelectedContacts.Model model) {
   markContactUnselected(model.getSelectedContact());
   if (onContactSelectedListener != null) {
   onContactSelectedListener.onContactDeselected(Optional.of(model.getRecipient().getId()), model.getRecipient().getE164().orElse(null));
   }

   return Unit.INSTANCE;
   }
   **/

  private void addChipForSelectedContact(@NonNull String selectedContact) {

    setChipGroupVisibility(View.VISIBLE);
  }

  private Unit onChipCloseIconClicked(SelectedStrings.Model m) {
    markContactUnselected(m.getSelectedString().getName());
    return null;
  }

  private void setChipGroupVisibility(int visibility) {
    if (!safeArguments().getBoolean(DISPLAY_CHIPS, requireActivity().getIntent().getBooleanExtra(DISPLAY_CHIPS, true))) {
      return;
    }

    /*
    TransitionManager.beginDelayedTransition(linearLayout, new AutoTransition().setDuration(CHIP_GROUP_REVEAL_DURATION_MS));


    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(linearLayout);
    constraintSet.setVisibility(R.id.chipRecycler, visibility);
    constraintSet.applyTo(linearLayout);
    TODO
     */
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipRecycler) ? chipRecycler.getWidth() : 0;
    chipRecycler.smoothScrollBy(x, 0);
  }

  private void markContactUnselected(@NonNull String selectedContact) {
    int removed = viewModel.removeFromSelectedContacts(selectedContact);
    if(removed <= 0){
      Log.w(TAG, String.format(Locale.US,"%s could not be removed from selection!", selectedContact.toString()));
    } else {
      Log.i(TAG, String.format(Locale.US,"%d contact(s) were removed from selection.", removed));
      contactChipViewModel.remove(selectedContact);
      viewModel.removeFromSelectedContacts(selectedContact);
      TIRecyclerViewAdapter.notifyItemRangeChanged(0, TIRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
      if (contactChipViewModel.numberOfChips() == 0){
        chipRecycler.setVisibility(View.GONE);
      }
    }
  }

  private void markContactSelected(@NonNull String selectedContact) {
    boolean added = viewModel.addSelectedContact(selectedContact);
    if(!added){
      Log.i(TAG, String.format("Contact %s was already part of the selection.", selectedContact.toString()));
    } else {
      addChipForSelectedContact(selectedContact);
      viewModel.addSelectedContact(selectedContact);
      TIRecyclerViewAdapter.notifyItemRangeChanged(0, TIRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
    }
  }

  /**
  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    int removed = viewModel.removeFromSelectedContacts(selectedContact);
    if(removed <= 0){
      Log.w(TAG, String.format(Locale.US,"%s could not be removed from selection!", selectedContact.toString()));
    } else {
      Log.i(TAG, String.format(Locale.US,"%d contact(s) were removed from selection.", removed));
      TIRecyclerViewAdapter.notifyItemRangeChanged(0, TIRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
      contactChipViewModel.remove(selectedContact);
    }
  }

  private void markContactSelected(@NonNull SelectedContact selectedContact) {
    boolean added = viewModel.addSelectedContact(selectedContact);
    if(!added){
      Log.i(TAG, String.format("Contact %s was already part of the selection.", selectedContact.toString()));
    } else {
      addChipForSelectedContact(selectedContact);
    }
  }**/

  private void handleSelectedContactsChanged(@NonNull List<SelectedContacts.Model> selectedContacts) {
    contactChipAdapter.submitList(new MappingModelList(selectedContacts), this::smoothScrollChipsToEnd);

    if (selectedContacts.isEmpty()) {
      setChipGroupVisibility(ConstraintSet.GONE);
    } else {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }
  }
}
