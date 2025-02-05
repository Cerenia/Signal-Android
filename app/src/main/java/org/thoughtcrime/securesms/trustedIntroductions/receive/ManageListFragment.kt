package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.SimpleColorFilter
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.pnikosis.materialishprogress.ProgressWheel
import org.signal.core.util.DimensionUnit
import org.signal.core.util.ResourceUtil.getResources
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.splitIntroductionDate
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.ActiveTab
import org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.ActiveTab.NEW
import org.thoughtcrime.securesms.util.ViewUtil
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.Objects
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min

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
  //  private lateinit var allHeader: View
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
    introductionList.clipToPadding = true
    introductionList.adapter = adapter

    ItemTouchHelper(
      IntroductionSwipeCallback(
        introductionList,
        adapter,
        this,
        this,
        introductionList.context
      )
    ).attachToRecyclerView(introductionList)

    noIntroductions = view.findViewById(R.id.no_introductions_found)
//    allHeader = view.findViewById(R.id.manage_fragment_header)

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
//        allHeader.visibility = View.VISIBLE
      } else {
        noIntroductions.visibility = View.VISIBLE
//        allHeader.visibility = View.GONE
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
      NEW -> s.isPending && !s.isStale && !userFiltered(s)
      ActiveTab.LIBRARY -> !s.isPending && !userFiltered(s)
      else -> true
    }
  }

  private fun userFiltered(s: TI_DatabaseGlue.Companion.State): Boolean {
    return when {
      viewModel.showConflicting().value == false && s.isConflicting -> true
      viewModel.showStale().value == false && s.isStale -> true
      viewModel.showTrusted().value == false && s.isTrusted -> true
      viewModel.showDistrusted().value == false && s.isDistrusted -> true
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

    @SuppressLint("CheckResult")
    override fun openChat(serviceId: String, e164: String?, username: String?) {
      var introduceeRecipient = TI_Utils.getRecipientIdOrUnknown(serviceId)
      if (introduceeRecipient.isUnknown) {
        Log.i(TAG, "Introducee is unknown")
        if (e164 == null) {
          Log.e(TAG, "Introducee number is null")
        } else {
          Log.i(TAG, "Introducee number is $e164. Fetching new Recipient (CDSi lookup)")
          when (val lookup = RecipientRepository.lookupNewE164(AppDependencies.application.applicationContext, e164)) {
            is RecipientRepository.LookupResult.Success -> {
              introduceeRecipient = lookup.recipientId
              Log.i(TAG, "Got Recipient ID: ${introduceeRecipient.toLong()}")
            }

            RecipientRepository.LookupResult.InvalidEntry -> TODO("log properly")
            RecipientRepository.LookupResult.NetworkError -> TODO("log properly")
            is RecipientRepository.LookupResult.NotFound -> TODO("log properly")
          }
        }
      } else {
        Log.i(TAG, "Introducee was known already, opening chat")
      }

      ConversationIntents.createBuilder(context, introduceeRecipient, -1L)
        .map { builder: ConversationIntents.Builder ->
          builder
            .withDraftText(null)
            .withDataUri(null)
            .withDataType(null)
            .build()
        }
        .subscribe { intent: Intent? ->
          context.startActivity(intent)
        }
    }

    override fun mask(item: ManageAdapter.IntroductionViewHolder, introducerServiceId: String) {
      if (introducerServiceId != TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
        ForgetIntroducerDialog.show(
          context,
          item.getIntroductionId(),
          item.getIntroduceeName().toString(),
          item.getIntroducerName(context), // todo: this might fail :c
          item.getDate()!!,
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
        item.getIntroductionId(),
        item.getIntroduceeName().toString(),
        introducerName,
        item.getDate()!!,
        deleteHandler,
        object : DeleteIntroductionDialog.DismissAction {
          override fun onDismiss() {
            Log.i(TAG, "Delete dialog for introduction ${item.getIntroductionId()} dismissed")
          }
        }
      )
    }
  }

  private class IntroductionSwipeCallback(
    private val recyclerView: RecyclerView,
    private val adapter: RecyclerView.Adapter<*>,
    private val deleteHandler: DeleteIntroductionDialog.DeleteIntroduction,
    private val forgetHandler: ForgetIntroducerDialog.ForgetIntroducer,
    private val context: Context
  ) :
    ItemTouchHelper.SimpleCallback(0, (ItemTouchHelper.START or ItemTouchHelper.END)) {

    private var pendingPosition: Int? = null
    private var pendingDismissDirection: Int? = null


    companion object {
      private const val SWIPE_ANIMATION_DURATION: Long = 175
      private const val MIN_ICON_SCALE: Float = 0.85f
      private const val MAX_ICON_SCALE: Float = 1f
    }

    private lateinit var lastTouched: WeakReference<RecyclerView.ViewHolder>;

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder
    ): Boolean {
      return false
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
      lastTouched = WeakReference<RecyclerView.ViewHolder>(viewHolder)

      return super.getSwipeDirs(recyclerView, viewHolder)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
      val position = viewHolder.bindingAdapterPosition
      pendingPosition = position
      pendingDismissDirection = direction

      val item = viewHolder as ManageAdapter.IntroductionViewHolder

      when (direction) {
        ItemTouchHelper.START -> { // Left swipe - Delete
          showDeleteDialog(item)
        }

        ItemTouchHelper.END -> { // Right swipe - Forget
          if (item.getIntroducerServiceId() != TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
            showForgetSnackbar(item)
          } else {
            // Reset swipe if introducer is already forgotten
            adapter.notifyItemChanged(position)
            Toast.makeText(context, "Introduction was already masked.", Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    private fun showDeleteDialog(item: ManageAdapter.IntroductionViewHolder) {
      val introducerName = if (item.getIntroducerServiceId() == TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
        context.getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer)
      } else {
        Recipient.resolved(TI_Utils.getRecipientIdOrUnknown(item.getIntroducerServiceId()))
          .getDisplayName(context)
      }

      DeleteIntroductionDialog.show(
        context,
        item.getIntroductionId(),
        item.getIntroduceeName().toString(),
        introducerName,
        item.getDate()!!,
        object : DeleteIntroductionDialog.DeleteIntroduction {
          override fun deleteIntroduction(introductionId: Long) {
            deleteHandler.deleteIntroduction(introductionId)
            pendingPosition?.let { pos ->
              adapter.notifyItemRemoved(pos)
              pendingPosition = null
              pendingDismissDirection = null
            }
          }
        },
        object : DeleteIntroductionDialog.DismissAction {
          override fun onDismiss() {
            Toast.makeText(context, "dismissed delete", Toast.LENGTH_SHORT).show()
            pendingPosition?.let { pos -> adapter.notifyItemChanged(pos) }
          }
        }
      )
    }

    private fun showForgetSnackbar(item: ManageAdapter.IntroductionViewHolder) {
      Snackbar.make(
        recyclerView,
        context.getString(R.string.ManageIntroductionsListItem__Forget_Introducer),
        Snackbar.LENGTH_LONG
      ).apply {
        setAction("UNDO") {
          pendingPosition?.let { pos ->
            adapter.notifyItemChanged(pos)
            pendingPosition = null
            pendingDismissDirection = null
          }
        }

        addCallback(object : Snackbar.Callback() {
          override fun onDismissed(snackbar: Snackbar, event: Int) {
            if (event != DISMISS_EVENT_ACTION) { // If not cancelled
              pendingPosition?.let { pos ->
                forgetHandler.forgetIntroducer(item.getIntroductionId())
//                adapter.notifyItemChanged(pos) // Refresh to show forgotten state
                /*
                TODO:not a big fan of this but no Idea how to fix it currently. We have to re-render the entire dataset. if only we get a reference to the changed item :c
                */
                adapter.notifyDataSetChanged()
                pendingPosition = null
                pendingDismissDirection = null
              }
            } else {
              Toast.makeText(context, "dismissed unmask", Toast.LENGTH_SHORT).show()
              pendingPosition?.let { pos ->
                adapter.notifyItemChanged(pos)
              }
            }
          }
        })

        show()
      }
    }

    override fun onChildDraw(
      canvas: Canvas,
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      dX: Float,
      dY: Float,
      actionState: Int,
      isCurrentlyActive: Boolean
    ) {
      val absoluteDx = abs(dX.toDouble()).toFloat()
      val context = viewHolder.itemView.context
      val isRtl = ViewUtil.isRtl(context)

      var iconDrawable: Drawable? = null
      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        val resources: Resources = getResources(context, Locale.getDefault())
        val itemView = viewHolder.itemView
        val percentDx = absoluteDx / viewHolder.itemView.width

        // Determine if swipe is towards start or end
        val isSwipeToEnd = if (isRtl) dX < 0 else dX > 0

        val color = if (isSwipeToEnd) {
          ArgbEvaluatorCompat.getInstance().evaluate(
            min(1.0, (percentDx * (1 / 0.25f)).toDouble()).toFloat(),
            ContextCompat.getColor(context, R.color.conversation_violet),
            ContextCompat.getColor(context, R.color.conversation_violet_shade)
          )
        } else {
          ArgbEvaluatorCompat.getInstance().evaluate(
            min(1.0, (percentDx * (1 / 0.25f)).toDouble()).toFloat(),
            ContextCompat.getColor(context, R.color.conversation_crimson),
            ContextCompat.getColor(context, R.color.conversation_crimson_shade)
          )
        }

        val scaleStartPoint = DimensionUnit.DP.toPixels(48f)
        val scaleEndPoint = DimensionUnit.DP.toPixels(96f)
        val scale = if (absoluteDx < scaleStartPoint) {
          MIN_ICON_SCALE
        } else if (absoluteDx > scaleEndPoint) {
          MAX_ICON_SCALE
        } else {
          min(
            MAX_ICON_SCALE.toDouble(),
            (MIN_ICON_SCALE + ((absoluteDx - scaleStartPoint) / (scaleEndPoint - scaleStartPoint)) * (MAX_ICON_SCALE - MIN_ICON_SCALE)).toDouble()
          ).toFloat()
        }

        if (absoluteDx > 0) {
          if (iconDrawable == null) {
            iconDrawable = if (isSwipeToEnd) {
              Objects.requireNonNull<Drawable?>(AppCompatResources.getDrawable(context, R.drawable.ti_domino_mask_24px))
            } else {
              Objects.requireNonNull<Drawable?>(AppCompatResources.getDrawable(context, R.drawable.ic_ti_trash_24))
            }
            iconDrawable.colorFilter = SimpleColorFilter(ContextCompat.getColor(context, R.color.signal_colorOnSurface))
            iconDrawable.setBounds(0, 0, iconDrawable.intrinsicWidth, iconDrawable.intrinsicHeight)
          }

          canvas.save()
          canvas.clipRect(itemView.left, itemView.top, itemView.right, itemView.bottom)
          canvas.drawColor(color)

          val gutter = resources.getDimension(R.dimen.dsl_settings_gutter)
          val extra = resources.getDimension(R.dimen.conversation_list_fragment_archive_padding)

          // Calculate translation based on layout direction and swipe direction
          val translationX = when {
            isSwipeToEnd && !isRtl -> itemView.left + gutter + extra
            isSwipeToEnd && isRtl -> itemView.right - gutter - extra
            !isSwipeToEnd && !isRtl -> itemView.right - gutter - extra
            else -> itemView.left + gutter + extra
          }

          canvas.translate(
            translationX,
            itemView.top + (itemView.bottom - itemView.top - iconDrawable!!.intrinsicHeight) / 2f
          )

          canvas.scale(scale, scale, iconDrawable.intrinsicWidth / 2f, iconDrawable.intrinsicHeight / 2f)
          iconDrawable.draw(canvas)
          canvas.restore()

          ViewCompat.setElevation(viewHolder.itemView, DimensionUnit.DP.toPixels(4f))
        } else if (absoluteDx == 0f) {
          ViewCompat.setElevation(viewHolder.itemView, DimensionUnit.DP.toPixels(0f))
        }

        viewHolder.itemView.translationX = dX
      } else {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
      }
    }
  }
}