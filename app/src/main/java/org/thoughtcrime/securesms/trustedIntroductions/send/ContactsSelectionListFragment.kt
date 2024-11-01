package org.thoughtcrime.securesms.trustedIntroductions.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pnikosis.materialishprogress.ProgressWheel
import org.signal.core.util.logging.Log.i
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.logging.Log.w
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionAdapter.TIContactViewHolder
import org.thoughtcrime.securesms.trustedIntroductions.send.SelectedTIContacts.register
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import java.util.Locale
import java.util.Objects
import java.util.Optional
import java.util.regex.Pattern

/**
 * In order to keep the tight coupling to a minimum, such that we can continue syncing against the upstream repo as it evolves, we opted to
 * copy some of the code instead of adapting the originals in the repo, which would more readily lead to merge conflicts down the line.
 * This is an adaptation of ContactSelectionListFragment, but it's always a multiselect and the data is loaded from an external cursor
 * instead of using DisplayMode.
 */
class ContactsSelectionListFragment : Fragment(), ContactFilterView.OnFilterChangedListener {
  // TODO: have a progress wheel for more substantial data? (cosmetic, not super important)
  private val showContactsProgress: ProgressWheel? = null
  private var viewModel: ContactsSelectionViewModel? = null
  private var TIRecyclerViewAdapter: ContactsSelectionAdapter? = null
  private var TIContactsRecycler: RecyclerView? = null
  private var chipRecycler: RecyclerView? = null
  private var contactChipAdapter: MappingAdapter? = null


  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val view = inflater.inflate(R.layout.ti_contact_selection_fragment, container, false)

    TIContactsRecycler = view.findViewById(R.id.recycler_view)
    chipRecycler = view.findViewById(R.id.chipRecycler)

    if (TIContactsRecycler == null || chipRecycler == null) {
      return view;
    }

    TIContactsRecycler!!.setItemAnimator(null)

    contactChipAdapter = MappingAdapter()
    // TODO, can I just use their selected contacts class? if this breaks, build your own
    register(contactChipAdapter!!) { m: SelectedTIContacts.Model -> this.onChipCloseIconClicked(m) }
    chipRecycler!!.setAdapter(contactChipAdapter)

    // Default values for now
    val recyclerViewClipping = true

    TIContactsRecycler!!.setClipToPadding(recyclerViewClipping)

    // Register checkbox behavior
    TIContactsRecycler!!.getViewTreeObserver().addOnPreDrawListener { this.restoreCheckboxState() }

    return view
  }

  /**
   * Called by activity containing the Fragment.
   *
   * @param viewModel The underlying persistent data storage (throughout Activity and Fragment Lifecycle).
   */
  fun setViewModel(viewModel: ContactsSelectionViewModel?) {
    this.viewModel = viewModel
    initializeAdapter()
    this.viewModel!!.contacts.observe(viewLifecycleOwner) { users: List<Recipient> ->
      val filtered = getFiltered(users, null)
      TIRecyclerViewAdapter!!.submitList(ArrayList(filtered))
    }
  }

  private fun safeArguments(): Bundle {
    return if (arguments != null) requireArguments() else Bundle()
  }

  private fun initializeAdapter() {
    val context = this.context
    if (context == null) {
      w(TAG, "failed to get context, cannot initialize adapter")
      throw IllegalStateException()
    }
    val glideRequests = Glide.get(context)
    // Not directly passing a cursor, instead submitting a list to ContactsAdapter
    TIRecyclerViewAdapter = ContactsSelectionAdapter(requireContext(), glideRequests, ContactClickListener())

    TIContactsRecycler!!.adapter = TIRecyclerViewAdapter
  }

  @MainThread
  @CallSuper
  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)
    loadSelection()
  }

  /**
   * Saved state to be restored from viewModel.
   */
  private fun loadSelection() {
    if (this.viewModel != null) {
      updateChips()
      restoreCheckboxState()
    } // Do nothing if viewModel is null, should never happen
  }

  private fun restoreCheckboxState(): Boolean {
    for (model in viewModel!!.listSelectedContactModels()) {
      val selected = model.recipientId
      for (i in 0 until TIContactsRecycler!!.childCount) {
        val item = (TIContactsRecycler!!.getChildViewHolder(TIContactsRecycler!!.getChildAt(i)) as TIContactViewHolder)
        if (item.recipientId == selected) {
          item.setCheckboxChecked(true)
          break
        }
      }
    }
    return true
  }

  private fun getFiltered(contacts: List<Recipient>, filter: String?): List<Recipient> {
    var filter = filter
    val filtered: MutableList<Recipient> = ArrayList(contacts)
    filter = if ((filter == null)) Objects.requireNonNull(viewModel!!.getFilter().value) else filter
    if (filter!!.isNotEmpty() && filter.compareTo("") != 0) {
      for (c in contacts) {
        // Choose appropriate string representation
        val filterPattern = Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE)
        if (!filterPattern.matcher(c.getDisplayName(requireContext())).find() &&
          !filterPattern.matcher(c.e164.orElse("")).find()
        ) {
          filtered.remove(c)
        }
      }
    }
    return filtered
  }

  override fun onFilterChanged(filter: String) {
    viewModel!!.setQueryFilter(filter)
    TIRecyclerViewAdapter!!.submitList(getFiltered(viewModel!!.contacts.value!!, filter))
  }

  private inner class ContactClickListener : ContactsSelectionAdapter.ItemClickListener {
    override fun onItemClick(item: TIContactViewHolder?) {
      if (viewModel!!.isSelectedContact(item!!.recipient!!)) {
        markContactUnselected(item.recipient!!)
        item.setCheckboxChecked(false)
      } else {
        markContactSelected(item.recipient!!)
        item.setCheckboxChecked(true)
      }
    }
  }

  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  internal interface OnContactSelectedListener {
    fun onContactSelected(recipientId: Optional<RecipientId?>?, number: String?)
  }

  private fun updateChips() {
    contactChipAdapter!!.submitList(MappingModelList(viewModel!!.listSelectedContactModels())) { this.smoothScrollChipsToEnd() }
    val selectedCount = viewModel!!.selectedContactsCount
    if (selectedCount == 0) {
      setChipGroupVisibility(ConstraintSet.GONE)
    } else {
      setChipGroupVisibility(ConstraintSet.VISIBLE)
    }
  }

  private fun onChipCloseIconClicked(m: SelectedTIContacts.Model) {
    markContactUnselected(m.selectedContact)
//    return null
  }

  private fun setChipGroupVisibility(visibility: Int) {
    chipRecycler!!.visibility = visibility
  }

  private fun smoothScrollChipsToEnd() {
    val x = if (ViewUtil.isLtr(chipRecycler!!)) chipRecycler!!.width else 0
    chipRecycler!!.smoothScrollBy(x, 0)
  }

  private fun markContactUnselected(selectedContact: Recipient) {
    if (viewModel!!.removeSelectedContact(selectedContact) < 0) {
      w(TAG, String.format(Locale.US, "%s could not be removed from selection!", selectedContact))
    } else {
      i(TAG, String.format(Locale.US, "%s was removed from selection.", selectedContact))
      updateChips()
    }
  }

  private fun markContactSelected(selectedContact: Recipient) {
    if (!viewModel!!.addSelectedContact(selectedContact)) {
      i(TAG, String.format("Contact %s was already part of the selection.", selectedContact))
    } else {
      updateChips()
    }
  }

  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, tag(ContactsSelectionListFragment::class.java))
  }
}
