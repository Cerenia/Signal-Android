package org.thoughtcrime.securesms.trustedIntroductions

import java.sql.Timestamp


data class TI_Data (val id: String?, val name: String, val phone: String, val identityKey: String, val securityNumber: String, val timestamp: Long)