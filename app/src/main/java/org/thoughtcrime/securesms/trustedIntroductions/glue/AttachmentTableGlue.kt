/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.os.Build
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset

object AttachmentTableGlue {


  /**
   * TODO
   *
   * @param existingPlaceholder the attachment to be evaluated
   * @return a ByteinputStream containing the contents of the attachment
   */
  @JvmStatic
  fun grabIntroductionData(existingPlaceholder: DatabaseAttachment, inputStream: InputStream): InputStream{
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
      //TODO: Start the job that processes the trusted introductions
    }
    return if (text.isBlank()) inputStream else text.byteInputStream()
  }

}