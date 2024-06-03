package org.thoughtcrime.securesms.trustedIntroductions.send;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;

public class ContactsSelectionListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ContactsSelectionListFragment.class));

  // TODO: have a progress wheel for more substantial data? (cosmetic, not super important)
  private ProgressWheel              showContactsProgress;
  private ContactsSelectionViewModel viewModel;
  private ContactsSelectionAdapter   TIRecyclerViewAdapter;
  private RecyclerView                  TIContactsRecycler;
  private RecyclerView                                           chipRecycler;
  private MappingAdapter contactChipAdapter;


  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.ti_contact_selection_fragment, container, false);

    TIContactsRecycler   = view.findViewById(R.id.recycler_view);
    chipRecycler = view.findViewById(R.id.chipRecycler);

    TIContactsRecycler.setItemAnimator(null);

    contactChipAdapter = new MappingAdapter();
    // TODO, can I just use their selected contacts class? if this breaks, build your own
    SelectedTIContacts.register(contactChipAdapter, this::onChipCloseIconClicked);
    chipRecycler.setAdapter(contactChipAdapter);

    // Default values for now
    boolean recyclerViewClipping  = true;

    TIContactsRecycler.setClipToPadding(recyclerViewClipping);

    // Register checkbox behavior
    TIContactsRecycler.getViewTreeObserver().addOnPreDrawListener(this::restoreCheckboxState);

    return view;
  }

  /**
   * Called by activity containing the Fragment.
   * @param viewModel The underlying persistent data storage (throughout Activity and Fragment Lifecycle).
   */
  public void setViewModel(ContactsSelectionViewModel viewModel){
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
    // Not directly passing a cursor, instead submitting a list to ContactsAdapter
    TIRecyclerViewAdapter = new ContactsSelectionAdapter(requireContext(), new ContactClickListener());

    TIContactsRecycler.setAdapter(TIRecyclerViewAdapter);
  }

  @MainThread
  @CallSuper
  public void onViewStateRestored(@Nullable Bundle savedInstanceState){
    super.onViewStateRestored(savedInstanceState);
    loadSelection();
  }

  /**
   * Saved state to be restored from viewModel.
   */
  private void loadSelection(){
    if(this.viewModel != null) {
      updateChips();
      restoreCheckboxState();
    } // Do nothing if viewModel is null, should never happen
  }

  private boolean restoreCheckboxState(){
    for (SelectedTIContacts.Model model : this.viewModel.listSelectedContactModels()) {
      RecipientId selected = model.getRecipientId();
      for (int i = 0; i < TIContactsRecycler.getChildCount(); i++) {
        ContactsSelectionAdapter.TIContactViewHolder item = ((ContactsSelectionAdapter.TIContactViewHolder) TIContactsRecycler.getChildViewHolder(TIContactsRecycler.getChildAt(i)));
        if (item.getRecipientId().equals(selected)) {
          item.setCheckboxChecked(true);
          break;
        }
      }
    }
    return true;
  }

  private List<Recipient> getFiltered(List<Recipient> contacts, @Nullable String filter){
    List<Recipient> filtered = new ArrayList<>(contacts);
    filter = (filter==null)? Objects.requireNonNull(viewModel.getFilter().getValue()): filter;
    if (!filter.isEmpty() && filter.compareTo("") != 0){
      for (Recipient c: contacts) {
        // Choose appropriate string representation
        Pattern filterPattern = Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE);
        if(!filterPattern.matcher(c.getDisplayName(requireContext())).find() &&
           !filterPattern.matcher(c.getE164().orElse("")).find()){
          filtered.remove(c);
        }
      }
    }
    return filtered;
  }

  @Override public void onFilterChanged(String filter) {
    viewModel.setQueryFilter(filter);
    TIRecyclerViewAdapter.submitList(getFiltered(viewModel.getContacts().getValue(), filter));
  }

  private class ContactClickListener implements ContactsSelectionAdapter.ItemClickListener {

    @Override public void onItemClick(ContactsSelectionAdapter.TIContactViewHolder item) {
      if (viewModel.isSelectedContact(item.getRecipient())) {
        markContactUnselected(item.getRecipient());
        item.setCheckboxChecked(false);
      } else {
        markContactSelected(item.getRecipient());
        item.setCheckboxChecked(true);
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
    contactChipAdapter.submitList(new MappingModelList(viewModel.listSelectedContactModels()), this::smoothScrollChipsToEnd);
    int selectedCount = viewModel.getSelectedContactsCount();
    if (selectedCount == 0) {
      setChipGroupVisibility(ConstraintSet.GONE);
    } else {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }
  }

  private Unit onChipCloseIconClicked(SelectedTIContacts.Model m) {
    markContactUnselected(m.getSelectedContact());
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
    if(viewModel.removeSelectedContact(selectedContact) < 0){
      Log.w(TAG, String.format(Locale.US,"%s could not be removed from selection!", selectedContact));
    } else {
      Log.i(TAG, String.format(Locale.US,"%s was removed from selection.", selectedContact));
      updateChips();
    }
  }

  private void markContactSelected(@NonNull Recipient selectedContact) {
    if(!viewModel.addSelectedContact(selectedContact)){
      Log.i(TAG, String.format("Contact %s was already part of the selection.", selectedContact));
    } else {
      updateChips();
    }
  }

}
