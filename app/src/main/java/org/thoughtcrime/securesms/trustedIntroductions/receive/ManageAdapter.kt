package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.ACCEPTED
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.ACCEPTED_CONFLICTING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.ACCEPTED_UNKNOWN
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.PENDING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.PENDING_CONFLICTING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.REJECTED
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.REJECTED_CONFLICTING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.REJECTED_UNKNOWN
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.STALE_ACCEPTED
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.STALE_ACCEPTED_CONFLICTING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.STALE_PENDING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.STALE_PENDING_CONFLICTING
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.STALE_REJECTED
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.State.STALE_REJECTED_CONFLICTING
import java.util.Date

class ManageAdapter(
  context: Context,
  private val listener: InteractionListener
) : ListAdapter<Pair<TI_Data, ManageViewModel.IntroducerInformation>, ManageAdapter.IntroductionViewHolder>(
  object : DiffUtil.ItemCallback<Pair<TI_Data, ManageViewModel.IntroducerInformation>>() {
    override fun areItemsTheSame(
      oldItem: Pair<TI_Data, ManageViewModel.IntroducerInformation>,
      newItem: Pair<TI_Data, ManageViewModel.IntroducerInformation>
    ): Boolean = oldItem.first.id?.compareTo(newItem.first.id!!) == 0

    private fun areNullableFieldsEqual(field1: Any?, field2: Any?): Boolean = when {
      field1 == null && field2 == null -> true
      field1 == null || field2 == null -> false
      else -> field1 == field2
    }

    override fun areContentsTheSame(
      oldPair: Pair<TI_Data, ManageViewModel.IntroducerInformation>,
      newPair: Pair<TI_Data, ManageViewModel.IntroducerInformation>
    ): Boolean {
      val oldItem = oldPair.first
      val newItem = newPair.first
      return oldItem.state == newItem.state &&
        areNullableFieldsEqual(oldItem.id, newItem.id) &&
        (oldItem.introducerServiceId == null ||
          newItem.introducerServiceId == null ||
          oldItem.introducerServiceId == newItem.introducerServiceId) &&
        oldItem.introduceeServiceId == newItem.introduceeServiceId &&
        areNullableFieldsEqual(oldItem.introduceeName, newItem.introduceeName) &&
        areNullableFieldsEqual(oldItem.introduceeNumber, newItem.introduceeNumber) &&
        oldItem.introduceeIdentityKey == newItem.introduceeIdentityKey &&
        areNullableFieldsEqual(oldItem.predictedSecurityNumber, newItem.predictedSecurityNumber) &&
        oldItem.timestamp == newItem.timestamp
    }
  }
) {
  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageAdapter::class.java))
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroductionViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.ti_manage_list_item, parent, false)
    return IntroductionViewHolder(view, listener, parent.context)
  }

  override fun onBindViewHolder(holder: IntroductionViewHolder, position: Int) {
    getItem(position)?.let { pair ->
      holder.bind(pair.first, pair.second)
    }
  }

  class IntroductionViewHolder(
    itemView: View,
    private val listener: InteractionListener,
    private val context: Context
  ) : RecyclerView.ViewHolder(itemView) {
    private var data: TI_Data? = null
    private val timestampDate: TextView = itemView.findViewById(R.id.timestamp_date)
    private val timestampTime: TextView = itemView.findViewById(R.id.timestamp_time)
    private val introducerName: TextView = itemView.findViewById(R.id.introducerName)
    private val introducerNumber: TextView = itemView.findViewById(R.id.introducerNumber)
    private val introduceeName: TextView = itemView.findViewById(R.id.introduceeName)
    private val introduceeNumber: TextView = itemView.findViewById(R.id.introduceeNumber)
    private val accept: RadioButton = itemView.findViewById(R.id.accept)
    private val reject: RadioButton = itemView.findViewById(R.id.reject)
    private val radioGroup: RadioGroup = itemView.findViewById(R.id.trust_distrust)
    private val radioGroupLabel: TextView = itemView.findViewById(R.id.radio_group_label)
    private val chatButton: MaterialButton = itemView.findViewById(R.id.chat)
//    private val guideline: Guideline = itemView.findViewById(R.id.guideline_right)
//    private val mask: ImageView = itemView.findViewById(R.id.maskedImage)
    private val maskIntroducer: MaterialButton = itemView.findViewById(R.id.mask)
    private val delete: MaterialButton = itemView.findViewById(R.id.delete)

    init {
      radioGroup.setOnCheckedChangeListener { _, id ->
        changeTrust(id == accept.id)
      }
    }

    @SuppressLint("RestrictedApi")
    fun bind(d: TI_Data?, introducerInformation: ManageViewModel.IntroducerInformation?) {
      data = d
      d?.let { safeData ->
        val date = Date(safeData.timestamp)
        val dString = TI_Utils.INTRODUCTION_DATE_PATTERN.format(date)

        timestampDate.text = dString.split(" ")[0]
        timestampTime.text = dString.split(" ")[1]
        introduceeName.text = safeData.introduceeName
        introduceeNumber.text = safeData.introduceeNumber
        introduceeName.visibility = View.VISIBLE
        introduceeNumber.visibility = View.VISIBLE

        if (introducerInformation == null) {
          introducerName.setText(R.string.ManageIntroductionsListItem__Unknown_Value)
          introducerNumber.setText(R.string.ManageIntroductionsListItem__Unknown_Value)
        } else {
          introducerNumber.text = introducerInformation.number
          introducerName.text = introducerInformation.name
        }

        introducerNumber.visibility = View.VISIBLE
        introducerName.visibility = View.VISIBLE
//        guideline.setGuidelinePercent(0.5f)
        changeListItemAppearanceByState(safeData.state)

        maskIntroducer.setOnClickListener {
          listener.mask(this, safeData.introducerServiceId!!)
        }
        delete.setOnClickListener {
          listener.delete(this, safeData.introducerServiceId!!)
        }
        chatButton.setOnClickListener {
          listener.openChat(safeData.introduceeServiceId, safeData.introduceeNumber, null)
        }
      }
    }

    fun getIntroduceeName(): String? = data?.introduceeName

    fun getDate(): Date? = data?.timestamp?.let { Date(it) }

    fun getIntroductionId(): Long {
      return requireNotNull(data?.id) {
        "Data ID should never be null once written to database"
      }
    }

    fun getIntroducerName(c: Context): String {
      val introducerId = data?.introducerServiceId
      return when {
        introducerId == null || introducerId == RecipientId.UNKNOWN.toString() ->
          c.getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer)

        else -> {
          Recipient.live(TI_Utils.getRecipientIdOrUnknown(introducerId))
            .resolve()
            .getDisplayName(c)
        }
      }
    }

    private fun changeTrust(trust: Boolean) {
      val currentData = data ?: return
      val currentState = currentData.state

      if (currentState.isStale) return
      if (currentState.isTrusted && trust || currentState.isDistrusted && !trust) return

      val newState = when {
        trust -> when (currentState) {
          PENDING, REJECTED -> ACCEPTED
          PENDING_CONFLICTING, REJECTED_CONFLICTING -> ACCEPTED_CONFLICTING
          PENDING_UNKNOWN, REJECTED_UNKNOWN -> ACCEPTED_UNKNOWN
          else -> throw AssertionError("$TAG Illegal state-machine transition for state: ${currentState.name} and new trust: true (accept)")
        }

        else -> when (currentState) {
          PENDING, ACCEPTED -> REJECTED
          PENDING_CONFLICTING, ACCEPTED_CONFLICTING -> REJECTED_CONFLICTING
          PENDING_UNKNOWN, ACCEPTED_UNKNOWN -> REJECTED_UNKNOWN
          else -> throw AssertionError("$TAG Illegal state-machine transition for state: ${currentState.name} and new trust: false (reject)")
        }
      }

      var toastContent = ""
      toastContent = if (trust) {
        listener.accept(requireNotNull(currentData.id))
        "Accepted introduction for ${currentData.introduceeName} (was ${currentData.state})"
      } else {
        listener.reject(requireNotNull(currentData.id))
        "Rejected introduction for ${currentData.introduceeName} (was ${currentData.state})"
      }

      data = changeState(currentData, newState)
      Toast.makeText(context, toastContent, Toast.LENGTH_SHORT).show()
    }

    private fun setForgetIntroducerComponentVisibility() {
      val introducerServiceId = requireNotNull(data?.introducerServiceId)
      if (introducerServiceId == TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) {
//        maskIntroducer.visibility = View.GONE
        introducerNumber.visibility = View.GONE
        introducerName.visibility = View.GONE
//        mask.visibility = View.VISIBLE
        maskIntroducer.setIconResource(R.drawable.ti_domino_mask_active_48)
        maskIntroducer.setEnabled(false)
      } else {
//        maskIntroducer.visibility = View.VISIBLE
        maskIntroducer.setIconResource(R.drawable.ti_domino_mask_24px)
        maskIntroducer.setEnabled(true)
//        mask.visibility = View.GONE
      }
    }

    private fun changeListItemAppearanceByState(state: TI_DatabaseGlue.Companion.State) {
      // Background
      val backgroundRes = when {
        state.isStale && state.isConflicting -> R.drawable.ti_manage_listview_background_stale_conflicting
        state.isStale -> R.drawable.ti_manage_listview_background_stale
        state.isConflicting -> R.drawable.ti_manage_listview_background_conflicting
        else -> R.drawable.ti_manage_listview_background_default
      }
      itemView.background = ContextCompat.getDrawable(context, backgroundRes)

      // Radio buttons state
      accept.isEnabled = !state.isStale
      accept.isClickable = !state.isStale
      reject.isEnabled = !state.isStale
      reject.isClickable = !state.isStale

      // Masking visibility and state
      maskIntroducer.visibility = View.VISIBLE
      when (state) {
        STALE_PENDING, STALE_PENDING_CONFLICTING -> {
          maskIntroducer.isEnabled = true
          maskIntroducer.isClickable = true
        }

        else -> {
          maskIntroducer.isEnabled = !state.isPending
          maskIntroducer.isClickable = !state.isPending
          setForgetIntroducerComponentVisibility()
        }
      }

      // Label text and radio group state
      when (state) {
        PENDING, PENDING_CONFLICTING, PENDING_UNKNOWN -> {
          radioGroupLabel.visibility = View.VISIBLE
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Pending)
        }

        ACCEPTED_CONFLICTING -> {
          radioGroupLabel.visibility = View.VISIBLE
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting)
          if (!accept.isChecked) accept.isChecked = true
        }

        REJECTED_CONFLICTING -> {
          radioGroupLabel.visibility = View.VISIBLE
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Conflicting)
          if (!reject.isChecked) reject.isChecked = true
        }

        STALE_PENDING, STALE_PENDING_CONFLICTING -> {
          radioGroupLabel.visibility = View.VISIBLE
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale)
        }

        STALE_ACCEPTED, STALE_ACCEPTED_CONFLICTING -> {
          radioGroupLabel.visibility = View.VISIBLE
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale)
          if (!accept.isChecked) accept.isChecked = true
        }

        STALE_REJECTED, STALE_REJECTED_CONFLICTING -> {
          radioGroupLabel.visibility = View.VISIBLE
          radioGroupLabel.setText(R.string.ManageIntroductionsListItem__Stale)
          if (!reject.isChecked) reject.isChecked = true
        }

        ACCEPTED, ACCEPTED_UNKNOWN -> {
          radioGroupLabel.visibility = View.GONE
          if (!accept.isChecked) accept.isChecked = true
        }

        REJECTED, REJECTED_UNKNOWN -> {
          radioGroupLabel.visibility = View.GONE
          if (!reject.isChecked) reject.isChecked = true
        }
      }
    }

    private fun changeState(d: TI_Data, newState: TI_DatabaseGlue.Companion.State): TI_Data {
      return TI_Data(
        id = d.id,
        state = newState,
        introducerServiceId = d.introducerServiceId,
        introduceeServiceId = d.introduceeServiceId,
        introduceeName = d.introduceeName,
        introduceeNumber = d.introduceeNumber,
        introduceeIdentityKey = d.introduceeIdentityKey,
        predictedSecurityNumber = d.predictedSecurityNumber,
        timestamp = d.timestamp
      )
    }

    fun setEnabled(enabled: Boolean) {
      itemView.isEnabled = enabled
    }

    // Sticky header helpers
    fun measure(width: Int, height: Int) {
      itemView.measure(width, height)
    }

    fun getMeasuredHeight(): Int = itemView.measuredHeight

    fun layout(left: Int, top: Int, right: Int, bottom: Int) {
      itemView.layout(left, top, right, bottom)
    }

    fun getBottom(): Float = itemView.bottom.toFloat()
  }

  interface InteractionListener {
    fun openChat(serviceId: String, e164: String?, username: String?)
    fun accept(introductionId: Long)
    fun reject(introductionId: Long)
    fun mask(item: IntroductionViewHolder, introducerServiceId: String)
    fun delete(item: IntroductionViewHolder, introducerServiceId: String)
  }
}