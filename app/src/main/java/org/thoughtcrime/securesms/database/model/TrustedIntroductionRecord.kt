package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Database model for [TI_Database].
 * We only store incoming introductions. The identity key is not part of the record and only used to determine the state.
 * // TODO: This would have to change if we want to include the ability to "revive" stale verifications.
 * @See TI_Database
 */
data class TrustedIntroductionRecord(
  val introducingRecipientId: RecipientId?,
  val introduceeRecipientId: RecipientId,
  val timestamp: Long,
  val recordState: TI_Database.State
)
