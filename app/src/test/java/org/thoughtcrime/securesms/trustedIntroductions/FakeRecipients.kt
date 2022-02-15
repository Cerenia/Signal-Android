package org.thoughtcrime.securesms.trustedIntroductions

import android.net.Uri
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.StringUtil

// Define Mock Data
open class FakeRecipientData {
  class FakeRecipient(private val id: Long, private val verificationState: IdentityDatabase.VerifiedStatus, private val name: String) {
    fun getId():
    Long{
      return id
    }
    fun isSelf():
    Boolean{
      return false
    }
    fun getVerificationState():
      IdentityDatabase.VerifiedStatus{
      return verificationState
    }
    fun getName():
    String{
      return name
    }
    fun toRecipient():
    Recipient{
      return RecipientDatabaseTestUtils.createRecipient(recipientId = RecipientId.from(getId()), signalProfileName = ProfileName.fromParts(getName(), ""), syncExtras = RecipientRecord.SyncExtras(null, null, null, getVerificationState(), false, false))
    }
  }

  companion object{
    var numbers = 1..50
    //var recipientIDs = numbers.map { number -> RecipientId.from(number.toLong()) }
    var verificationStates = IdentityDatabase.VerifiedStatus.values()
    //var syncExtras = verificationStates.map { state ->  RecipientRecord.SyncExtras(null, null, null, state, false, false)}
    var names = listOf("Peter", "Anna", "Zarathustra")
    var uri = Uri.EMPTY
    var recipients = numbers.map{ n->
      //RecipientDatabaseTestUtils.createRecipient(recipientId = recipientIDs[n-1], signalProfileName = ProfileName.fromParts(names[(n-1)%names.size],n.toString()), syncExtras = syncExtras[(n-1)%syncExtras.size], messageRingtone = Uri.EMPTY, callRingtone = Uri.EMPTY)
      FakeRecipient(n.toLong(), verificationStates[n% verificationStates.size], names[(n-1)%names.size])
    }
  }
}




