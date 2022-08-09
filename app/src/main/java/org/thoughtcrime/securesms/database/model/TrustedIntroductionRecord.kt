package org.thoughtcrime.securesms.database.model

import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Database model for [TrustedIntroductionsDatabase].
 * We only store incoming introductions. The identity key is not part of the record and only used to determine the state.
 * // TODO: This would have to change if we want to include the ability to "revive" stale verifications.
 * @See TrustedIntroductionsDatabase
 */
data class TrustedIntroductionRecord(
  val introducingRecipientId: RecipientId?,
  val introduceeRecipientId: RecipientId,
  val timestamp: Long,
  val recordState: TrustedIntroductionsDatabase.State,

)
