package org.thoughtcrime.securesms.trustedIntroductions

import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.RecipientDatabaseTestUtils
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

// Define Mock Data
object FakeRecipients {
  var numbers = 1..50
  var recipientIDs = numbers.map { number -> RecipientId.from(number.toLong()) }
  var verificationStates = IdentityDatabase.VerifiedStatus.values()
  var syncExtras = verificationStates.map { state ->  RecipientRecord.SyncExtras(null, null, null, state, false, false)}
  var names = listOf("Peter", "Anna", "Zarathustra")
  var recipients = numbers.map{ n->
    RecipientDatabaseTestUtils.createRecipient(recipientId = recipientIDs[n], signalProfileName = ProfileName.fromParts(names[n%names.size],n.toString()), syncExtras = syncExtras[n%syncExtras.size])
  }
}




