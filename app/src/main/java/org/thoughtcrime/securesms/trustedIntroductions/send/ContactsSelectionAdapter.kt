package org.thoughtcrime.securesms.trustedIntroductions.send

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.send.ContactsSelectionAdapter.TIContactViewHolder
import org.thoughtcrime.securesms.util.ViewUtil

class ContactsSelectionAdapter internal constructor(
  context: Context,
  private val glide: Glide,
  private val clickListener: ItemClickListener?
) :
  ListAdapter<Recipient?, TIContactViewHolder>(object : DiffUtil.ItemCallback<Recipient?>() {
    override fun areItemsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
      return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
      return oldItem.equals(newItem)
    }
  }) {
  private val layoutInflater: LayoutInflater = LayoutInflater.from(context)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TIContactViewHolder {
    return TIContactViewHolder(layoutInflater.inflate(R.layout.ti_contact_selection_list_item, parent, false), clickListener)
  }

  /**
   * Called by RecyclerView to display the data at the specified position. This method should
   * update the contents of the { ViewHolder#itemView} to reflect the item at the given
   * position.
   *
   *
   * Note that unlike [ListView], RecyclerView will not call this method
   * again if the position of the item changes in the data set unless the item itself is
   * invalidated or the new position cannot be determined. For this reason, you should only
   * use the `position` parameter while acquiring the related data item inside
   * this method and should not keep a copy of it. If you need the position of an item later
   * on (e.g. in a click listener), use { ViewHolder#getBindingAdapterPosition()} which
   * will have the updated adapter position.
   *
   *
   * Override { #onBindViewHolder(ViewHolder, int, List)} instead if Adapter can
   * handle efficient partial bind.
   *
   * @param holder   The ViewHolder which should be updated to represent the contents of the
   * item at the given position in the data set.
   * @param position The position of the item within the adapter's data set.
   */
  override fun onBindViewHolder(holder: TIContactViewHolder, position: Int) {
    val current = getItem(position)
    // For Type, see contactRepository, 0 == normal
    holder.bind(glide, current!!)
  }


  class TIContactViewHolder internal constructor(
    itemView: View,
    clickListener: ItemClickListener?
  ) : RecyclerView.ViewHolder(itemView) {
    private val contactPhotoImage: AvatarImageView = itemView.findViewById(R.id.contact_photo_image)
    private val nameView: TextView = itemView.findViewById(R.id.name)
    private val numberView: TextView = itemView.findViewById(R.id.number)
    private val checkbox: CheckBox = itemView.findViewById(R.id.check_box)
    var recipient: Recipient? = null
      private set


    init {
      itemView.setOnClickListener { _: View? ->
        clickListener?.onItemClick(this)
      }
      ViewUtil.setTextViewGravityStart(this.nameView, itemView.context)
    }

    fun bind(glide: Glide, recipient: Recipient) {
      this.recipient = recipient
      nameView.text = recipient.getDisplayName(itemView.context)
      contactPhotoImage.setAvatar(Glide.with(itemView.context), recipient, false)
      numberView.text = recipient.e164.orElse("")
    }

    fun setEnabled(enabled: Boolean) {
      itemView.isEnabled = enabled
    }

    val recipientId: RecipientId
      get() = recipient!!.id

    fun setCheckboxChecked(checked: Boolean) {
      checkbox.isChecked = checked
    }
  }

  interface ItemClickListener {
    // naughty: This does not not know about TIContactViewHolder
    fun onItemClick(item: TIContactViewHolder?)
  }
}
