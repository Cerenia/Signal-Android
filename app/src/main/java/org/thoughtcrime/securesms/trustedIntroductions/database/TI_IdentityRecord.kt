package org.thoughtcrime.securesms.trustedIntroductions.database

import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.recipients.RecipientId

data class TI_IdentityRecord (
  val recipientId: RecipientId,
  val identityKey: IdentityKey,
  val verifiedStatus: TI_IdentityTable.VerifiedStatus,
  @get:JvmName("isFirstUse")
  val firstUse: Boolean,
  val timestamp: Long,
  @get:JvmName("isApprovedNonBlocking")
  val nonblockingApproval: Boolean
  )
