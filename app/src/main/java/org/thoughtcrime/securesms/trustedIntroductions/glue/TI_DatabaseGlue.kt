package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database

interface TI_DatabaseGlue {
  fun fetchRecipientRecord(introduceeId: RecipientId): Map<RecipientId, RecipientRecord>

  fun buildContentValuesForStateUpdate(introduction: TI_Data, newState: State): ContentValues

  fun getSignalWritableDatabase(): SQLiteDatabase

  fun buildContentValuesForInsert(
    state: State,
    introducerServiceId: String,
    introduceeServiceId: String,
    introduceeName: String,
    introduceeNumber: String,
    introduceeIdentityKey: String,
    predictedSecurityNumber: String,
    timestamp: Long
  ): ContentValues

  fun turnAllIntroductionsStale(serviceID: String): Boolean

  fun incomingIntroduction(introduction: TI_Data): Long

  fun deleteIntroduction(introductionId: Long): Boolean

  fun clearIntroducer(introduction: TI_Data): Boolean

  fun getAllDisplayableIntroductions(): TI_Database.IntroductionReader

  fun isRecipientUnknown(serviceID: String): Boolean

  fun acceptIntroduction(introduction: TI_Data): Boolean

  fun rejectIntroduction(introduction: TI_Data): Boolean

  fun staleIntroduction(introduction: TI_Data): Boolean

  fun atLeastOneIntroductionIs(states: State, introduceeServiceId: String): Boolean

  fun atLeastOneIntroductionIsUnknown(introduceeServiceId: String): Boolean

  fun handleUnknownIntroductions(serviceId: String, encodedIdentityKey: String): State?

