package org.thoughtcrime.securesms.trustedIntroductions

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

// introduceeIdentityKey is encoded in Base64 (this is how it is currently stored in the Identity Database) @see TI_Utils.encodeIdentityKey
data class TI_Data (val id: String?, val state: TrustedIntroductionsDatabase.State?, val introducerId: RecipientId, val introduceeId: RecipientId?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long)