package org.thoughtcrime.securesms.trustedIntroductions

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.Serializable;

// introduceeIdentityKey is encoded in Base64 (this is how it is currently stored in the Identity Database) @see TI_Utils.encodeIdentityKey
// TODO: if Java serialization does not work out of the box, use Json: https://kotlinlang.org/docs/serialization.html#example-json-serialization
data class TI_Data (val id: String?, val state: TrustedIntroductionsDatabase.State?, val introducerId: RecipientId, val introduceeId: RecipientId?, val introduceeServiceId: String, val introduceeName: String, val introduceeNumber: String, val introduceeIdentityKey: String, var predictedSecurityNumber: String?, val timestamp: Long) : Serializable