package org.thoughtcrime.securesms.trustedIntroductions.receive

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.trustedIntroductions.RelativeTimestamp.getRelativeTime
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

    private val chatButton: MaterialButton = itemView.findViewById(R.id.chat)

    // my experimental stuff
    private val introducerAvatar: AvatarImageView = itemView.findViewById(R.id.introducerAvatar);
    private val introduceeAvatar: AvatarImageView = itemView.findViewById(R.id.introduceeAvatar);
    private val introduceeHeading: TextView = itemView.findViewById(R.id.introduceeHeading);
    private val introduceeInfo: TextView = itemView.findViewById(R.id.introduceeInfo);
    private val toggleGroup: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleTrust);
    private val acceptBtn: Button = itemView.findViewById(R.id.acceptIntro);
    private val rejectBtn: Button = itemView.findViewById(R.id.rejectIntro);


    @SuppressLint("RestrictedApi", "SetTextI18n")
    fun bind(d: TI_Data?, introducerInformation: ManageViewModel.IntroducerInformation?) {
      data = d
      d?.let { safeData ->
        val date = Date(safeData.timestamp)
        val dString = TI_Utils.INTRODUCTION_DATE_PATTERN.format(date)

        changeListItemAppearanceByState(safeData.state)

        acceptBtn.setOnClickListener { _ ->
          changeTrust(true)
        }

        rejectBtn.setOnClickListener { _ ->
          changeTrust(false)
        }

        chatButton.setOnClickListener {
          listener.openChat(safeData.id ?: -1, safeData.introduceeServiceId, safeData.introduceeNumber, null)
        }

        // todo: mask should probably not set the `safeData.introducerServiceId` as the "-1" string
        val introducerRecipient = safeData.introducerServiceId?.let { serviceId -> runCatching { TI_Utils.getRecipientIdOrUnknown(serviceId) }.getOrNull() }
        if (introducerRecipient != null && !introducerRecipient.isUnknown) {
          introducerAvatar.setAvatar(Recipient.resolved(introducerRecipient)) // todo: might hit the disk and crash terribly
        } else {
          introducerAvatar.setNonAvatarImageResource(R.drawable.ti_domino_mask_active_48)
        }
        val introduceeRecipient = safeData.introduceeServiceId.let { serviceId -> runCatching { TI_Utils.getRecipientIdOrUnknown(serviceId) }.getOrNull() }
        if (introduceeRecipient != null && !introduceeRecipient.isUnknown) {
          introduceeAvatar.setAvatar(Recipient.resolved(introduceeRecipient)) // todo: might hit the disk and crash terribly
        }

        introduceeHeading.text = "Meet " + safeData.introduceeName
        val introducer = introducerInformation?.name ?: "Somebody"
        introduceeInfo.text = "Introduced by " + introducer + " (" + getRelativeTime(context, safeData.timestamp) + ")";

        introducerAvatar.setOnClickListener {
          run {
            Toast.makeText(it.context, "This is $introducer", Toast.LENGTH_LONG).show()
            if (introducerRecipient != null && !introducerRecipient.isUnknown) {
              RecipientBottomSheetDialogFragment.show((context as FragmentActivity).supportFragmentManager, introducerRecipient, null)
            }
          }
        }

        introduceeAvatar.setOnClickListener {
          run {
            Toast.makeText(it.context, "Introducee: $safeData.introduceeName ($safeData.introduceeServiceId)", Toast.LENGTH_LONG).show()
            if (introduceeRecipient != null && !introduceeRecipient.isUnknown) {
              RecipientBottomSheetDialogFragment.show((context as FragmentActivity).supportFragmentManager, introduceeRecipient, null)
            }
          }
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

    fun getIntroducerServiceId(): String {
      return data?.introducerServiceId ?: TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID
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

    @Deprecated("Will be removed if not needed anymore")
    private fun setForgetIntroducerComponentVisibility() {
      val introducerServiceId = requireNotNull(data?.introducerServiceId)
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

      // Toggle buttons state
      acceptBtn.isEnabled = !state.isStale
      acceptBtn.isClickable = !state.isStale
      rejectBtn.isEnabled = !state.isStale
      rejectBtn.isClickable = !state.isStale

      // Label text and radio group state
      when (state) {
        PENDING, PENDING_CONFLICTING, PENDING_UNKNOWN -> {
        }

        ACCEPTED_CONFLICTING -> {
          if (toggleGroup.checkedButtonId != acceptBtn.id) toggleGroup.check(acceptBtn.id);
        }

        REJECTED_CONFLICTING -> {
          if (toggleGroup.checkedButtonId != rejectBtn.id) toggleGroup.check(rejectBtn.id);
        }

        STALE_PENDING, STALE_PENDING_CONFLICTING -> {
        }

        STALE_ACCEPTED, STALE_ACCEPTED_CONFLICTING -> {
          if (toggleGroup.checkedButtonId != acceptBtn.id) toggleGroup.check(acceptBtn.id);
        }

        STALE_REJECTED, STALE_REJECTED_CONFLICTING -> {
          if (toggleGroup.checkedButtonId != rejectBtn.id) toggleGroup.check(rejectBtn.id);
        }

        ACCEPTED, ACCEPTED_UNKNOWN -> {
          if (toggleGroup.checkedButtonId != acceptBtn.id) toggleGroup.check(acceptBtn.id);
        }

        REJECTED, REJECTED_UNKNOWN -> {
          if (toggleGroup.checkedButtonId != rejectBtn.id) toggleGroup.check(rejectBtn.id);
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
        introduceeProfileKey = d.introduceeProfileKey,
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
    fun openChat(introductionId: Long, serviceId: String, e164: String?, username: String?)
    fun accept(introductionId: Long)
    fun reject(introductionId: Long)
    fun mask(item: IntroductionViewHolder, introducerServiceId: String)
    fun delete(item: IntroductionViewHolder, introducerServiceId: String)
  }
}