package org.thoughtcrime.securesms.trustedIntroductions

import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.Base64.encodeWithoutPadding
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus.Companion.forState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies.application
import org.thoughtcrime.securesms.dependencies.AppDependencies.jobManager
import org.thoughtcrime.securesms.dependencies.AppDependencies.protocolStore
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob
import org.thoughtcrime.securesms.recipients.Recipient.Companion.live
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue.VerifiedStatus
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue.getRecordsForReceivingTI
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue.getRecordsForSendingTI
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TrustedIntroductionsReceiveJob
import org.thoughtcrime.securesms.util.IdentityUtil
import org.whispersystems.signalservice.api.push.ServiceId.Companion.parseOrThrow
import org.whispersystems.signalservice.api.util.Preconditions
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// TODO: May be able to simplify further by using JsonUtil.java in codebase...
// Serialization for each object that I am sending is already present..
object TI_Utils {
  // Prefix all logging with this tag for ease of search
  const val TI_LOG_TAG: String = "_TI:%s"
  val TAG: String = String.format(TI_LOG_TAG, Log.tag(TI_Utils::class.java))

  // Version, change if you change data/message format for compatibility
  // TODO: this is currently only reflected in message format, would need to add this to Database to make
  // Backup/Restore work across revisions
  const val TI_MESSAGE_VERSION: String = "2.0"

  // Since the Signal version is still important and will not be overwritten I define my own
  // 1: major changes, 2: feature/ui changes , 3. bugs | stability fixes
  const val TI_APK_VERSION: String = "2.1.2"

  // text is the interim solution. In the future a custom mimetype should be used such that we can release a
  // custom interpreter that can be used by people that do not have the TI_extension installed.
  const val TI_MIME_TYPE: String = "text/plain"
  const val TI_MESSAGE_EXTENSION: String = ".trustedintro"
  const val TI_MESSAGE_FILENAME: String = "Signal$TI_MESSAGE_EXTENSION"

  // Random String to mark a message as a trustedIntroduction, since I'm tunneling through normal messages
  const val TI_IDENTIFIER: String = "QOikEX9PPGIuXfiejT9nC2SsDB8d9AG0dUPQ9gERBQ8qHF30Xj --- This message is part of an experimental feature and not meant to be read by humans --- Introduction Data:\n"

  // This should be added as a comment above and below each executed glue line in the Signal codebase
  // will aid in applying glue logic mechanically further down the line.
  // "TI_GLUE: eNT9XAHgq0lZdbQs2nfH /start"
  // "TI_GLUE: eNT9XAHgq0lZdbQs2nfH /end"
  const val TI_GLUE_START: String = "TI_GLUE: eNT9XAHgq0lZdbQs2nfH start"
  const val TI_GLUE_END: String = "TI_GLUE: eNT9XAHgq0lZdbQs2nfH end"
  const val TI_SEPARATOR: String = "\n" // marks start of JsonArray, human friendly
  const val INDENT_SPACES: Int = 1 // pretty printing for human readableness

  // For safety_number generation
  // @see VerifyDisplayFragment, iterations hardcoded there
  const val ITERATIONS: Int = 5200

  // @See length of codes in VerifyDisplayFragment
  const val SEGMENTS: Int = 12

  const val UNDISCLOSED: String = "undisclosed"

  // Json keys
  // TODO: May want to add that to be part of the introduction at some point. This way we can avoid crashed on importing old backups with version mismatches
  const val TI_VERSION_J: String = "ti_version"
  const val INTRODUCER_J: String = "introducer"
  const val INTRODUCEE_DATA_J: String = "introducees"
  const val SERVICE_ID_J: String = "service_ID"
  const val NAME_J: String = "name"
  const val NUMBER_J: String = "number"
  const val IDENTITY_J: String = "identity_key_base64"
  const val PREDICTED_FINGERPRINT_J: String = "safety_number"

  // Job constants
  @JvmField
  val TI_JOB_LIFESPAN: Long = TimeUnit.DAYS.toMillis(1)

  //public static final int TI_JOB_MAX_ATTEMPTS = Job.Parameters.UNLIMITED;
  const val TI_JOB_MAX_ATTEMPTS: Int = 10

