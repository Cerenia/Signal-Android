package org.thoughtcrime.securesms.trustedIntroductions;

import android.animation.LayoutTransition;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.chip.ChipGroup;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.components.recyclerview.ToolbarShadowAnimationHelper;
import org.thoughtcrime.securesms.contacts.ContactChip;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;

/**
 * In order to keep the tight coupling to a minimum, such that we can continue syncing against the upstream repo as it evolves, we opted to
 * copy some of the code instead of adapting the originals in the repo, which would more readily lead to merge conflicts down the line.
 * This is an adaptation of ContactSelectionListFragment, but it's always a multiselect and the data is loaded from an external cursor
 * instead of using DisplayMode.
 */
public class IntroductionContactsSelectionListFragment extends Fragment {//implements Observer {

  private static final String TAG = Log.tag(IntroductionContactsSelectionListFragment.class);

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 1;
  private static final int CHIP_GROUP_REVEAL_DURATION_MS = 150;

  public static final String RECENTS           = "recents";
  public static final String DISPLAY_CHIPS     = "display_chips";

  private View          showContactsLayout;
  private   Button        showContactsButton;
  private   TextView      showContactsDescription;
  private   ProgressWheel showContactsProgress;
  private   TextView      emptyText;
  private ConstraintLayout constraintLayout;
  private TrustedIntroductionContactsViewModel viewModel;
  private IntroducableContactsAdapter          TIRecyclerViewAdapter;
  private RecyclerView                         recyclerView;
  private String                                                   searchFilter;
  private ChipGroup                                                    chipGroup;
  private   HorizontalScrollView                                         chipGroupScrollContainer;
  private ContactSelectionListFragment.OnSelectionLimitReachedListener onSelectionLimitReachedListener;
  private SelectionLimits                                              selectionLimit = SelectionLimits.NO_LIMITS;
  private View                                        shadowView;
  private ToolbarShadowAnimationHelper                toolbarShadowAnimationHelper;
  private RecipientId recipientId;


  private GlideRequests    glideRequests;
  private           RecyclerViewFastScroller fastScroller;
  @Nullable private FixedViewsAdapter        headerAdapter;
  @Nullable private   ContactSelectionListFragment.ListCallback   listCallback;
  @Nullable private ContactSelectionListFragment.ScrollCallback scrollCallback;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    //emptyText                = view.findViewById(R.id.ti_no_contacts);
    emptyText                = view.findViewById(R.id.empty);
    recyclerView             = view.findViewById(R.id.recycler_view);
    fastScroller             = view.findViewById(R.id.fast_scroller);
    showContactsLayout       = view.findViewById(R.id.show_contacts_container);
    showContactsButton       = view.findViewById(R.id.show_contacts_button);
    showContactsDescription  = view.findViewById(R.id.show_contacts_description);
    showContactsProgress     = view.findViewById(R.id.progress);
    chipGroup                = view.findViewById(R.id.chipGroup);
    chipGroupScrollContainer = view.findViewById(R.id.chipGroupScrollContainer);
    constraintLayout         = view.findViewById(R.id.container);
    shadowView               = view.findViewById(R.id.toolbar_shadow);

    toolbarShadowAnimationHelper = new ToolbarShadowAnimationHelper(shadowView);

