package org.thoughtcrime.securesms.trustedIntroductions.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.signal.core.util.SqlUtil.buildArgs
import org.signal.core.util.logging.Log.e
import org.signal.core.util.logging.Log.i
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.logging.Log.w
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.live
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.MissingIdentityException
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.getEncodedIdentityKey
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.getRecipientIdOrUnknown
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue.getRecordsForSendingTI
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.turnIntroductionStale
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.unknownStateTransition
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.userToggledAccepted
import org.thoughtcrime.securesms.trustedIntroductions.glue.TI_DatabaseGlue.Companion.userToggledRejected
import org.whispersystems.signalservice.api.push.ServiceId.Companion.parseOrThrow
import org.whispersystems.signalservice.api.util.Preconditions
import java.io.Closeable
import java.io.IOException
import java.util.Optional

/**
 * Database holding received trusted Introductions.
 * We are consciously trying to have the sending Introduction ephemeral since we want to maximize privacy,
 * (think an Informant that forwards someone to a Journalist, you don't want that information hanging around)
 *
 *
 * This implementation currently does not support multi-device.
 */
class TI_Database(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), TI_DatabaseGlue {
  @VisibleForTesting
  fun clearTable() {
    // Debugging
    val db: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val res: Int = db.delete(TABLE_NAME, "", arrayOf())
    if (res < 0) {
      w(TAG, "Failed to clear table: $TABLE_NAME")
    }
  }

