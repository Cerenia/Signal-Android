package org.thoughtcrime.securesms.trustedIntroductions.database

import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.recipients.RecipientId

data class TI_IdentityStoreRecord(
  val addressName: String,
  val identityKey: IdentityKey,
  val verifiedStatus: TI_IdentityTable.VerifiedStatus,
  val firstUse: Boolean,
  val timestamp: Long,
  val nonblockingApproval: Boolean
) {
  fun toIdentityRecord(recipientId: RecipientId): TI_IdentityRecord {
    return TI_IdentityRecord(
      recipientId = recipientId,
      identityKey = identityKey,
      verifiedStatus = verifiedStatus,
      firstUse = firstUse,
      timestamp = timestamp,
      nonblockingApproval = nonblockingApproval
    )
  }
}