    recyclerView.addOnScrollListener(toolbarShadowAnimationHelper);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    recyclerView.setItemAnimator(new DefaultItemAnimator() {
      @Override
      public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
        return true;
      }
    });

    Intent intent    = requireActivity().getIntent();
    Bundle arguments = safeArguments();

    // Default values for now
    int     recyclerViewPadBottom = -1;
    boolean recyclerViewClipping  = true;

    /*if (recyclerViewPadBottom != -1) {
      ViewUtil.setPaddingBottom(recyclerView, recyclerViewPadBottom);
    }*/

    recyclerView.setClipToPadding(recyclerViewClipping);

    // TODO: Is it correct to initialize this in Activity instead?
    //TrustedIntroductionContactsViewModel.Factory factory = new TrustedIntroductionContactsViewModel.Factory(recipientId);
    //viewModel = new ViewModelProvider(this, factory).get(TrustedIntroductionContactsViewModel.class);
    //viewModel.getContacts().observe(getViewLifecycleOwner(), users -> {
    //  cursorRecyclerViewAdapter.submitList(users);
    //});

    return view;
  }

  @MainThread
  @CallSuper
  public void onViewStateRestored(@Nullable Bundle savedInstanceState){
    // TODO: Why is the state here only INITIALIZED?
    super.onViewStateRestored(savedInstanceState);
    loadSelection();
  }

  public void setRecipientId(RecipientId id){
    this.recipientId = id;
  }

  /**
   * Called by activity containing the Fragment.
   * @param viewModel The underlying persistent data storage.
   */
  public void setViewModel(TrustedIntroductionContactsViewModel viewModel){
    this.viewModel = viewModel;
    this.viewModel.getContacts().observe(getViewLifecycleOwner(), users -> {
      TIRecyclerViewAdapter.submitList(users);
    });
    // Do all the things you can do when the viewModel has been created.
    initializeAdapter();
  }

  private @NonNull Bundle safeArguments() {
    return getArguments() != null ? getArguments() : new Bundle();
  }

  public @NonNull List<SelectedContact> getSelectedContacts() {
    if (TIRecyclerViewAdapter == null) {
      return Collections.emptyList();
    }

    return TIRecyclerViewAdapter.getSelectedContacts();
  }

  public int getSelectedContactsCount() {
    if (TIRecyclerViewAdapter == null) {
      return 0;
    }

    return TIRecyclerViewAdapter.getSelectedContactsCount();
  }


  private void initializeAdapter() {
    glideRequests = GlideApp.with(this);
    // Not directly passing a cursor, instead submitting a list to ContactsAdapter
    TIRecyclerViewAdapter = new IntroducableContactsAdapter(requireContext(),
                                                            glideRequests,
                                                            this.viewModel,
                                                            new ListClickListener());

    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();

    // TODO: needed?
    /*
    if (listCallback != null) {
      headerAdapter = new FixedViewsAdapter(createNewGroupItem(listCallback));
      headerAdapter.hide();
      concatenateAdapter.addAdapter(headerAdapter);
    }*/

    concatenateAdapter.addAdapter(TIRecyclerViewAdapter);

    // TODO: needed?
    /*
    if (listCallback != null) {
      footerAdapter = new FixedViewsAdapter(createInviteActionView(listCallback));
      footerAdapter.hide();
      concatenateAdapter.addAdapter(footerAdapter);
    }*/

    recyclerView.addItemDecoration(new LetterHeaderDecoration(requireContext(), this::hideLetterHeaders));
    recyclerView.setAdapter(concatenateAdapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          if (scrollCallback != null) {
            scrollCallback.onBeginScroll();
          }
        }
      }
    });

    //if (TIRecyclerViewAdapter != null) {
     // TIRecyclerViewAdapter.onSelectionChanged();
    //}
  }

  /**
   * Saved state to be restored from viewModel.
   */
  private void loadSelection(){
    if(this.viewModel != null){
      List<SelectedContact> selection = this.viewModel.listSelectedContacts();
      for(SelectedContact current: selection){
        addChipForSelectedContact(current);
      }
    } // should never happen, but if ViewModel does not exist, don't load anything.
  }

  public void setQueryFilter(String filter) {
    this.searchFilter = filter;
    viewModel.setFilter(filter);
  }

  private boolean hideLetterHeaders() {
    return hasQueryFilter() || shouldDisplayRecents();
  }

  // TODO: needed?
  public boolean hasQueryFilter() {
    return !TextUtils.isEmpty(searchFilter);
  }

  private boolean shouldDisplayRecents() {
    return safeArguments().getBoolean(RECENTS, requireActivity().getIntent().getBooleanExtra(RECENTS, false));
  }

  /**
   * Observer callback for live Data.
   * @param o
   */
  /**@Override public void onChanged(Object o) {
      cursorRecyclerViewAdapter.submitList((LiveData<List>)o);
  }*/


  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {
      SelectedContact selectedContact = contact.isUsernameType() ? SelectedContact.forUsername(contact.getRecipientId().orNull(), contact.getNumber())
                                                                 : SelectedContact.forPhone(contact.getRecipientId().orNull(), contact.getNumber());
      if (TIRecyclerViewAdapter.isSelectedContact(selectedContact)) {
        markContactUnselected(selectedContact);
      } else {
        markContactSelected(selectedContact);
      }

    }
  }

  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  public interface OnContactSelectedListener {
    void onContactSelected(Optional<RecipientId> recipientId, @Nullable String number);
  }

  private boolean selectionHardLimitReached() {
    return getChipCount() >= selectionLimit.getHardLimit();
  }

  private boolean selectionWarningLimitReachedExactly() {
    return getChipCount() == selectionLimit.getRecommendedLimit();
  }

  private int getChipCount() {
    int count = chipGroup.getChildCount() - CHIP_GROUP_EMPTY_CHILD_COUNT;
    if (count < 0) throw new AssertionError();
    return count;
  }

  private void registerChipRecipientObserver(@NonNull ContactChip chip, @Nullable LiveRecipient recipient) {
    if (recipient != null) {
      recipient.observe(getViewLifecycleOwner(), resolved -> {
        if (chip.isAttachedToWindow()) {
          chip.setAvatar(glideRequests, resolved, null);
          chip.setText(resolved.getShortDisplayName(chip.getContext()));
        }
      });
    }
  }

  private void markContactSelected(@NonNull SelectedContact selectedContact) {
    TIRecyclerViewAdapter.addSelectedContact(selectedContact);
    addChipForSelectedContact(selectedContact);
  }

  private void addChipForSelectedContact(@NonNull SelectedContact selectedContact) {
    // TODO: This change made the chips appear correctly when restoring state from the ViewModel. Was this a bug in the first place?
    // or have I broken some other things through this change?
    //Lifecycle state = getViewLifecycleOwner().getLifecycle();
    Lifecycle state = getLifecycle();
    SimpleTask.run(state,
                   ()       -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                   resolved -> addChipForRecipient(resolved, selectedContact));
  }

  private void addChipForRecipient(@NonNull Recipient recipient, @NonNull SelectedContact selectedContact) {
    final ContactChip chip = new ContactChip(requireContext());

    if (getChipCount() == 0) {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }

    chip.setText(recipient.getShortDisplayName(requireContext()));
    chip.setContact(selectedContact);
    chip.setCloseIconVisible(true);
    chip.setOnCloseIconClickListener(view -> {
      markContactUnselected(selectedContact);
    });

    chipGroup.getLayoutTransition().addTransitionListener(new LayoutTransition.TransitionListener() {
      @Override
      public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
      }

      @Override
      public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
        if (getView() == null || !requireView().isAttachedToWindow()) {
          Log.w(TAG, "Fragment's view was detached before the animation completed.");
          return;
        }

        if (view == chip && transitionType == LayoutTransition.APPEARING) {
          chipGroup.getLayoutTransition().removeTransitionListener(this);
          registerChipRecipientObserver(chip, recipient.live());
          chipGroup.post(IntroductionContactsSelectionListFragment.this::smoothScrollChipsToEnd);
        }
      }
    });

    chip.setAvatar(glideRequests, recipient, () -> addChip(chip));
  }

  private void addChip(@NonNull ContactChip chip) {
    chipGroup.addView(chip);
    if (selectionWarningLimitReachedExactly()) {
      if (onSelectionLimitReachedListener != null) {
        onSelectionLimitReachedListener.onSuggestedLimitReached(selectionLimit.getRecommendedLimit());
      } else {
        GroupLimitDialog.showRecommendedLimitMessage(requireContext());
      }
    }
  }

  private void setChipGroupVisibility(int visibility) {
    if (!safeArguments().getBoolean(DISPLAY_CHIPS, requireActivity().getIntent().getBooleanExtra(DISPLAY_CHIPS, true))) {
      return;
    }

    TransitionManager.beginDelayedTransition(constraintLayout, new AutoTransition().setDuration(CHIP_GROUP_REVEAL_DURATION_MS));

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(constraintLayout);
    constraintSet.setVisibility(R.id.chipGroupScrollContainer, visibility);
    constraintSet.applyTo(constraintLayout);
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipGroupScrollContainer) ? chipGroup.getWidth() : 0;
    chipGroupScrollContainer.smoothScrollTo(x, 0);
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    TIRecyclerViewAdapter.removeFromSelectedContacts(selectedContact);
    TIRecyclerViewAdapter.notifyItemRangeChanged(0, TIRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
    removeChipForContact(selectedContact);
  }

  private void removeChipForContact(@NonNull SelectedContact contact) {
    for (int i = chipGroup.getChildCount() - 1; i >= 0; i--) {
      View v = chipGroup.getChildAt(i);
      if (v instanceof ContactChip && contact.matches(((ContactChip) v).getContact())) {
        chipGroup.removeView(v);
      }
    }

    if (getChipCount() == 0) {
      setChipGroupVisibility(ConstraintSet.GONE);
    }
  }
}
