package org.thoughtcrime.securesms.trustedIntroductions

import org.thoughtcrime.securesms.database.TrustedIntroductionsDatabase
import java.sql.Timestamp


data class TI_Data (val id: String?, val state: TrustedIntroductionsDatabase.State?, val name: String, val phone: String, val identityKey: String, val securityNumber: String, val timestamp: Long)