package org.thoughtcrime.securesms.trustedIntroductions

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.os.Build
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import kotlin.math.abs

object RelativeTimestamp {
  fun getRelativeTime(context: Context, timestamp: Long): String {
    val now = System.currentTimeMillis()

//    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//      // API level 24 or higher (RelativeDateTimeFormatter)
//      getRelativeTimeWithRelativeDateTimeFormatter(timestamp, now)
//    } else {
      // Fallback for API level 21-23 (DateUtils)
    return getRelativeTimeWithDateUtils(context, timestamp)
//    }
  }

  // For API level 24 or higher
  @RequiresApi(Build.VERSION_CODES.N)
  private fun getRelativeTimeWithRelativeDateTimeFormatter(timestamp: Long, now: Long): String {
    val delta = timestamp - now

    val locale = ULocale.getDefault() // Get the device's default locale
    val formatter = RelativeDateTimeFormatter.getInstance()

    // TODO: the underlying API is very basic. A way to get the right RelativeUnit based on the (absolute) delta would be good...
    return if (delta > 0) {
      // Future timestamp
      formatter.format(abs(delta).toDouble(), RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS)
    } else if (delta < 0) {
      // Past timestamp
      formatter.format(abs(delta.toDouble()), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.DAYS)
    } else {
      // not sure it can happen in practice
      formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
    }
  }

  // For API level 21-23 (fallback)
  private fun getRelativeTimeWithDateUtils(context: Context, timestamp: Long): String {
    val locale = Locale.getDefault();
    return DateUtils.getBriefRelativeTimeSpanString(context, locale, timestamp)
  }
}