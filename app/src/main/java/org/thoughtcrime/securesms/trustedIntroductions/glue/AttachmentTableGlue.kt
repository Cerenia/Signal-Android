/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import org.signal.core.util.bytes
import org.signal.core.util.stream.LimitedInputStream
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import java.io.ByteArrayOutputStream

object AttachmentTableGlue {


  /**
   * Given a (maybe?) message attachment, checks if it might be trusted introduction data (by checking file extension)
   * and if yes attempts to read the data and pass it to the handler.
   * Finally we return an `inputStream` since we don't want to disturb the control flow of the caller
   * (and any given `inputStream` can only be read once).
   *
   * @param attachment the attachment to be evaluated
   * @return a `ByteInputStream` containing the contents of the attachment
   */
  @JvmStatic
  fun grabIntroductionData(attachment: Attachment, inputStream: LimitedInputStream): LimitedInputStream {
    var text = ""
    if(attachment.fileName!!.contains(TI_Utils.TI_MESSAGE_EXTENSION)){
      val byteOutputStream = ByteArrayOutputStream()
      inputStream.use {
        byteOutputStream.use { output ->
          inputStream.copyTo(output)
        }
      }
      text = byteOutputStream.toString()
      handleTIMessage(text, attachment.uploadTimestamp)
    }
    return if (text.isBlank()) inputStream else LimitedInputStream(text.byteInputStream(), text.length.bytes.bytes)
  }

  private fun handleTIMessage(message: String, timestamp: Long) {
    if (!message.contains(TI_Utils.TI_IDENTIFIER)) return
    TI_Utils.handleTIMessage(message, timestamp)
  }

}
