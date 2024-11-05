package org.thoughtcrime.securesms.trustedIntroductions.glue

import android.content.Context
import android.database.Cursor
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.tiIdentityTable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable

interface IdentityTableGlue {
  /**
   * @return Returns a Cursor which iterates through all contacts that are unlocked for
   * trusted introductions (for which @see VerifiedStatus.tiUnlocked returns true)
   */
  val cursorForTIUnlocked: Cursor

  /**
   * Publicly exposes verified status by recipient id.
   *
   * @param id: id of the recipient
   * @return The VerifiedStatus of the recipient or default if the recipient is not in the database.
   */
  fun getVerifiedStatus(id: RecipientId?): VerifiedStatus


  /**
   * Adds a new identity to the shadow table
   */
  @WorkerThread
  fun saveIdentity(addressName: String, verifiedStatus: VerifiedStatus): Boolean

  /**
   * Set the TI verification state of this recipient. If the recipient does not yet have an entry in the
   * DB, create one.
   *
   * @param id: id of the recipient
   * @return Success of status change.
   */
  @WorkerThread
  fun setVerifiedStatus(id: RecipientId, newStatus: VerifiedStatus): Boolean

  /**
   * @param introduceeServiceId            The service ID of the introducee that may get their verification state modified.
   * @param previousIntroduceeVerification The previous introducee verification state.
   * @param newState                       The new state of the modified introduction.
   * @param logMessage                     What to print to logcat if the verification state was modified.
   */
  fun modifyIntroduceeVerification(introduceeServiceId: String, previousIntroduceeVerification: VerifiedStatus, newState: TI_DatabaseGlue.Companion.State, logMessage: String)

