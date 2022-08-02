package org.thoughtcrime.securesms.trustedIntroductions

import android.view.View
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactChip
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

object SelectedTIContacts {

  @JvmStatic
  fun register(adapter: MappingAdapter, onCloseIconClicked: (Model) -> Unit) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onCloseIconClicked) }, R.layout.contact_selection_list_chip))
  }

  class Model(val selectedContact: MinimalContactSelectionListItem, val recipientId: RecipientId): MappingModel<Model> {

    override fun equals(other: Any?): Boolean {
      if (other is Model){
        return this.areContentsTheSame(other)
      }
      return super.equals(other)
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return areItemsTheSame(newItem);
    }

    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.selectedContact.getFullName().equals(recipientId);
    }
  }

  private class ViewHolder(itemView: View, private val onCloseIconClicked: (Model) -> Unit) : MappingViewHolder<Model>(itemView) {

    private val chip: ContactChip = itemView.findViewById(R.id.contact_chip)

    override fun bind(m: Model) {
      chip.text = m.selectedContact.get()
      chip.isCloseIconVisible = true
      chip.setOnCloseIconClickListener {
        onCloseIconClicked(m);
      }
    }
  }

}