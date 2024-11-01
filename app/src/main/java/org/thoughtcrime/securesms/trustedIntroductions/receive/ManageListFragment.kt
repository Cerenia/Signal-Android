package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pnikosis.materialishprogress.ProgressWheel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.splitIntroductionDate
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.ActiveTab
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.ActiveTab.NEW
import java.util.regex.Pattern

class ManageListFragment(
  private val owner: ViewModelStoreOwner? = null,
  private var tab: ActiveTab = NEW
) : Fragment(),
  DeleteIntroductionDialog.DeleteIntroduction,
  ForgetIntroducerDialog.ForgetIntroducer {

  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageListFragment::class.java))
    const val TYPE_KEY = "type_key"
    lateinit var FORGOTTEN_INTRODUCER: String
  }

  private var showIntroductionsProgress: ProgressWheel? = null
  private lateinit var viewModel: ManageViewModel
  private lateinit var adapter: ManageAdapter
  private lateinit var noIntroductions: TextView
  private lateinit var allHeader: View
  private lateinit var showConflicting: MaterialButton
  private var sCisFirstInit = true

  private lateinit var showAccepted: MaterialButton
  private var sAisFirstInit = true

  private lateinit var showRejected: MaterialButton
  private var sRisFirstInit = true

  private lateinit var showStale: MaterialButton
  private var sSisFirstInit = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.ti_manage_fragment, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val factory = ManageViewModel.Factory(FORGOTTEN_INTRODUCER)
    viewModel = ViewModelProvider(requireActivity(), factory)[ManageViewModel::class.java]

    if (!viewModel.introductionsLoaded()) {
      viewModel.loadIntroductions()
    }

    adapter = ManageAdapter(requireContext(), IntroductionClickListener(this, this))

    val introductionList: RecyclerView = view.findViewById(R.id.recycler_view)
    introductionList.setClipToPadding(true)
    introductionList.adapter = adapter

    noIntroductions = view.findViewById(R.id.no_introductions_found)
    allHeader = view.findViewById(R.id.manage_fragment_header)

    // Filter state buttons
    showConflicting = view.findViewById(R.id.conflictingFilter)
    showStale = view.findViewById(R.id.staleFilter)
    showAccepted = view.findViewById(R.id.acceptedFilter)
    showRejected = view.findViewById(R.id.rejectedFilter)

    // Restore tab from saved state if available
    savedInstanceState?.getString(TYPE_KEY)?.let {
      tab = ActiveTab.fromString(it)
    }

    setupFilterButtons()
    setupFilterStateObservers()
    setupIntroductionsObserver()
  }

  private fun setupFilterButtons() {
    showConflicting.visibility = View.VISIBLE
    showStale.visibility = View.VISIBLE

    // Set initial button states
    showConflicting.isChecked = viewModel.showConflicting().value ?: true
    showStale.isChecked = viewModel.showStale().value ?: true

    // Add listeners
    showConflicting.addOnCheckedChangeListener { _, isChecked ->
      viewModel.setShowConflicting(isChecked)
    }
    showStale.addOnCheckedChangeListener { _, isChecked ->
      viewModel.setShowStale(isChecked)
    }

    when (tab) {
      NEW -> {
        showAccepted.visibility = View.GONE
        showRejected.visibility = View.GONE
        showStale.visibility = View.GONE
      }

      ActiveTab.LIBRARY -> {
        showAccepted.visibility = View.VISIBLE
        showRejected.visibility = View.VISIBLE

        showAccepted.isChecked = viewModel.showTrusted().value ?: true
        showRejected.isChecked = viewModel.showDistrusted().value ?: true

        showAccepted.addOnCheckedChangeListener { _, isChecked ->
          viewModel.setShowTrusted(isChecked)
        }
        showRejected.addOnCheckedChangeListener { _, isChecked ->
          viewModel.setShowDistrusted(isChecked)
        }
      }
    }
  }

  private fun setupFilterStateObservers() {
    viewModel.showConflicting().observe(viewLifecycleOwner) { state ->
      sCisFirstInit = onFilterStateChanged(showConflicting, state, sCisFirstInit)
    }
    viewModel.showStale().observe(viewLifecycleOwner) { state ->
      sSisFirstInit = onFilterStateChanged(showStale, state, sSisFirstInit)
    }
    viewModel.showTrusted().observe(viewLifecycleOwner) { state ->
      sAisFirstInit = onFilterStateChanged(showAccepted, state, sAisFirstInit)
    }
    viewModel.showDistrusted().observe(viewLifecycleOwner) { state ->
      sRisFirstInit = onFilterStateChanged(showRejected, state, sRisFirstInit)
    }
  }

  private fun setupIntroductionsObserver() {
    viewModel.getIntroductions().observe(viewLifecycleOwner) { introductions ->
      if (introductions.isNotEmpty()) {
        noIntroductions.visibility = View.GONE
        allHeader.visibility = View.VISIBLE
      } else {
        noIntroductions.visibility = View.VISIBLE
        allHeader.visibility = View.GONE
        noIntroductions.setText(R.string.ManageIntroductionsFragment__No_Introductions_all)
      }
      refreshList()
    }
  }

  private fun onFilterStateChanged(
    button: MaterialButton,
    newCheckState: Boolean?,
    isFirstInit: Boolean
  ): Boolean {
    newCheckState?.let {
      if (button.isChecked != it) {
        button.isChecked = it
      }
    }
    if (!isFirstInit) {
      refreshList()
    }
    return false
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString(TYPE_KEY, tab.toString())
    super.onSaveInstanceState(outState)
  }

  private fun isDisplayed(p: Pair<TI_Data, ManageViewModel.IntroducerInformation>): Boolean {
    val s = p.first.state
    return when (tab) {
      NEW -> s.isPending() && !s.isStale() && !userFiltered(s)
      ActiveTab.LIBRARY -> !s.isPending() && !userFiltered(s)
      else -> true
    }
  }

  private fun userFiltered(s: TI_Database.State): Boolean {
    return when {
      viewModel.showConflicting().value == false && s.isConflicting() -> true
      viewModel.showStale().value == false && s.isStale() -> true
      viewModel.showTrusted().value == false && s.isTrusted() -> true
      viewModel.showDistrusted().value == false && s.isDistrusted() -> true
      else -> false
    }
  }

  private fun getFiltered(
    introductions: List<Pair<TI_Data, ManageViewModel.IntroducerInformation>>?,
    filter: String?
  ): List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> {
    introductions ?: return emptyList()

    val filtered = introductions.filter { isDisplayed(it) }.toMutableList()

    if (!filter.isNullOrBlank()) {
      val filterPattern = Pattern.compile("\\A$filter.*", Pattern.CASE_INSENSITIVE)
      filtered.removeAll { p ->
        val d = p.first
        val timestampParts = splitIntroductionDate(d.timestamp)

        // Match conditions
        val matchYear = filterPattern.matcher(timestampParts.year).find()
        val matchMonth = filterPattern.matcher(timestampParts.month).find()
        val matchDay = filterPattern.matcher(timestampParts.day).find()
        val matchHours = filterPattern.matcher(timestampParts.hours).find()
        val matchMinutes = filterPattern.matcher(timestampParts.minutes).find()
        val matchSeconds = filterPattern.matcher(timestampParts.seconds).find()
        val matchIntroduceeName = filterPattern.matcher(d.introduceeName!!).find()
        val matchIntroduceeNumber = filterPattern.matcher(d.introduceeNumber!!).find()
        val matchIntroducerName = filterPattern.matcher(p.second.name).find()
        val matchIntroducerNumber = filterPattern.matcher(p.second.number).find()

        !(matchYear || matchMonth || matchDay || matchHours || matchMinutes ||
          matchSeconds || matchIntroduceeName || matchIntroduceeNumber ||
          matchIntroducerName || matchIntroducerNumber)
      }
    }

    return sortIntroductions(filtered)
  }

  private fun sortIntroductions(
    filtered: List<Pair<TI_Data, ManageViewModel.IntroducerInformation>>
  ): List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> {
    return when (tab) {
      NEW -> filtered.sortedBy { it.first.timestamp }
      ActiveTab.LIBRARY -> filtered.sortedWith(
        compareBy(
          { it.first.state.toInt() },
          { it.first.introduceeName },
          { it.first.timestamp }
        )
      )

      else -> throw AssertionError("$TAG Unknown tab type!")
    }
  }

  fun refreshList() {
    adapter.submitList(getFiltered(viewModel.getIntroductions().value, viewModel.getTextFilter().value))
  }

  fun onFilterChanged(filter: String) {
    viewModel.setTextFilter(filter)
    adapter.submitList(getFiltered(viewModel.getIntroductions().value, filter))
  }

  // Implement dialog callbacks
  override fun deleteIntroduction(introductionId: Long) {
    viewModel.deleteIntroduction(introductionId)
  }

  override fun forgetIntroducer(introductionId: Long) {
    viewModel.forgetIntroducer(introductionId)
  }

  private inner class IntroductionClickListener(
    private val deleteHandler: DeleteIntroductionDialog.DeleteIntroduction,
    private val forgetHandler: ForgetIntroducerDialog.ForgetIntroducer
  ) : ManageAdapter.InteractionListener {

    private val context = requireContext()

    override fun accept(introductionId: Long) {
      viewModel.acceptIntroduction(introductionId)
    }

    override fun reject(introductionId: Long) {
      viewModel.rejectIntroduction(introductionId)
    }

    override fun mask(item: ManageAdapter.IntroductionViewHolder, introducerServiceId: String) {
      if (introducerServiceId != TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
        ForgetIntroducerDialog.show(
          context,
          item.introductionId,
          item.introduceeName,
          item.getIntroducerName(context)!!, // todo: this might fail :c
          item.date,
          forgetHandler
        )
      }
    }

    override fun delete(item: ManageAdapter.IntroductionViewHolder, introducerServiceId: String) {
      val introducerName = if (introducerServiceId == TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
        getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer)
      } else {
        Recipient.resolved(TI_Utils.getRecipientIdOrUnknown(introducerServiceId))
          .getDisplayName(context)
      }

      DeleteIntroductionDialog.show(
        context,
        item.introductionId,
        item.introduceeName,
        introducerName,
        item.date,
        deleteHandler
      )
    }
  }
}