  companion object {
    val TAG: String = String.format(TI_Utils.TI_LOG_TAG, Log.tag(IdentityTableGlue::class.java))
    val createTable: String
      get() = TI_IdentityTable.CREATE_TABLE

    fun createSingleton(c: Context?, databaseHelper: SignalDatabase?): IdentityTableGlue {
      return TI_IdentityTable(c, databaseHelper)
    }

    const val TI_ADDRESS_PROJECTION: String = IdentityTable.ADDRESS
    const val VERIFIED: String = IdentityTable.VERIFIED
    const val TABLE_NAME: String = IdentityTable.TABLE_NAME

    /**
     * FSM transitions
     *
     */
    fun getManuallyVerified() : VerifiedStatus {
      return VerifiedStatus.MANUALLY_VERIFIED
    }

    fun getQrVerificationSuccess(previousIntroduceeVerification: VerifiedStatus) : VerifiedStatus{
      return if (previousIntroduceeVerification == VerifiedStatus.INTRODUCED){
        VerifiedStatus.DUPLEX_VERIFIED
      } else {
        VerifiedStatus.DIRECTLY_VERIFIED
      }
    }

    fun getUnverified() : VerifiedStatus{
      return VerifiedStatus.UNVERIFIED
    }

    fun getToggleIntroduction(previousIntroduceeVerification: VerifiedStatus, newIntroductionState: TI_DatabaseGlue.Companion.State, introduceeServiceId: String) : VerifiedStatus {
      val newIntroduceeVerification = when (newIntroductionState) {
        TI_DatabaseGlue.Companion.State.PENDING, TI_DatabaseGlue.Companion.State.PENDING_UNKNOWN -> throw AssertionError(TAG + " Precondition Violation! State was: " + newIntroductionState.name)
        // Any stale state leads to unverified
        TI_DatabaseGlue.Companion.State.STALE_PENDING, TI_DatabaseGlue.Companion.State.STALE_ACCEPTED, TI_DatabaseGlue.Companion.State.STALE_REJECTED, TI_DatabaseGlue.Companion.State.STALE_ACCEPTED_CONFLICTING,
        TI_DatabaseGlue.Companion.State.STALE_REJECTED_CONFLICTING, TI_DatabaseGlue.Companion.State.STALE_PENDING_CONFLICTING -> VerifiedStatus.UNVERIFIED
        // An accepted introduction may lead to the introducee verification state:
        TI_DatabaseGlue.Companion.State.ACCEPTED, TI_DatabaseGlue.Companion.State.ACCEPTED_UNKNOWN -> when (previousIntroduceeVerification) {
          // Becoming or staying strongly verified
          VerifiedStatus.DUPLEX_VERIFIED, VerifiedStatus.DIRECTLY_VERIFIED -> VerifiedStatus.DUPLEX_VERIFIED
          // Staying or becoming introduced
          VerifiedStatus.DEFAULT, VerifiedStatus.UNVERIFIED, VerifiedStatus.INTRODUCED, VerifiedStatus.MANUALLY_VERIFIED -> VerifiedStatus.INTRODUCED
          // Or staying in the suspected compromised state
          VerifiedStatus.SUSPECTED_COMPROMISE -> VerifiedStatus.SUSPECTED_COMPROMISE
        }
        // A rejected introduction may lead to the introducee verification state:
        TI_DatabaseGlue.Companion.State.REJECTED, TI_DatabaseGlue.Companion.State.REJECTED_UNKNOWN -> when (previousIntroduceeVerification) {
          // Staying the same
          VerifiedStatus.DIRECTLY_VERIFIED, VerifiedStatus.MANUALLY_VERIFIED, VerifiedStatus.DEFAULT, VerifiedStatus.UNVERIFIED -> previousIntroduceeVerification
          // Potentially degrading in status
          VerifiedStatus.DUPLEX_VERIFIED -> {
            if (SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_DatabaseGlue.Companion.State.ACCEPTED, introduceeServiceId)) VerifiedStatus.DUPLEX_VERIFIED
            else VerifiedStatus.DIRECTLY_VERIFIED
          }

          VerifiedStatus.INTRODUCED -> {
            if (SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_DatabaseGlue.Companion.State.ACCEPTED, introduceeServiceId)) VerifiedStatus.INTRODUCED
            else VerifiedStatus.UNVERIFIED
          }
          // Or staying in the suspected compromised state
          VerifiedStatus.SUSPECTED_COMPROMISE -> VerifiedStatus.SUSPECTED_COMPROMISE
        }
        // An accepted conflicting introduction will lead to a suspected compromise
        TI_DatabaseGlue.Companion.State.ACCEPTED_CONFLICTING -> VerifiedStatus.SUSPECTED_COMPROMISE
        // A rejected conflicting introduction might move the introducee out of the conflicting state or keep the state the same
        TI_DatabaseGlue.Companion.State.REJECTED_CONFLICTING -> when (previousIntroduceeVerification) {
          VerifiedStatus.SUSPECTED_COMPROMISE -> {
            if (SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_DatabaseGlue.Companion.State.ACCEPTED_CONFLICTING, introduceeServiceId)) VerifiedStatus.SUSPECTED_COMPROMISE
            else {
              if (SignalDatabase.tiDatabase.atLeastOneIntroductionIs(TI_DatabaseGlue.Companion.State.ACCEPTED, introduceeServiceId)) VerifiedStatus.INTRODUCED
              else VerifiedStatus.UNVERIFIED
            }
          }
          VerifiedStatus.INTRODUCED, VerifiedStatus.UNVERIFIED, VerifiedStatus.DIRECTLY_VERIFIED, VerifiedStatus.MANUALLY_VERIFIED, VerifiedStatus.DEFAULT,
          VerifiedStatus.DUPLEX_VERIFIED -> previousIntroduceeVerification
        }
        TI_DatabaseGlue.Companion.State.PENDING_CONFLICTING -> previousIntroduceeVerification
      }
      return newIntroduceeVerification
    }

    /**
     * FSM states
     *
     */
    enum class VerifiedStatus {
      DEFAULT, MANUALLY_VERIFIED, UNVERIFIED, DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED, SUSPECTED_COMPROMISE;

      fun toInt(): Int {
        return when (this) {
          DEFAULT -> 0
          MANUALLY_VERIFIED -> 1
          UNVERIFIED -> 2
          DIRECTLY_VERIFIED -> 3
          INTRODUCED -> 4
          DUPLEX_VERIFIED -> 5
          SUSPECTED_COMPROMISE -> 6
        }
      }

      companion object {
        fun forState(state: Int): VerifiedStatus {
          return when (state) {
            0 -> DEFAULT
            1 -> MANUALLY_VERIFIED
            3 -> DIRECTLY_VERIFIED
            4 -> INTRODUCED
            5 -> DUPLEX_VERIFIED
            6 -> SUSPECTED_COMPROMISE
            else -> UNVERIFIED
          }
        }

        fun toVanilla(status: Int): Int {
          val s = forState(status)
          return when (s) {
            DEFAULT -> IdentityTable.VerifiedStatus.DEFAULT.toInt()
            DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED, MANUALLY_VERIFIED -> IdentityTable.VerifiedStatus.VERIFIED.toInt()
            else -> IdentityTable.VerifiedStatus.UNVERIFIED.toInt()
          }
        }

        @JvmStatic
        fun toVanilla(status: VerifiedStatus): IdentityTable.VerifiedStatus {
          return IdentityTable.VerifiedStatus.forState(toVanilla(status.toInt()))
        }

        /**
         * Much of the code relies on checks of the verification status that are not interested in the finer details.
         * This function can now be called instead of doing 4 comparisons manually.
         * Do not use this to decide if trusted introductions are allowed.
         *
         * @return True is verified, false otherwise.
         */
        @JvmStatic
        fun isVerified(status: VerifiedStatus): Boolean {
          return when (status) {
            DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED, MANUALLY_VERIFIED -> true
            else -> false
          }
        }

        /**
         * Convenience function with id instead of status. Hits Disk.
         *
         * @param id recipientID to be queried.
         * @return true if verified, otherwise false.
         */
        fun isVerified(id: RecipientId?): Boolean {
          val status = tiIdentityTable.getVerifiedStatus(id)
          return isVerified(status)
        }

        /**
         * Adding this in order to be able to change my mind easily on what should unlock a TI.
         * For now, only direct verification unlocks forwarding a contact's public key,
         * in order not to propagate malicious verifications further than one connection.
         *
         * @param status the verification status to be checked
         * @return true if verification status suffices to forward this contact as a
         * trusted introduction, false otherwise
         */
        fun tiForwardUnlocked(status: VerifiedStatus): Boolean {
          return when (status) {
            DIRECTLY_VERIFIED, DUPLEX_VERIFIED -> true
            else -> false
          }
        }

        /**
         * A recipient can only receive TrustedIntroductions iff they have previously been strongly verified.
         * This function exists as it's own thing to allow for flexible changes. Queries DB for TI verification status.
         *
         * @param id The recipient ID.
         * @return True if this recipient can receive trusted introductions.
         */
        fun tiRecipientUnlocked(id: RecipientId?): Boolean {
          val status = tiIdentityTable.getVerifiedStatus(id)
          return when (status) {
            DIRECTLY_VERIFIED, DUPLEX_VERIFIED, INTRODUCED -> true
            else -> false
          }
        }

        /**
         * Returns true for any non-trivial positive verification status.
         * Used to prompt user when clearing a verification status that is not trivially recoverable and to decide
         * if a channel is secure enough to forward an introduction over.
         */
        @JvmStatic
        fun stronglyVerified(status: VerifiedStatus): Boolean {
          return when (status) {
            DIRECTLY_VERIFIED, DUPLEX_VERIFIED, INTRODUCED -> true
            else -> false
          }
        }
      }
    }


  }
}
