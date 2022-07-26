package org.thoughtcrime.securesms.trustedIntroductions

import android.view.View
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactChip
import org.thoughtcrime.securesms.contacts.SelectedContacts
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

object SelectedStrings {

  @JvmStatic
  fun register(adapter: MappingAdapter, onCloseIconClicked: (SelectedStrings.Model) -> Unit) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onCloseIconClicked) }, R.layout.contact_selection_list_chip))
  }

  class Model(val selectedString: MinimalStringModel, val str: String): MappingModel<SelectedStrings.Model> {
    override fun areContentsTheSame(newItem: SelectedStrings.Model): Boolean {
      return areItemsTheSame(newItem);
    }

    override fun areItemsTheSame(newItem: SelectedStrings.Model): Boolean {
      return newItem.selectedString.getName().equals(str);
    }
  }

  private class ViewHolder(itemView: View, private val onCloseIconClicked: (SelectedStrings.Model) -> Unit) : MappingViewHolder<SelectedStrings.Model>(itemView) {

    private val chip: ContactChip = itemView.findViewById(R.id.contact_chip)

    override fun bind(m: SelectedStrings.Model) {
      chip.text = m.selectedString.get();
      chip.setOnCloseIconClickListener {
        onCloseIconClicked(m);
      }
    }

  }

}