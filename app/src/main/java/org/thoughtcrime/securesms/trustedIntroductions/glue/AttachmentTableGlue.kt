/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import androidx.annotation.NonNull
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import java.io.ByteArrayOutputStream
import java.io.InputStream

object AttachmentTableGlue {


  /**
   * Given a message attachment, checks if it might be trusted introduction data (by checking file extension)
   * and if yes attempts to read the data and pass it to the handler.
   * Finally we return an inputstream since we don't want to disturb the control flow of the caller 
   * (and any given inputstream can only be read once).
   *
   * @param attachment the attachment to be evaluated
   * @return a ByteinputStream containing the contents of the attachment
   */
  @JvmStatic
  fun grabIntroductionData(attachment: Attachment, inputStream: InputStream): InputStream {
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
    return if (text.isBlank()) inputStream else text.byteInputStream()
  }

  fun handleTIMessage(message: String, timestamp: Long) {
    if (!message.contains(TI_Utils.TI_IDENTIFYER)) return
    TI_Utils.handleTIMessage(message, timestamp)
  }

}