  companion object {
    @JvmStatic
    fun executeCreateTable(db: SQLiteDatabase) {
      db.execSQL(TI_Database.CREATE_TABLE)
    }

    @JvmStatic
    fun getTIDatabase(db: SignalDatabase?): TI_DatabaseGlue {
      requireNotNull(db) { "SignalDatabase cannot be null" }
      return TI_Database.instance!!
    }

    @JvmStatic
    fun createSingleton(c: Context, databaseHelper: SignalDatabase): TI_DatabaseGlue {
      return TI_Database(c, databaseHelper)
    }

    @JvmStatic
    fun getCreateTable(): String {
      return TI_Database.CREATE_TABLE
    }

    /**
     * FSM transitions
     */
    fun userToggledAccepted(introduction: TI_Data) : State {
      val newState: State
      if (SignalDatabase.tiDatabase.isRecipientUnknown(introduction.introduceeServiceId)){
        newState = State.ACCEPTED_UNKNOWN
      } else if (introduction.state.equals(State.PENDING_CONFLICTING)){
        newState = State.ACCEPTED_CONFLICTING
      } else {
        newState = State.ACCEPTED
      }
      return newState
    }

    fun userToggledRejected(introduction: TI_Data) : State {
      val newState: State
      if (SignalDatabase.tiDatabase.isRecipientUnknown(introduction.introduceeServiceId)){
        newState = State.REJECTED_UNKNOWN
      } else if (introduction.state.equals(State.PENDING_CONFLICTING)){
        newState = State.REJECTED_CONFLICTING
      } else {
        newState = State.REJECTED
      }
      return newState
    }

    fun turnIntroductionStale(introduction: TI_Data) : State {
      // Find stale state
      val newState: State = when (introduction.state) {
        State.PENDING, State.PENDING_UNKNOWN -> State.STALE_PENDING
        State.ACCEPTED, State.ACCEPTED_UNKNOWN -> State.STALE_ACCEPTED
        State.REJECTED, State.REJECTED_UNKNOWN -> State.STALE_REJECTED
        State.PENDING_CONFLICTING -> State.STALE_PENDING_CONFLICTING
        State.ACCEPTED_CONFLICTING -> State.STALE_ACCEPTED_CONFLICTING
        State.REJECTED_CONFLICTING -> State.STALE_REJECTED_CONFLICTING
        else -> throw AssertionError("State: " + introduction.state + " was illegal or already stale.")
      }
      return newState
    }

    fun unknownStateTransition(introduction: TI_Data) : State {
      val newState : State = when (introduction.state) {
        State.PENDING_UNKNOWN -> State.PENDING
        State.ACCEPTED_UNKNOWN -> State.ACCEPTED
        State.REJECTED_UNKNOWN -> State.REJECTED
        else -> throw AssertionError("State: " + introduction.state + " was illegal or already stale.")
      }
      return newState
    }

    /**
     * FSM States
     */
    enum class State {
      PENDING, ACCEPTED, REJECTED, PENDING_UNKNOWN, ACCEPTED_UNKNOWN, REJECTED_UNKNOWN, PENDING_CONFLICTING, ACCEPTED_CONFLICTING, REJECTED_CONFLICTING, STALE_PENDING, STALE_ACCEPTED,
      STALE_REJECTED, STALE_PENDING_CONFLICTING, STALE_ACCEPTED_CONFLICTING, STALE_REJECTED_CONFLICTING;

      fun toInt(): Int {
        return when (this) {
          PENDING -> 0
          ACCEPTED -> 1
          REJECTED -> 2
          PENDING_UNKNOWN -> 3
          ACCEPTED_UNKNOWN -> 4
          REJECTED_UNKNOWN -> 5
          PENDING_CONFLICTING -> 6
          ACCEPTED_CONFLICTING -> 7
          REJECTED_CONFLICTING -> 8
          STALE_PENDING -> 9
          STALE_ACCEPTED -> 10
          STALE_REJECTED -> 11
          STALE_PENDING_CONFLICTING -> 12
          STALE_ACCEPTED_CONFLICTING -> 13
          STALE_REJECTED_CONFLICTING -> 14
        }
      }

      val isStale: Boolean
        get() = when (this) {
          STALE_PENDING, STALE_ACCEPTED, STALE_REJECTED, STALE_PENDING_CONFLICTING, STALE_ACCEPTED_CONFLICTING, STALE_REJECTED_CONFLICTING -> true
          else -> false
        }

      val isPending: Boolean
        get() {
          return when (this) {
            PENDING, PENDING_CONFLICTING, PENDING_UNKNOWN, STALE_PENDING, STALE_PENDING_CONFLICTING -> true
            else -> false
          }
        }

      val isUnknownRecipient: Boolean
        get() {
          return when (this) {
            PENDING_UNKNOWN, ACCEPTED_UNKNOWN, REJECTED_UNKNOWN -> true
            else -> false
          }
        }

      val isConflicting: Boolean
        get() {
          return when (this) {
            PENDING_CONFLICTING, ACCEPTED_CONFLICTING, REJECTED_CONFLICTING, STALE_PENDING_CONFLICTING, STALE_ACCEPTED_CONFLICTING, STALE_REJECTED_CONFLICTING -> true
            else -> false
          }
        }

      val isTrusted: Boolean
        get() {
          return when (this) {
            ACCEPTED, ACCEPTED_UNKNOWN, ACCEPTED_CONFLICTING, STALE_ACCEPTED, STALE_ACCEPTED_CONFLICTING -> true
            else -> false
          }
        }

      val isDistrusted: Boolean
        get() {
          return when (this) {
            REJECTED, REJECTED_UNKNOWN, REJECTED_CONFLICTING, STALE_REJECTED, STALE_REJECTED_CONFLICTING -> true
            else -> false
          }
        }

      companion object {
        fun forState(state: Int): State {
          return when (state) {
            0 -> PENDING
            1 -> ACCEPTED
            2 -> REJECTED
            3 -> PENDING_UNKNOWN
            4 -> ACCEPTED_UNKNOWN
            5 -> REJECTED_UNKNOWN
            6 -> PENDING_CONFLICTING
            7 -> ACCEPTED_CONFLICTING
            8 -> REJECTED_CONFLICTING
            9 -> STALE_PENDING
            10 -> STALE_ACCEPTED
            11 -> STALE_REJECTED
            12 -> STALE_PENDING_CONFLICTING
            13 -> STALE_ACCEPTED_CONFLICTING
            14 -> STALE_REJECTED_CONFLICTING
            else -> throw AssertionError("No such state: $state")
          }
        }
      }
    }

  }
}