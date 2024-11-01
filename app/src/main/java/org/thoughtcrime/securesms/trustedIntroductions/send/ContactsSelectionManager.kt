package org.thoughtcrime.securesms.trustedIntroductions.send

import androidx.core.util.Consumer
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue.getValidTICandidates

class ContactsSelectionManager internal constructor(// This is the person which will receive the security numbers of the selected contacts through
  // a secure introduction.
  val recipientId: RecipientId, // Dependency injection makes the class testable
  private val idb: IdentityTableGlue
) {

  fun getValidContacts(introducibleContacts: Consumer<List<Recipient>>) {
    SignalExecutors.BOUNDED.execute {
      val eligibleCandidates = getValidTICandidates(idb.getCursorForTIUnlocked())

      if (eligibleCandidates.isEmpty()) {
        introducibleContacts.accept(emptyList())
        return@execute
      }

      eligibleCandidates
        .asSequence()
        .filter { (id, _) -> id != recipientId }
        .map { (id, _) -> Recipient.resolved(id) }
        .sortedBy { it.profileName.toString() }
        .toList()
        .let { introducibleContacts.accept(it) }
    }
  }

//  fun getValidContacts(introducibleContacts: Consumer<List<Recipient>?>) {
//    SignalExecutors.BOUNDED.execute {
//      val eligibleCandidates = getValidTICandidates(idb.cursorForTIUnlocked)
//      val count = eligibleCandidates.size
//      if (count == 0) {
//        introducibleContacts.accept(emptyList())
//      } else {
//        val contacts: MutableList<Recipient> = ArrayList()
//        eligibleCandidates.forEach { (recipientID: RecipientId?, recipientRecord: RecipientRecord?) ->
//          if (recipientID.compareTo(this.recipientId) != 0) {
//            contacts.add(resolved(recipientID))
//          }
//        }
//        // sort ascending
//        Collections.sort(
//          contacts,
//          Comparator.comparing { recipient: Recipient -> recipient.profileName.toString() }
//        )
//        introducibleContacts.accept(contacts)
//      }
//    }

//  fun getRecipientId(): RecipientId = recipientId
}
