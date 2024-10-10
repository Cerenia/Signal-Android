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
   * TODO
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