  // How to format dates in introductions:
  @JvmField
  @SuppressLint("SimpleDateFormat")
  val INTRODUCTION_DATE_PATTERN: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm:ss")

  /**
   * /@see ManageListFragment::getFiltered()
   *
   * @param timestamp the timestamp as long
   * @return The date in 6 parts, in order of the format string, as strings
   */
  @JvmStatic
  fun splitIntroductionDate(timestamp: Long): TimestampDateParts {
    val date = INTRODUCTION_DATE_PATTERN.format(timestamp)
    val dateTime = date.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val dateParts = dateTime[0].split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val timeParts = dateTime[1].split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return TimestampDateParts(
      dateParts[0],
      dateParts[1],
      dateParts[2],
      timeParts[0],
      timeParts[1],
      timeParts[2]
    )
  }

  /**
   * @see VerifyDisplayFragment
   */
  private fun getFormattedSafetyNumbers(fingerprint: Fingerprint): String {
    val segments = getSegments(fingerprint)
    val result = StringBuilder()

    for (i in segments.indices) {
      result.append(segments[i])

      if (i != segments.size - 1) {
        result.append(' ')
      }
    }

    return result.toString()
  }

  /**
   * @see VerifyDisplayFragment
   */
  private fun getSegments(fingerprint: Fingerprint): Array<String?> {
    val segments = arrayOfNulls<String>(SEGMENTS)
    val digits = fingerprint.displayableFingerprint.displayText
    val partSize = digits.length / SEGMENTS

    for (i in 0 until SEGMENTS) {
      segments[i] = digits.substring(i * partSize, (i * partSize) + partSize)
    }

    return segments
  }

  /**
   * Recreates the safety number that is generated between two recipients.
   * (used when sending intro, and to conveniently compute difference on conflict to expose in UI)
   * PRE: Nullable parameters must either ALL BE NULL or NONE BE NULL.
   *
   * @param introductionRecipientId first Recipient
   * @param introduceeId            second Recipient (introducee) => Must be present in the local database!
   * @param maybeIntroducerServiceId,    fetched if null and needed
   * @param introduceeIdentityKey   fetched if null
   * @return The expected safety number as a String, formated into segments identical to the VerifyDisplayFragment TODO: fix wacky formatting (some whitespaces missing)
   */
  private fun predictFingerprint(introductionRecipientId: RecipientId, introduceeId: RecipientId, maybeIntroducerServiceId: String?, introduceeIdentityKey: IdentityKey?): String {
    var introduceeServiceId = maybeIntroducerServiceId
    if (introduceeServiceId == null && introduceeIdentityKey == null) {
      // Fetch all the values
      val liveIntroducee = live(introduceeId)
      val introduceeResolved = liveIntroducee.resolve()
      introduceeServiceId = introduceeResolved.serviceId.toString()
    } else if (introduceeServiceId != null && introduceeIdentityKey != null) {
      //noop, normal case when recipient fetched through cursor
      Log.i(TAG, " hit a noop: recipient fetched through cursor (no introducee ServiceId or IdentityKey)")
    } else {
      // TODO: Does that make sense??
      throw AssertionError("$TAG Unexpected non-null parameter in TI_Utils.predictFingerprint")
    }
    // Initialize introduction recipients id & key
    val introductionRecipientFingerprintId: ByteArray
    val live = live(introductionRecipientId)
    val introductionRecipientResolved = live.resolve()
    val generator = NumericFingerprintGenerator(ITERATIONS)
    Log.i(TAG, "using " + introductionRecipientResolved.requireServiceId())
    introductionRecipientFingerprintId = introductionRecipientResolved.requireServiceId().toByteArray()
    val introduceeFingerprintId = introduceeServiceId.toByteArray()
    val introductionRecipientIdentityKey: IdentityKey
    try {
      introductionRecipientIdentityKey = getIdentityKey(introductionRecipientId)
    } catch (e: MissingIdentityException) {
      Log.e(TAG, e.toString())
      throw AssertionError("$TAG The key of the introduction recipient must be present in the database at this stage. RecipientID: $introductionRecipientId")
    }

    // @see VerifyDisplayFragment::initializeFingerprint(), iterations there also hardcoded to 5200 for FingerprintGenerator
    // @see ServiceId.java to understand how they convert the ACI to ByteArray
    // @see IdentityKey.java
    // Only version 2 is used since the migration to usernames
    val fingerprint = generator.createFor(
      2,
      introductionRecipientFingerprintId,
      introductionRecipientIdentityKey,
      introduceeFingerprintId,
      introduceeIdentityKey
    )
    return getFormattedSafetyNumbers(fingerprint).replace("\n", "")
  }

