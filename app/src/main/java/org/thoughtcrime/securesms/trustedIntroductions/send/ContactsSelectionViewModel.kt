package org.thoughtcrime.securesms.trustedIntroductions.send

import androidx.core.util.Consumer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.signal.core.util.concurrent.SimpleTask
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Objects

class ContactsSelectionViewModel internal constructor(private val manager: ContactsSelectionManager) : ViewModel() {
  private val selectedContacts = ArrayList<SelectedTIContacts.Model>()
  private val introducibleContacts = MutableLiveData<List<Recipient>>()
  private val filter = MutableLiveData("")

  init {
    loadValidContacts()
  }

  fun addSelectedContact(contact: Recipient): Boolean {
    return selectedContacts.add(SelectedTIContacts.Model(contact, contact.id))
  }

  fun removeSelectedContact(contact: Recipient): Int {
    val c = SelectedTIContacts.Model(contact, contact.id)
    val removed = selectedContacts.remove(c)
    return if (removed) 1 else 0
  }

  fun isSelectedContact(contact: Recipient): Boolean {
    val c = SelectedTIContacts.Model(contact, contact.id)
    return Objects.requireNonNull(selectedContacts).contains(c)
  }

  val selectedContactsCount: Int
    get() = Objects.requireNonNull(selectedContacts).size


  fun listSelectedContactModels(): List<SelectedTIContacts.Model> {
    return selectedContacts
  }

  private fun listSelectedContactIds(): List<Recipient> {
    val selected = ArrayList<Recipient>()
    for (m in selectedContacts) {
      selected.add(m.selectedContact)
    }
    return selected
  }


  fun setQueryFilter(filter: String) {
    this.filter.value = filter
  }

  fun getFilter(): LiveData<String> {
    return this.filter
  }

  private fun loadValidContacts() {
    manager.getValidContacts { value: List<Recipient> -> introducibleContacts.postValue(value) }
  }

  val contacts: LiveData<List<Recipient>>
    get() = introducibleContacts

  fun getDialogStateForSelectedContacts(callback: Consumer<IntroduceDialogMessageState?>) {
    SimpleTask.run(
      {
        val selection = listSelectedContactIds()
        IntroduceDialogMessageState(resolved(manager.recipientId), selection)
      },
      { value: IntroduceDialogMessageState? -> callback.accept(value) }
    )
  }

  // TODO: Opted to use recipients directly instead of the SelectedContact class..
  // May need to reconsider if there are performance issues during integration testing.
  class IntroduceDialogMessageState(@JvmField val recipient: Recipient, @JvmField val toIntroduce: List<Recipient>)


  internal class Factory(id: RecipientId?) : ViewModelProvider.Factory {
    private val manager = ContactsSelectionManager(id!!, tiIdentityTable)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return Objects.requireNonNull(modelClass.cast(ContactsSelectionViewModel(manager)))
    }
  }
}

