package org.thoughtcrime.securesms.trustedIntroductions

import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientDetails
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.libsignal.util.guava.Optional

// Define Mock Data
// TODO: Could I use Mockito for this? Yes ^^
object FakeRecipients {
  var numbers = 0..50
  var recipientIDs = numbers.map { number -> RecipientId.from(number.toLong()) }
  var vibrateState = RecipientDatabase.VibrateState.DEFAULT
  var registeredState = RecipientDatabase.RegisteredState.UNKNOWN
  var verificationStates = IdentityDatabase.VerifiedStatus.values()
  var syncExtras = verificationStates.map { state ->  RecipientRecord.SyncExtras(null, null, null, state, false, false)}
  var names = listOf("Peter", "Anna", "Zarathustra")
  var recipientRecords = numbers.map{
    n -> RecipientRecord(recipientIDs[n], null, null, names[n%names.size]+n.toString(), null, null, null,
    RecipientDatabase.GroupType.NONE, false, 0, vibrateState, vibrateState, null, null, 1, 1, registeredState,
    null, null, ProfileName.EMPTY, null, null, null, null, ProfileName.EMPTY, null,
    false, false, 0, null, RecipientDatabase.UnidentifiedAccessMode.UNKNOWN, false, 0,
    Recipient.Capability.UNKNOWN, Recipient.Capability.UNKNOWN, Recipient.Capability.UNKNOWN, Recipient.Capability.UNKNOWN, Recipient.Capability.UNKNOWN, RecipientDatabase.InsightsBannerTier.NO_TIER,
    null, RecipientDatabase.MentionSetting.ALWAYS_NOTIFY, null, null, AvatarColor.A100, null, null, syncExtras[n% syncExtras.size], null, false,
    listOf<Badge>())
  }
  var recipients = numbers.map {
    n -> Recipient(recipientIDs[n], RecipientDetails(null, names[n%names.size]+n.toString()+"system", Optional.absent(), false, false,
    RecipientDatabase.RegisteredState.UNKNOWN, recipientRecords[n], null), false)
  }
}