  /**
   * Also used in  TrustedIntroductionsDatabase
   *
   * @param id recipient ID
   * @return their identity as saved in the Identity database
   */
  @Throws(MissingIdentityException::class)
  fun getIdentityKey(id: RecipientId): IdentityKey {
    val identityRecord = protocolStore.aci().identities().getIdentityRecord(id)
    if (identityRecord.isEmpty) {
      throw MissingIdentityException("$TAG No identity found for the recipient with id: $id")
    }
    return identityRecord.get().identityKey
  }

  fun encodeIdentityKey(key: IdentityKey): String {
    return encodeWithoutPadding(key.serialize())
  }

  @JvmStatic
  @Throws(MissingIdentityException::class)
  fun getEncodedIdentityKey(id: RecipientId): String {
    return encodeIdentityKey(getIdentityKey(id))
  }

  @SuppressLint("Range")
  @WorkerThread
  @Throws(JSONException::class)
  @JvmStatic
  fun buildMessageBody(introducerRecipientId: RecipientId, introductionRecipientId: RecipientId, introducees: Set<RecipientId?>): String {
    if (introducees.isEmpty()) {
      throw AssertionError("$TAG buildMessageBody called with no Introducees!")
    }

    val data = JSONObject()

//    val recipients = getRecordsForSendingTI(introducees)
    val recipients = getRecordsForSendingTI(introducees.filterNotNull().toSet())
    if (recipients.isEmpty()) {
      throw AssertionError("$TAG buildMessageBody - no recipients!")
    }

    data.put(TI_VERSION_J, TI_MESSAGE_VERSION)

    // create Introducer entry
    val introducer = JSONObject()
    val resolvedIntroducer = live(introducerRecipientId).get()

    introducer.put(NAME_J, getSomeNonNullName(introducerRecipientId, SignalDatabase.recipients.getRecord(introducerRecipientId)))
    introducer.put(NUMBER_J, if (resolvedIntroducer.e164.isEmpty) UNDISCLOSED else resolvedIntroducer.e164.get())
    introducer.put(SERVICE_ID_J, if (resolvedIntroducer.serviceId.isEmpty) UNDISCLOSED else resolvedIntroducer.serviceId.get().toString())
    try {
      introducer.put(
        PREDICTED_FINGERPRINT_J, predictFingerprint(
          introducerRecipientId,
          introductionRecipientId,
          live(introductionRecipientId).get().requireServiceId().toString(),
          getIdentityKey(introductionRecipientId)
        )
      )
    } catch (e: MissingIdentityException) {
      // should never be the case with the introducer
      throw AssertionError("$TAG My own identity key cannot be missing! ")
    }
    try {
      introducer.put(IDENTITY_J, encodeIdentityKey(getIdentityKey(introducerRecipientId)))
    } catch (e: MissingIdentityException) {
      // should never be the case with the introducer
      throw AssertionError("$TAG The introducers Identity cannot be missing! $introducerRecipientId cannot be an introducer!")
    }
    data.put(INTRODUCER_J, introducer)

    // Now do the same for all introducees and wrap them in an array
    val introduceeData = JSONArray()
    recipients.forEach { (recipientId: RecipientId?, recipientRecord: RecipientRecord?) ->
      try {
        val introducee = JSONObject()
        introducee.put(NAME_J, getSomeNonNullName(recipientId, recipientRecord))
        val introduceeE164 = recipientRecord.e164 ?: UNDISCLOSED
        introducee.put(NUMBER_J, introduceeE164)
        val introduceeServiceId = recipientRecord.aci ?: throw AssertionError(TAG + "Introducee service ID may not be null.")
        introducee.put(SERVICE_ID_J, introduceeServiceId)
        val formatedSafetyNR: String
        try {
          val introduceeIdentityKey = getIdentityKey(recipientId)
          introducee.put(IDENTITY_J, encodeIdentityKey(introduceeIdentityKey))
          formatedSafetyNR = predictFingerprint(introductionRecipientId, recipientId, introduceeServiceId.toString(), introduceeIdentityKey)
        } catch (e: MissingIdentityException) {
          throw AssertionError("$TAG Unexpected missing identities when building TI message body!\n ${e.stackTraceToString()}")
        }
        introducee.put(PREDICTED_FINGERPRINT_J, formatedSafetyNR)
        introduceeData.put(introducee)
        data.put(INTRODUCEE_DATA_J, introduceeData)
      } catch (e: JSONException) {
        throw AssertionError("$TAG Json Error occurred while building TI_message body.\n ${e.stackTraceToString()}")
      }
    }
    return TI_IDENTIFIER + TI_SEPARATOR + data.toString(INDENT_SPACES)
  }