  /**
   * Used to update a database entry. Pass all the data that should stay the same and change what needs to be updated.
   *
   * @return Content Values for the updated entry
   */
  private fun buildContentValuesForUpdate(
    introductionId: Long,
    state: TI_DatabaseGlue.Companion.State,
    introducerServiceId: String?,
    serviceId: String,
    name: String,
    number: String?,
    identityKey: String,
    profileKey: String,
    predictedFingerprint: String,
    timestamp: Long
  ): ContentValues {
    val cv = ContentValues()
    cv.put(ID, introductionId)
    cv.put(STATE, state.toInt())
    cv.put(INTRODUCER_SERVICE_ID, introducerServiceId)
    cv.put(INTRODUCEE_SERVICE_ID, serviceId)
    cv.put(INTRODUCEE_NAME, name)
    cv.put(INTRODUCEE_NUMBER, number)
    cv.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, identityKey)
    cv.put(INTRODUCEE_PROFILE_KEY, profileKey)
    cv.put(PREDICTED_FINGERPRINT, predictedFingerprint)
    cv.put(TIMESTAMP, timestamp)
    return cv
  }

  /**
   * @param c         a cursor pointing to a fully populated query result in the database.
   * @param timestamp the new timestamp to insert.
   */
  @SuppressLint("Range")
  private fun buildContentValuesForTimestampUpdate(c: Cursor, timestamp: Long): ContentValues {
    return buildContentValuesForUpdate(
      c.getString(c.getColumnIndex(ID)),
      c.getString(c.getColumnIndex(STATE)),
      c.getString(c.getColumnIndex(INTRODUCER_SERVICE_ID)),
      c.getString(c.getColumnIndex(INTRODUCEE_SERVICE_ID)),
      c.getString(c.getColumnIndex(INTRODUCEE_NAME)),
      c.getString(c.getColumnIndex(INTRODUCEE_NUMBER)),
      c.getString(c.getColumnIndex(INTRODUCEE_PROFILE_KEY)),
      c.getString(c.getColumnIndex(INTRODUCEE_PUBLIC_IDENTITY_KEY)),
      c.getString(c.getColumnIndex(PREDICTED_FINGERPRINT)),
      timestamp.toString()
    )
  }

  /**
   * Convenience function when changing state of an introduction
   *
   * @param introduction the introduction to change the state of
   * @param newState            new state
   * @return Correctly populated ContentValues
   */
  @SuppressLint("Range")
  override fun buildContentValuesForStateUpdate(introduction: TI_Data, newState: TI_DatabaseGlue.Companion.State): ContentValues {
    val values: ContentValues = buildContentValuesForUpdate(introduction)
    values.remove(STATE)
    values.put(STATE, newState.toInt())
    return values
  }

  override fun getSignalWritableDatabase(): SQLiteDatabase {
    return databaseHelper.signalWritableDatabase
  }

  /**
   * id not yet known, state either pending or conflicting
   *
   * @param state                   new introduction state, either a PENDING_ or CONFLICTING_ state
   * @param introducerServiceId     who made the introduction
   * @param introduceeServiceId     who is getting introduced
   * @param introduceeName          display name of the introducee
   * @param introduceeNumber        phone number in e.164 format (if present)
   * @param introduceeIdentityKey   identity public key (could be for ACI or PNI)
   * @param predictedSecurityNumber expected fingerprint between us and introduceeServiceId
   * @param timestamp               when was the introduction made
   * @return populated content values ready for insertion
   */
  override fun buildContentValuesForInsert(
    state: TI_DatabaseGlue.Companion.State,
    introducerServiceId: String,
    introduceeServiceId: String,
    introduceeName: String,
    introduceeNumber: String,
    introduceeIdentityKey: String,
    introduceeProfileKey: String,
    predictedSecurityNumber: String,
    timestamp: Long
  ): ContentValues {
    Preconditions.checkArgument(state == TI_DatabaseGlue.Companion.State.PENDING || state == TI_DatabaseGlue.Companion.State.PENDING_CONFLICTING || state == TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN)
    val cv = ContentValues()
    cv.put(STATE, state.toInt())
    cv.put(INTRODUCER_SERVICE_ID, introducerServiceId)
    cv.put(INTRODUCEE_SERVICE_ID, introduceeServiceId)
    cv.put(INTRODUCEE_NAME, introduceeName)
    cv.put(INTRODUCEE_NUMBER, introduceeNumber)
    cv.put(INTRODUCEE_PUBLIC_IDENTITY_KEY, introduceeIdentityKey)
    cv.put(INTRODUCEE_PROFILE_KEY, introduceeProfileKey)
    cv.put(PREDICTED_FINGERPRINT, predictedSecurityNumber)
    cv.put(TIMESTAMP, timestamp)
    return cv
  }


  /**
   * Meant for values pulled directly from the Database through a Query.
   * PRE: None of the Strings may be empty or Null.
   *
   * @param introductionId       Expected to represent a Long > 0.
   * @param state                Expected to represent an Int between 0 and 14 (inclusive).
   * @param introducerServiceId  who made the introduction (uuid)
   * @param introduceeServiceId  Who is getting introduced - could be an ACI or PNI ServiceId
   * @param name                 Display name (optional)
   * @param number               Phone number in e.164 format (optional)
   * @param identityKey          Public key - could be related to an ACI or PNI identity
   * @param predictedFingerprint Expected fingerprint between us and the introduced contact
   * @param timestamp            Expected to represent a Long.
   * @return Properly populated content values, NumberFormatException/AssertionError if a value was invalid.
   */
  @Throws(NumberFormatException::class)
  private fun buildContentValuesForUpdate(
    introductionId: String,
    state: String,
    introducerServiceId: String,
    introduceeServiceId: String,
    name: String,
    number: String?,
    identityKey: String,
    profileKey: String,
    predictedFingerprint: String,
    timestamp: String
  ): ContentValues {
    Preconditions.checkArgument(
      introductionId.isNotEmpty() &&
        state.isNotEmpty() &&
        introducerServiceId.isNotEmpty() &&
        introduceeServiceId.isNotEmpty() &&
        name.isNotEmpty() &&
        identityKey.isNotEmpty() &&
        predictedFingerprint.isNotEmpty() &&
        timestamp.isNotEmpty()
    )
    val introId: Long = introductionId.toLong()
    Preconditions.checkArgument(introId > 0)
    val s: Int = state.toInt()
    Preconditions.checkArgument(s in 0..14)
    val timestampLong: Long = timestamp.toLong()
    Preconditions.checkArgument(timestampLong > 0)
    return buildContentValuesForUpdate(
      introductionId = introId,
      state = TI_DatabaseGlue.Companion.State.forState(s),
      introducerServiceId = introducerServiceId,
      serviceId = introduceeServiceId,
      name = name,
      number = number,
      identityKey = identityKey,
      profileKey = profileKey,
      predictedFingerprint = predictedFingerprint,
      timestamp = timestampLong
    )
  }

  /**
   * @param introduction PRE: none of it's fields may be null, except introducerServiceId (forgotten introducer)
   * @return A populated contentValues object, to use for updates.
   */
  private fun buildContentValuesForUpdate(introduction: TI_Data): ContentValues {
    Preconditions.checkNotNull(introduction.id)
    Preconditions.checkNotNull(introduction.state)
    Preconditions.checkNotNull(introduction.predictedSecurityNumber)
    val introduceeName: String = introduction.introduceeName ?: ""
    return buildContentValuesForUpdate(
      introductionId = introduction.id!!,
      state = introduction.state,
      introducerServiceId = introduction.introducerServiceId,
      serviceId = introduction.introduceeServiceId,
      name = introduceeName,
      number = introduction.introduceeNumber,
      identityKey = introduction.introduceeIdentityKey,
      profileKey = introduction.introduceeProfileKey,
      predictedFingerprint = introduction.predictedSecurityNumber!!,
      timestamp = introduction.timestamp
    )
  }

  /**
   * @param introduction PRE: none of it's fields (except nr.) may be null, state != stale.
   * @return A populated contentValues object, to use when turning introductions stale.
   */
  private fun buildContentValuesForStale(introduction: TI_Data): ContentValues {
    Preconditions.checkNotNull(introduction.id)
    Preconditions.checkNotNull(introduction.state)
    Preconditions.checkNotNull(introduction.introducerServiceId)
    Preconditions.checkNotNull(introduction.predictedSecurityNumber)
    Preconditions.checkArgument(!introduction.state.isStale)
    val newState = turnIntroductionStale(introduction)

    val introduceeName: String = introduction.introduceeName ?: ""

    return buildContentValuesForUpdate(
      introductionId = introduction.id!!,
      state = newState,
      introducerServiceId = introduction.introducerServiceId,
      serviceId = introduction.introduceeServiceId,
      name = introduceeName,
      number = introduction.introduceeNumber,
      identityKey = introduction.introduceeIdentityKey,
      profileKey = introduction.introduceeProfileKey,
      predictedFingerprint = introduction.predictedSecurityNumber!!,
      timestamp = introduction.timestamp
    )
  }


  /**
   * PRE: No null fields (except nr.) and the state must be one of the 'unknowns'
   *
   * @param introduction the introduction for the previously unknown recipient.
   * @return content Values executing the appropriate state transitions for the introduction
   */
  private fun buildContentValuesForUnknownTransition(introduction: TI_Data): ContentValues {
    Preconditions.checkNotNull(introduction.id)
    Preconditions.checkNotNull(introduction.state)
    Preconditions.checkNotNull(introduction.introducerServiceId)
    Preconditions.checkNotNull(introduction.predictedSecurityNumber)
    Preconditions.checkArgument(introduction.state.isUnknownRecipient)
    // Unknown state transitions
    val newState = unknownStateTransition(introduction)

    val introduceeName: String = introduction.introduceeName ?: ""

    return buildContentValuesForUpdate(
      introductionId = introduction.id!!,
      state = newState,
      introducerServiceId = introduction.introducerServiceId,
      serviceId = introduction.introduceeServiceId,
      name = introduceeName,
      number = introduction.introduceeNumber,
      identityKey = introduction.introduceeIdentityKey,
      profileKey = introduction.introduceeProfileKey,
      predictedFingerprint = introduction.predictedSecurityNumber!!,
      timestamp = introduction.timestamp
    )
  }

  // TODO: Given a contact that cannot be contacted (hidden, no username/phone nr.) we cannot determine from the pending introduction, if there was a conflict
  // or the thing turned stale in the meantime when a session is initiated. Thus we must turn it stale immediately from whatever state it was in...
  private fun insertIntroduction(data: TI_Data, state: TI_DatabaseGlue.Companion.State): Long {
    Preconditions.checkArgument(state == TI_DatabaseGlue.Companion.State.PENDING || state == TI_DatabaseGlue.Companion.State.PENDING_CONFLICTING || state == TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN)
    val db: TI_DatabaseGlue = tiDatabase
    val values: ContentValues = db.buildContentValuesForInsert(
      state = state,
      introducerServiceId = data.introducerServiceId!!,
      introduceeServiceId = data.introduceeServiceId,
      introduceeName = data.introduceeName!!,
      introduceeNumber = data.introduceeNumber!!,
      introduceeIdentityKey = data.introduceeIdentityKey,
      introduceeProfileKey = data.introduceeProfileKey,
      predictedSecurityNumber = data.predictedSecurityNumber!!,
      timestamp = data.timestamp
    )
    val writeableDatabase: SQLiteDatabase = db.getSignalWritableDatabase()
    val id: Long = writeableDatabase.insert(TABLE_NAME, null, values)
    i(TAG, "Inserted new introduction for: " + data.introduceeName + ", with id: " + id)
    return id
  }

  private fun insertUnknownIntroduction(data: TI_Data): Long {
    return insertIntroduction(data, TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN)
  }

  /**
   * This is the START state of the introduction FSM.
   * Check if there is a detectable conflict (only possible if the service ID maps to a recipient ID)
   * and set the state accordingly for the insert
   *
   * @param data the new introduction to insert.
   * @return insertion id of introduction.
   */
  private fun insertKnownNewIntroduction(data: TI_Data): Long {
    val introduceeOpt: Optional<RecipientId> = recipients.getByServiceId(parseOrThrow(data.introduceeServiceId))
    val introduceeId: RecipientId? = introduceeOpt.orElse(null)
    if (introduceeId != null) {
      // The recipient already exists, check if the identity key matches what we already have in the database
      val identityKey: String
      try {
        identityKey = getEncodedIdentityKey(introduceeId)
        if (data.introduceeIdentityKey != identityKey) {
          return insertIntroduction(data, TI_DatabaseGlue.Companion.State.PENDING_CONFLICTING)
        }
      } catch (e: MissingIdentityException) {
        // Continue to end condition, recipient is unknown.
      }
    }
    return insertIntroduction(data, TI_DatabaseGlue.Companion.State.PENDING)
  }

  /**
   * Decides which fields must match to count as a duplicate introduction.
   *
   * @param data the introduction to check against.
   * @return a cursor populated with all the matches it found.
   */
  private fun checkForDuplicates(data: TI_Data): Cursor {
    // Fetch Data to compare if present
    // TODO: Adapt when we are more clear about what the data will be...
    // TODO: reimplement...
    val andAppend = " AND %s=?"
    val selectionBuilder: String = (String.format("%s=?", INTRODUCER_SERVICE_ID) + String.format(andAppend, INTRODUCEE_SERVICE_ID) + String.format(andAppend, INTRODUCEE_PUBLIC_IDENTITY_KEY))

    val args: Array<String> = buildArgs(
      data.introducerServiceId,
      data.introduceeServiceId,
      data.introduceeIdentityKey
    )

    val writeableDatabase: SQLiteDatabase = databaseHelper.signalWritableDatabase
    return writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selectionBuilder, args, null, null, null)
  }

  /**
   * Logic to update any duplicate introductions with the new available data.
   * Closes the cursor.
   *
   * @param c    the cursor populated with the duplicate introductions.
   * @param data the new introduction this was matched against.
   * @return the update result. Negative if something went wrong, row index of the introduction otherwise.
   */
  private fun updateDuplicateIntroduction(c: Cursor, data: TI_Data): Long {
    c.moveToFirst()
    val writeableDatabase: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val idColumn: Int = c.getColumnIndex(ID)
    if (idColumn < 0) {
      return idColumn.toLong()
    }
    val result: Long = writeableDatabase.update(TABLE_NAME, buildContentValuesForTimestampUpdate(c, data.timestamp), "$ID = ?", buildArgs(c.getInt(idColumn).toLong())).toLong()
    i(TAG, "Updated timestamp of introduction " + result + " to: " + TI_Utils.INTRODUCTION_DATE_PATTERN.format(data.timestamp))
    c.close()
    return result
  }

  /**
   * We first check if an introduction with the same introducer service id, introducee service id, and identity key
   * already exists in the database to avoid duplication.
   * If we find a duplicate, we simply update the timestamp to the most recent one.
   * Otherwise the start of the introduction FSM is reached.
   *
   * @param introduction the incoming introduction
   * @return insertion id of introduction.
   */
  @SuppressLint("Range")
  @WorkerThread
  override fun incomingIntroduction(introduction: TI_Data): Long {
    // Fetch Data to compare if present
    val c: Cursor = checkForDuplicates(introduction)
    // We found a matching introduction, we will update it and not insert a new one.
    if (c.count == 1) {
      // this closes the cursor
      //TODO: Debugging, uncomment at some point
      return updateDuplicateIntroduction(c, introduction)
    }

    /*
     if(c.getCount() != 0) {
     // If we don't call updateDuplicateIntroduction, we need to close it ourselves.
     c.close();
     // TODO: This assertion is no longer true, when we aren't checking for duplicates and just inserting any intro
     throw new AssertionError(TAG + " When checking for existing Introductions, there is one entry or none, nothing else is valid.");
     } */
    c.close()
    if (isRecipientUnknown(introduction.introducerServiceId!!)) {
      // todo: this should throw of course
      throw AssertionError(TAG + " We have received an introduction from an unknown contact " + introduction.introducerServiceId)
    }
    return if (isRecipientUnknown(introduction.introduceeServiceId)) insertUnknownIntroduction(introduction) else insertKnownNewIntroduction(introduction)
  }


  /**
   * Modify the state of an introduction and call to modify the verified state of the introducee if appropriate
   *
   * @param introduction the introduction to be modified. Their state cannot be any of the PENDING states as they are final. ID =! null.
   * @param newState     the new state for the introduction. Cannot be PENDING
   * @param logMessage   what should be written on the logcat for the modification.
   * @return if the insertion succeeded or failed
   */
  @WorkerThread
  private fun changeIntroductionState(introduction: TI_Data, newState: TI_DatabaseGlue.Companion.State, logMessage: String): Boolean {
    // We are setting the pending states directly when the introduction is first received. There is no other transition to this state.
    Preconditions.checkArgument(newState != TI_DatabaseGlue.Companion.State.PENDING)
    Preconditions.checkArgument(introduction.id != null)

    // Modify introduction
    val newValues: ContentValues = buildContentValuesForStateUpdate(introduction, newState)
    val writeableDatabase: SQLiteDatabase = getSignalWritableDatabase()
    val result: Long = writeableDatabase.update(TABLE_NAME, newValues, "$ID = ?", buildArgs(introduction.id)).toLong()

    if (result > 0) {
      // Log message on success
      i(TAG, logMessage)
      // Check if a recipient may change verification status as a result of this operation
      val introduceeID: RecipientId = getRecipientIdOrUnknown(introduction.introduceeServiceId)
      if (!introduceeID.isUnknown) {
        val previousIntroduceeVerification: IdentityTableGlue.Companion.VerifiedStatus = tiIdentityTable.getVerifiedStatus(introduceeID)
//        if (previousIntroduceeVerification == null) {
//          throw AssertionError("Unexpected missing verification status for " + introduction.introduceeName)
//        }
        tiIdentityTable.modifyIntroduceeVerification(introduction.introduceeServiceId, previousIntroduceeVerification, newState, logMessage)
      } // if introduceeID is unknown we do not have the recipient as a conversation partner yet and can skip any verification modification

      return true
    }
    // don't touch the verification state of the introducee if the modification failed
    e(TAG, "State modification of introduction: " + introduction.id + " failed!")
    return false
  }

  /**
   * @param states               which state to query for
   * @param introduceeServiceId The serviceID of the recipient whose verification status may change
   */
  @WorkerThread
  override fun atLeastOneIntroductionIs(states: TI_DatabaseGlue.Companion.State, introduceeServiceId: String): Boolean {
    val selection: String = String.format("%s=?", INTRODUCEE_SERVICE_ID) + String.format(" AND %s=?", STATE)
    val args: Array<String> = buildArgs(
      introduceeServiceId,
      states.toInt()
    )
    val writeableDatabase: SQLiteDatabase = getSignalWritableDatabase()
    val c: Cursor = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selection, args, null, null, null)

    return c.count >= 1
  }

  /**
   * Check if there is at least one introduction for this introducee that of the 'unknown' kind (we did not know the person when they were introduced).
   *
   * @param introduceeServiceId the service ID of the introducee.
   * @return true if there is at least one introduction for this introducee that meets the 'unknown' state criteria.
   */
  @SuppressLint("DefaultLocale")
  override fun atLeastOneIntroductionIsUnknown(introduceeServiceId: String): Boolean {
    val selection: String = String.format(
      "%s=? AND %s IN (%d,%d,%d)",
      INTRODUCEE_SERVICE_ID,
      STATE,
      TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN.toInt(),
      TI_DatabaseGlue.Companion.State.ACCEPTED_UNKNOWN.toInt(),
      TI_DatabaseGlue.Companion.State.REJECTED_UNKNOWN.toInt()
    )
    val args: Array<String> = buildArgs(introduceeServiceId)
    val writeableDatabase: SQLiteDatabase = getSignalWritableDatabase()
    val c: Cursor = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selection, args, null, null, null)

    val debugCursor: Cursor = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, null, null, null, null, null, null)
    if (debugCursor.moveToFirst()) {
      while (!debugCursor.isAfterLast) {
        do {
          val map: HashMap<String, String> = HashMap()
          for (i in 0 until debugCursor.columnCount) {
            map[debugCursor.getColumnName(i)] = debugCursor.getString(i)
          }
          i("TI - table state", listOf(map).toString())
        } while (debugCursor.moveToNext())
      }
    }
    return c.count >= 1
  }

  /**
   * Check database for any 'unknown' introductions and execute the state transitions.
   * PRE: When this is called, none of the returned introductions should be in the 'known' state.
   *
   * @param serviceId the service ID of the new contact
   * @return the state with the highest priority.
   */
  @WorkerThread
  override fun handleUnknownIntroductions(serviceId: String, encodedIdentityKey: String): TI_DatabaseGlue.Companion.State? {
    val selection: String = String.format("%s=?", INTRODUCEE_SERVICE_ID)
    val args: Array<String> = buildArgs(serviceId)
    val writeableDatabase: SQLiteDatabase = getSignalWritableDatabase()
    val c: Cursor = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, selection, args, null, null, null)
    val staleIntroductions: ArrayList<TI_Data> = ArrayList()
    val upToDateIntroductions: ArrayList<TI_Data> = ArrayList()
    // Keep count of any introductions that were interacted with that have not turned stale
    var hasTrusted = false
    var hasRejected = false
    var hasPending = false
    var hasStalePending = false
    var hasStaleTrusted = false
    var hasStaleRejected = false
    if (c.count >= 1) {
      val reader = IntroductionReader(c)
      var current: TI_Data?
      do {
        current = reader.next
        // TODO: double check if this is correct
        checkNotNull(current)
        if (!(current.state.isUnknownRecipient)) {
          throw AssertionError(TAG + "encountered an illegal introduction state: " + current.state + "\n for an unknown recipient with service ID: " + serviceId)
        }
        // todo: check WTF is going on here, is the COMPARISON good?
        if (encodedIdentityKey != current.introduceeIdentityKey) {
          // Add this datapoint to the introductions that must be turned stale
          staleIntroductions.add(current)
          if (current.state == TI_DatabaseGlue.Companion.State.ACCEPTED_UNKNOWN) hasStaleTrusted = true
          if (current.state == TI_DatabaseGlue.Companion.State.REJECTED_UNKNOWN) hasStaleRejected = true
          if (current.state == TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN) hasStalePending = true
        } else {
          upToDateIntroductions.add(current)
          if (current.state == TI_DatabaseGlue.Companion.State.ACCEPTED_UNKNOWN) hasTrusted = true
          if (current.state == TI_DatabaseGlue.Companion.State.REJECTED_UNKNOWN) hasRejected = true
          if (current.state == TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN) hasPending = true
        }
      } while (reader.hasNext())
      try {
        reader.close()
      } catch (e: IOException) {
        e(TAG, e.stackTrace.contentToString())
        throw AssertionError("Error occurred while trying to close the cursor to dangling Introductions for " + current!!.introduceeName)
      }
      // Turn all introductions stale that had the incorrect identity key
      val where = "$ID = ?"
      for (staleIntro: TI_Data in staleIntroductions) {
        val cv: ContentValues = buildContentValuesForStale(staleIntro.introduction)
        if (staleIntro.id == null) {
          throw AssertionError(TAG + " Introduction for " + staleIntro.introduceeName + " did not have an id ")
        }
        val result: Long = writeableDatabase.update(TABLE_NAME, cv, where, buildArgs(staleIntro.id)).toLong()
        if (result < 0) {
          throw AssertionError(TAG + " Could not turn introduction for " + staleIntro.introduceeName + " stale!")
        }
      }
      // Transition all the unknown introductions with the correct identity key to their known counterparts.
      for (unknownIntro: TI_Data in upToDateIntroductions) {
        if (unknownIntro.id == null) {
          throw AssertionError(TAG + " Introduction for " + unknownIntro.introduceeName + " did not have an id ")
        }
        val cv: ContentValues = buildContentValuesForUnknownTransition(unknownIntro.introduction)
        writeableDatabase.update(TABLE_NAME, cv, where, buildArgs(unknownIntro.id))
      }
    }
    // Priority defined here w.r.t which introduction state should be considered:
    if (!(hasTrusted || hasRejected || hasStaleTrusted || hasStaleRejected || hasStalePending || hasPending)) return null
    return if (hasTrusted) {
      TI_DatabaseGlue.Companion.State.ACCEPTED
    } else if (hasStaleTrusted) {
      TI_DatabaseGlue.Companion.State.STALE_ACCEPTED
    } else if (hasRejected) {
      TI_DatabaseGlue.Companion.State.REJECTED
    } else if (hasStaleRejected) {
      TI_DatabaseGlue.Companion.State.STALE_REJECTED
    } else if (hasPending) {
      TI_DatabaseGlue.Companion.State.PENDING
    } else {
      TI_DatabaseGlue.Companion.State.STALE_PENDING
    }
  }

  override fun isRecipientUnknown(serviceID: String): Boolean {
    val rid: RecipientId = getRecipientIdOrUnknown(serviceID)
    return rid == RecipientId.UNKNOWN
  }

  /**
   * Expects the introducee to have been fetched.
   * Expects introduction to already be present in database
   *
   * @param introduction PRE: introduction.id cannot be null
   * @return true if success, false otherwise
   */
  @WorkerThread
  override fun acceptIntroduction(introduction: TI_Data): Boolean {
    Preconditions.checkArgument(introduction.id != null)
    return changeIntroductionState(introduction, userToggledAccepted(introduction), "Accepted introduction for: " + introduction.introduceeName)
  }

  /**
   * Expects the introducee to have been fetched.
   * Expects introduction to already be present in database.
   *
   * @param introduction PRE: introduction.id cannot be null
   * @return true if success, false otherwise
   */
  @WorkerThread
  override fun rejectIntroduction(introduction: TI_Data): Boolean {
    Preconditions.checkArgument(introduction.id != null)
    return changeIntroductionState(introduction, userToggledRejected(introduction), "Rejected introduction for: " + introduction.introduceeName)
  }

  /**
   * Expects the introducee to have been fetched.
   * Expects introduction to already be present in database.
   *
   * @param introduction PRE: introduction.id cannot be null
   * @return true if success, false otherwise
   */
  @WorkerThread
  override fun staleIntroduction(introduction: TI_Data): Boolean {
    Preconditions.checkArgument(introduction.id != null)
    return changeIntroductionState(introduction, introduction.state, "Turned introduction stale for: " + introduction.introduceeName)
  }

  /**
   * Fetches All displayable Introduction data.
   * Introductions with null introducerServiceId are omitted
   *
   * @return IntroductionReader which can be used as an iterator.
   */
  @WorkerThread
  override fun getAllDisplayableIntroductions(): IntroductionReader {
    val query = "SELECT * FROM $TABLE_NAME WHERE $INTRODUCER_SERVICE_ID IS NOT NULL"
    val db: SQLiteDatabase = databaseHelper.signalReadableDatabase
    return IntroductionReader(db.rawQuery(query, null))
  }

  /**
   * PRE: introductionId may not be null, IntroducerServiceId must be null
   * Updates the entry in the database accordingly.
   * Effectively "forget" who did this introduction.
   *
   * @return true if success, false otherwise
   */
  @WorkerThread
  override fun clearIntroducer(introduction: TI_Data): Boolean {
    Preconditions.checkArgument(UNKNOWN_INTRODUCER_SERVICE_ID == introduction.introducerServiceId)
    Preconditions.checkArgument(introduction.id != null)
    val database: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val query = "$ID = ?"
    if (introduction.id == null) {
      e(TAG, "tried to clean introduction without id")
      return false
    }
    val args: Array<String> = buildArgs(introduction.id)

    val values: ContentValues = buildContentValuesForUpdate(introduction)

    val update: Int = database.update(TABLE_NAME, values, query, args)
    i(TAG, "Forgot introducer for introduction with id: " + introduction.id)

    // TODO: For multi-device, syncing would be handled here
    return update > 0
  }

  /**
   * Turns all introductions for the introducee named by id stale.
   * If this succeeds attempts to update the verification status of the introducee
   *
   * @param serviceID the introducee whose security nr. changed.
   * @return true if all updates succeeded, false otherwise
   */
  @WorkerThread
  override fun turnAllIntroductionsStale(serviceID: String): Boolean {
    val success: Boolean = turnAllIntroductionsStaleInternal(serviceID)
    val recipient: Recipient = live(RecipientId.fromSidOrE164(serviceID)).get()
    if (!success) {
      // This should hopefully never happen... if it does we need to investigate how to handle this inconsistent state.
      // Maybe turn it into a job and try again?
      throw AssertionError("At least one introduction for: " + recipient.getDisplayName(context) + " could not be turned stale! Verification state will not be updated!")
    }
    val tiIdentityDB: IdentityTableGlue = tiIdentityTable
    // Any stale state will result in the same unverified new verification state
    tiIdentityDB.modifyIntroduceeVerification(
      serviceID, tiIdentityDB.getVerifiedStatus(recipient.id), TI_DatabaseGlue.Companion.State.STALE_PENDING, ("Marked " + recipient.getDisplayName(context) + " unverified"
        + "after successfully turning all introductions for them stale.")
    )
    return true
  }

  private fun turnAllIntroductionsStaleInternal(serviceId: String): Boolean {
    var updateSucceeded = true
    Preconditions.checkArgument(getRecipientIdOrUnknown(serviceId) != RecipientId.UNKNOWN)
    val query = "$INTRODUCEE_SERVICE_ID = ?"
    val args: Array<String> = buildArgs(serviceId)

    val writeableDatabase: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val c: Cursor = writeableDatabase.query(TABLE_NAME, TI_ALL_PROJECTION, query, args, null, null, null)
    val reader = IntroductionReader(c)
    var introduction: TI_Data?
    while ((reader.next.also { introduction = it }) != null) {
      // If the intro is already stale, we don't need to do anything.
      if (!introduction!!.state.isStale && introduction!!.id != null) {
        val cv: ContentValues = buildContentValuesForStale(introduction!!)
        val res: Int = writeableDatabase.update(TABLE_NAME, cv, "$ID = ?", buildArgs(introduction!!.id))
        if (res < 0) {
          e(TAG, "Introduction " + introduction!!.id + " for " + introduction!!.introduceeName + " with state " + introduction!!.state + " could not be turned stale!")
          updateSucceeded = false
        } else {
          i(TAG, "Introduction " + introduction!!.id + " for " + introduction!!.introduceeName + " with state " + introduction!!.state + " was turned stale!")
          // TODO: For multi-device, syncing would be handled here
        }
      }
    }
    return updateSucceeded
  }


  /**
   * PRE: introductionId must be > 0
   * Deletes an introduction out of the database.
   *
   * @return true if success, false otherwise
   */
  @SuppressLint("DefaultLocale")
  @WorkerThread
  override fun deleteIntroduction(introductionId: Long): Boolean {
    Preconditions.checkArgument(introductionId > 0)
    val database: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val query = "$ID = ?"
    val args: Array<String> = buildArgs(introductionId)

    val count: Int = database.delete(TABLE_NAME, query, args)

    if (count == 1) {
      i(TAG, String.format("Deleted introduction with id: %d from the database.", introductionId))
      return true
    } else if (count > 1) {
      // matching with id, which must be unique
      throw AssertionError()
    } else {
      return false
    }
  }

  /*
    General Utilities
   */
  /**
   * @param introduceeId Which recipient to look for in the recipient database
   * @return Cursor pointing to query result.
   */
  @WorkerThread
  override fun fetchRecipientRecord(introduceeId: RecipientId): Map<RecipientId, RecipientRecord> {
    // TODO: Simplify if you see that you finally never query this cursor with more than 1 recipient...
    val s: MutableSet<RecipientId> = HashSet()
    s.add(introduceeId)
    return getRecordsForSendingTI(s)
  }

  class IntroductionReader internal constructor(private val cursor: Cursor) : Closeable {
    // TODO: Make it slightly more flexible in terms of which data you pass around.
    // A cursor pointing to the result of a query using TI_DATA_PROJECTION
    init {
      cursor.moveToFirst()
    }

    @get:SuppressLint("Range")
    private val current: TI_Data?
      // This is now has a guarantee w.r.t. calling the constructor
      get() {
        if (cursor.isAfterLast || cursor.isBeforeFirst) {
          return null
        }
        val introductionId: Long = cursor.getLong(cursor.getColumnIndex(ID))
        val s: Int = cursor.getInt(cursor.getColumnIndex(STATE))
        val state: TI_DatabaseGlue.Companion.State = TI_DatabaseGlue.Companion.State.forState(s)
        val introducerServiceId: String = (cursor.getString(cursor.getColumnIndex(INTRODUCER_SERVICE_ID)))
        val introduceeServiceId: String = (cursor.getString(cursor.getColumnIndex(INTRODUCEE_SERVICE_ID)))
        // Do I need to hit the Recipient Database to check the name?
        // TODO: Name changes in introducees should get reflected in database (needs to happen when the name changes, not on query)
        val introduceeName: String = cursor.getString(cursor.getColumnIndex(INTRODUCEE_NAME))
        val introduceeNumber: String = cursor.getString(cursor.getColumnIndex(INTRODUCEE_NUMBER))
        val introduceeIdentityKey: String = cursor.getString(cursor.getColumnIndex(INTRODUCEE_PUBLIC_IDENTITY_KEY))
        val introduceeProfileKey: String = cursor.getString(cursor.getColumnIndex(INTRODUCEE_PROFILE_KEY))
        val securityNr: String = cursor.getString(cursor.getColumnIndex(PREDICTED_FINGERPRINT))
        val timestamp: Long = cursor.getLong(cursor.getColumnIndex(TIMESTAMP))
        return TI_Data(
          id = introductionId,
          state = state,
          introducerServiceId = introducerServiceId,
          introduceeServiceId = introduceeServiceId,
          introduceeName = introduceeName,
          introduceeNumber = introduceeNumber,
          introduceeIdentityKey = introduceeIdentityKey,
          introduceeProfileKey = introduceeProfileKey,
          predictedSecurityNumber = securityNr,
          timestamp = timestamp
        )
      }

    val next: TI_Data?
      /**
       * advances one row and returns it, null if empty, or cursor after last.
       */
      get() {
        val current: TI_Data? = current
        cursor.moveToNext()
        return current
      }

    fun hasNext(): Boolean {
      return !cursor.isAfterLast
    }

    @Throws(IOException::class)
    override fun close() {
      cursor.close()
    }
  }

  companion object {

    private val TAG: String = String.format(TI_Utils.TI_LOG_TAG, tag(TI_Database::class.java))

    @set:Throws(Exception::class)
    var instance: TI_DatabaseGlue? = null
      get() {
        if (field == null) {
          throw AssertionError("Attempted to fetch Singleton TI_Database before initializing it.")
        }
        return field
      }
      set(inst) {
        if (field != null) {
          throw Exception("Attempted to reassign Singleton instance of TI_Database")
        }
        field = inst
      }

    const val TABLE_NAME: String = "trusted_introductions"

    private const val ID: String = "_id"
    const val INTRODUCER_SERVICE_ID: String = "introducer_service_id"
    private const val INTRODUCEE_SERVICE_ID: String = "introducee_service_id"
    private const val INTRODUCEE_PUBLIC_IDENTITY_KEY: String = "introducee_identity_key" // The one contained in the Introduction
    private const val INTRODUCEE_NAME: String = "introducee_name" // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
    private const val INTRODUCEE_NUMBER: String = "introducee_number" // TODO: snapshot when introduction happened. Necessary? Or wrong approach?
    private const val INTRODUCEE_PROFILE_KEY: String = "introducee_profile_key"
    private const val PREDICTED_FINGERPRINT: String = "predicted_fingerprint"
    private const val TIMESTAMP: String = "timestamp"
    private const val STATE: String = "state"
    const val UNKNOWN_INTRODUCER_SERVICE_ID: String = "-1"

    const val CREATE_TABLE: String = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
      INTRODUCER_SERVICE_ID + " TEXT, " +
      INTRODUCEE_SERVICE_ID + " TEXT NOT NULL, " +
      INTRODUCEE_PUBLIC_IDENTITY_KEY + " TEXT NOT NULL, " +
      INTRODUCEE_NAME + " TEXT NOT NULL, " +
      INTRODUCEE_NUMBER + " TEXT, " +
      INTRODUCEE_PROFILE_KEY + " TEXT NOT NULL, " +
      PREDICTED_FINGERPRINT + " TEXT NOT NULL, " +
      TIMESTAMP + " INTEGER NOT NULL, " +
      STATE + " INTEGER NOT NULL);"

    private const val CLEAR_TABLE: String = "DELETE FROM $TABLE_NAME;"

    private val TI_ALL_PROJECTION: Array<String> = arrayOf(
      ID,
      INTRODUCER_SERVICE_ID,
      INTRODUCEE_SERVICE_ID,
      INTRODUCEE_PUBLIC_IDENTITY_KEY,
      INTRODUCEE_NAME,
      INTRODUCEE_NUMBER,
      INTRODUCEE_PROFILE_KEY,
      PREDICTED_FINGERPRINT,
      TIMESTAMP,
      STATE
    )
  }
}
