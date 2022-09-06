package org.thoughtcrime.securesms.trustedIntroductions

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

// introduceeIdentityKey is encoded in Base64 (this is how it is currently stored in the Identity Database)
data class TI_Data (val id: String?, val state: TrustedIntroductionsDatabase.State?, val introducerId: RecipientId, val introduceeId: RecipientId?,val introduceeName: String, val introduceePhone: String, val introduceeIdentityKey: String, val predictedSecurityNumber: String, val timestamp: Long)