  private fun getSomeNonNullName(id: RecipientId, record: RecipientRecord): String {
    var name = record.systemDisplayName
    if (!name.isNullOrEmpty()) {
      return name
    }
    name = record.username
    if (!name.isNullOrEmpty()) {
      return name
    }
    name = record.email
    if (!name.isNullOrEmpty()) {
      return name
    }
    val rp = resolved(id)
    name = rp.getDisplayName(application.applicationContext)
    if (name.isNotEmpty()) {
      return name
    }
    name = rp.profileName.toString()
    if (name.isNotEmpty()) {
      return name
    }
    return "¯\\_(ツ)_/¯"
  }

  // This structure allows for a oneliner in the processing logic to minimize additional code needed in there.
  fun handleTIMessage(message: String, timestamp: Long) {
    // Schedule Reception Job
    jobManager.add(TrustedIntroductionsReceiveJob(message, timestamp))
  }


  /**
   * @param id recipient Id for which the cache should be queried.
   * @return ACI as string if present, null otherwise
   */
  private fun getServiceIdFromRecipientId(id: RecipientId): String? {
    return if (live(id).resolve().serviceId.isPresent) {
      live(id).resolve().serviceId.get().toString()
    } else {
      null
    }
  }

  /**
   * PRE: message is a valid TI message (contains identifier)
   *
   * @param message the body of the .trustedintro attachment
   * @return a parsed JSONObject or null if there was a version mismatch
   */
  @Throws(JSONException::class)
  private fun getPureJson(message: String): JSONObject? {
    Preconditions.checkArgument(message.contains(TI_IDENTIFIER))
    if (isCorrectTImessageVersion(message)) {
      return JSONObject(message.replace(TI_IDENTIFIER, ""))
    } else {
      Log.e(
        TAG, """
   Invalid TI_message for the following body:
   $message
   
   --> The current version should be: $TI_MESSAGE_VERSION
   
   """.trimIndent()
      )
      return null
    }
  }

  /**
   * @param message the TI message (content of .trustedintro file)
   * @return True if the current TI_version is present in the message, false otherwise
   */
  private fun isCorrectTImessageVersion(message: String): Boolean {
    return message.contains(String.format(Locale.getDefault(), "\"ti_version\": \"%s\"", TI_MESSAGE_VERSION)) && message.contains(TI_IDENTIFIER)
  }

  /**
   * Parses the introducer recipient ID from the raw TI_message if possible, else null
   *
   * @param message the TI message (content of .trustedintro file)
   * @return the RecipientId of the introducer or null if there was a version mismatch
   */
  @JvmStatic
  fun getIntroducerFromRawMessage(message: String): RecipientId? {
    try {
      val jsonData = getPureJson(message)
      if (jsonData != null) {
        val introducer = JSONObject(jsonData.getString(INTRODUCER_J))
        return RecipientId.from(parseOrThrow(introducer.getString(SERVICE_ID_J)))
      }
    } catch (e: JSONException) {
      Log.e(TAG, "A JsonException occurred for the following TI message body: \n$message")
      e.printStackTrace()
    }
    return null
  }


