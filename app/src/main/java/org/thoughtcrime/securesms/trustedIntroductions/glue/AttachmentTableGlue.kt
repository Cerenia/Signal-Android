/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import java.io.ByteArrayOutputStream
import java.io.InputStream

object AttachmentTableGlue {


  /**
   * TODO
   *
   * @param existingPlaceholder the attachment to be evaluated
   * @return a ByteinputStream containing the contents of the attachment
   */
  @JvmStatic
  fun grabIntroductionData(existingPlaceholder: DatabaseAttachment, inputStream: InputStream, uploadTimestamp: Long): InputStream {
    var text: String = ""
    if(existingPlaceholder.fileName!!.contains(".trustedintro")){
      val byteOutputStream = ByteArrayOutputStream()
      inputStream.use {
        byteOutputStream.use { output ->
          inputStream.copyTo(output)
        }
      }
      byteOutputStream.use { stream -> {
        text = stream.toString()
        }
      }
      handleTIMessage(text, uploadTimestamp)
    }
    return if (text.isBlank()) inputStream else text.byteInputStream()
  }

  fun handleTIMessage(message: String, timestamp: Long) {
    if (!message.contains(TI_Utils.TI_IDENTIFYER)) return
    TI_Utils.handleTIMessage(message, timestamp)
  }

}