  /**
   * Parses an incoming TI message to create introduction data
   * PRE: body is a valid TI message with the correct version.
   *
   * @param body         of the incoming message
   * @param timestamp    when message was received
   * @param introducerId whom the message came from
   * @return populated List<TI_Data> if successful, null otherwise
  </TI_Data> */
  @JvmStatic
  @WorkerThread
  @SuppressLint("Range") // keywords exists
  fun constructIntroduceesFromTrustedIntrosString(body: String, timestamp: Long, introducerId: RecipientId): List<TI_Data>? {
    if (!body.contains(TI_IDENTIFIER) || !isCorrectTImessageVersion(body)) {
      throw AssertionError("Non TI message passed into constructIntroducees!")
    }
    val introducerServiceId = getServiceIdFromRecipientId(introducerId)
    val result = ArrayList<TI_Data>()
    try {
      val data = getPureJson(body)
        ?: // For now we just ignore introductions with mismatched versions or invalid bodies
        return null
      val introducees = data.getJSONArray(INTRODUCEE_DATA_J)
      val idKeyPairs = ArrayList<IdKeyPair>()
      val recipientServiceIds: MutableList<String> = ArrayList()
      // Get all ServiceIds of introducees first to minimize database Queries
      for (i in 0 until introducees.length()) {
        val o = introducees.getJSONObject(i)
        val introduceeServiceId = o.getString(SERVICE_ID_J)
        idKeyPairs.add(IdKeyPair(introduceeServiceId, o.getString(IDENTITY_J)))
        recipientServiceIds.add(introduceeServiceId)
      }
      // Get any known recipients & add to result
      val records = getRecordsForReceivingTI(recipientServiceIds)
      val knownIds = ArrayList<String>()
      if (records.isNotEmpty()) {
        records.forEach { (recipientID: RecipientId?, recipientRecord: RecipientRecord?) ->
          val introduceeServiceId = recipientRecord.aci.toString()
          knownIds.add(introduceeServiceId)
          val name = getSomeNonNullName(recipientID, recipientRecord)
          val phone = if (UNDISCLOSED == recipientRecord.e164) null else recipientRecord.e164
          val identityKey = IdKeyPair.findCorrespondingKeyInList(introduceeServiceId, idKeyPairs)
          val d = TI_Data(null, TI_Database.State.PENDING, introducerServiceId, introduceeServiceId, name, phone, identityKey, null, timestamp)
          result.add(d)
        }
      }
      // Iterate through JSONData again, create the introductions for the still unknown recipients & set the predictedSecurityNumbers for all
      for (i in 0 until introducees.length()) {
        val o = introducees.getJSONObject(i)
        // If data was fetched from local database, simply add the Security number information
        val introduceeServiceId = o.getString(SERVICE_ID_J)
        if (knownIds.contains(introduceeServiceId)) {
          var j = 0
          while (result[j].introduceeServiceId != introduceeServiceId) j++
          if (j >= result.size) {
            throw AssertionError("Known Id not found in the original JSON Data")
          }
          result[j].predictedSecurityNumber = o.getString(PREDICTED_FINGERPRINT_J)
        } else {
          val d = TI_Data(null, TI_Database.State.PENDING, introducerServiceId, o.getString(SERVICE_ID_J), o.getString(NAME_J), o.getString(NUMBER_J), o.getString(IDENTITY_J), o.getString(PREDICTED_FINGERPRINT_J), timestamp)
          result.add(d)
        }
      }
    } catch (e: JSONException) {
      Log.e(TAG, String.format("A JSON exception occurred while trying to parse the TI message: %s", body))
      return null // unsuccessful parse
    }
    return result
  }

  private fun getPhone(introducees: JSONArray, introuceeServiceId: String): String {
    try {
      for (i in 0 until introducees.length()) {
        val o = introducees.getJSONObject(i)
        if (o.getString(SERVICE_ID_J) == introuceeServiceId) {
          return o.getString(NUMBER_J)
        }
      }
    } catch (e: JSONException) {
      Log.i(
        TAG, """
   Error while extracting phone nr. from introduction. Using placeholder.
   ${e.stackTrace.contentToString()}
   """.trimIndent()
      )
    }
    return "missing"
  }

  /**
   * //TODO: What are generally sensible queue strings? Does it matter if there are multiple vs. one queue for our purposes (e.g., multiple incoming)?
   *
   * @param o Object, must be serializable.
   * @return A string that can be used for the Job queue key.
   */
  @JvmStatic
  fun serializeForQueue(o: Any?): String {
    val md: MessageDigest
    val hashText: String
    try {
      md = MessageDigest.getInstance("MD5")
      // Serialize introducee Set
      val bos = ByteArrayOutputStream()
      val oos = ObjectOutputStream(bos)
      oos.writeObject(o)
      oos.flush()
      // Create digest and convert to Hex String
      val digest = md.digest(bos.toByteArray())
      val no = BigInteger(1, digest)
      hashText = no.toString(16)
    } catch (e: NoSuchAlgorithmException) {
      Log.e(
        TAG, """
   $e
   ${e.message}
   """.trimIndent()
      )
      throw AssertionError("No such Algorithm!")
    } catch (ioe: IOException) {
      Log.e(
        TAG, """
   $ioe
   ${ioe.message}
   """.trimIndent()
      )
      throw AssertionError("IO exception!")
    }
    return hashText
  }

  /**
   * Spawns it's own thread.
   * Used both by verifyDisplayFragment and Introduction database.
   * TODO: Should this be a job for persistence?
   *
   * @param status The new verification status
   */
  @JvmStatic
  fun updateContactsVerifiedStatus(recipientId: RecipientId, remoteIdentity: IdentityKey, status: VerifiedStatus) {
    Log.i(TAG, "Saving identity: $recipientId")
    SignalExecutors.BOUNDED.execute {
      // Fetch remote identity
      val recipient = live(recipientId).resolve()
      ReentrantSessionLock.INSTANCE.acquire().use { _ ->
        // TI
        tiIdentityTable.setVerifiedStatus(recipientId, status)
        // Vanilla
//        val verified: Boolean = .VerifiedStatus.isVerified(status)
        val verified: Boolean = VerifiedStatus.isVerified(status)
        if (verified) {
          protocolStore.aci().identities()
            .saveIdentityWithoutSideEffects(
              recipientId,
              recipient.requireServiceId(),
              remoteIdentity,
              VerifiedStatus.toVanilla(status),
              false,
              System.currentTimeMillis(),
              true
            )
        } else {
          protocolStore.aci().identities()
            .setVerified(recipientId, remoteIdentity, forState(VerifiedStatus.toVanilla(status.toInt())))
        }
        // For other devices but the Android phone, we map the finer statuses to verified or unverified.
        // TODO: Change once we add new devices for TI
        jobManager
          .add(
            MultiDeviceVerifiedUpdateJob(
              recipientId,
              remoteIdentity,
              forState(VerifiedStatus.toVanilla(status.toInt()))
            )
          )
        StorageSyncHelper.scheduleSyncForDataChange()
        IdentityUtil.markIdentityVerified(application.applicationContext, recipient, verified, false)
      }
    }
  }

  /**
   * //TODO: May need some cashing in TA_Data here
   * Query db and check if recipient is present. If not return Unknown.
   * PRE: serviceId represents valid ACI
   */
  @JvmStatic
  @WorkerThread
  fun getRecipientIdOrUnknown(serviceId: String): RecipientId {
    val sId = parseOrThrow(serviceId)
    val recipients = recipients
    val rId = recipients.getByServiceId(sId)
    return rId.orElse(RecipientId.UNKNOWN)
  }

  class TimestampDateParts(@JvmField var year: String, @JvmField var month: String, @JvmField var day: String, @JvmField var hours: String, @JvmField var minutes: String, @JvmField var seconds: String)

  private class IdKeyPair(var id: String, var key: String) {
    companion object {
      fun findCorrespondingKeyInList(id: String, list: ArrayList<IdKeyPair>): String {
        for (p in list) {
          if (id == p.id) {
            return p.key
          }
        }
        throw AssertionError("$TAG The Id you were searching for was not found in the list!")
      }
    }
  }
}
