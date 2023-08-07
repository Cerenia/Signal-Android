package org.thoughtcrime.securesms.trustedIntroductions.database

import android.database.Cursor
import android.content.Context
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.DatabaseTable
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue
import java.lang.StringBuilder


class TI_IdentityTable internal constructor(context: Context?, databaseHelper: SignalDatabase?): DatabaseTable(context, databaseHelper), IdentityTableGlue{

  companion object Singleton{
    private val TAG = Log.tag(TI_IdentityTable::class.java)
    const val TABLE_NAME = "TI_shadow_identities"
    private const val ID = "_id"
    const val ADDRESS = "address"
    const val LAST_KNOWN_VANILLA_VERIFIED = "vanilla_verified"
    const val TI_VERIFIED = "ti_verified"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $ADDRESS INTEGER UNIQUE, 
        $LAST_KNOWN_VANILLA_VERIFIED INTEGER DEFAULT 0,
        $TI_VERIFIED INTEGER DEFAULT 0, 
      )
    """

    var inst: IdentityTableGlue? = null

    fun setInstance(intfHandle: IdentityTableGlue ){
      if (inst != null){
        throw AssertionError("Attempted to reassign trustedIntroduction Identity Table singleton!")
      }
      inst = intfHandle
    }

  }

  /**
   *
   * @return Returns a Cursor which iterates through all recipientIds that are unlocked for
   * trusted introductions (for which @see VerifiedStatus.tiUnlocked returns true)
   */
  override fun getCursorForTIUnlocked(): Cursor {
    // TODO: Check cache & adapt before you give this out.

    val validStates: ArrayList<String> = ArrayList()
    // dynamically compute the valid states and query the Signal database for these contacts
    for (e in VerifiedStatus.values()) {
      if (VerifiedStatus.ti_forwardUnlocked(e)) {
        validStates.add(e.toInt().toString())
      }
    }
    assert(validStates.size > 0) { "No valid states defined for TI" }
    val selectionBuilder = StringBuilder()
    selectionBuilder.append(String.format("%s=?", TI_VERIFIED))
    if (validStates.size > 1) {
      for (i in 0 until validStates.size - 1) {
        selectionBuilder.append(String.format(" OR %s=?", TI_VERIFIED))
      }
    }
    // create the rest of the query
    val readableDatabase = readableDatabase
    val states = validStates.toArray(arrayOf<String>())
    return readableDatabase.query(TABLE_NAME, arrayOf<String>(ADDRESS), selectionBuilder.toString(), states, null, null, null)
  }

  override fun getVerifiedStatus(id: RecipientId?): VerifiedStatus {
    // TODO: Adapt to new scheme, statemachine needs implementation here
    /**
    val recipient = Recipient.resolved(id!!)
    if(recipient.hasServiceId()){
      val ir = getIdentityRecord(recipient.requireServiceId().toString())
      return ir.map(TI_IdentityRecord::verifiedStatus).orElse(VerifiedStatus.UNVERIFIED) // fail closed
    } else {
      return VerifiedStatus.DEFAULT
    }**/
    return VerifiedStatus.DEFAULT
  }


  /**
   * Expose all keys of the database for Precondition check in @TI_Cursor.java
   */
  override fun getAllDatabaseKeys(): ArrayList<String> {
    val keys = arrayListOf<String>()
    keys.add(ID)
    keys.add(ADDRESS)
    keys.add(LAST_KNOWN_VANILLA_VERIFIED)
    keys.add(TI_VERIFIED)
    return keys
  }

  fun delete(addressName: String) {
  writableDatabase
    .delete(TABLE_NAME)
    .where("${ADDRESS} = ?", addressName)
    .run()
  }

  /**
   * We differentiate between a direct verification <code>DIRECTLY_VERIFIED</code> (via. QR code)
   * and a weaker, manual verification <code>MANUALLY_VERIFIED</code>. Additionally, a user can become verified by the
   * trusted introductions mechanism <code>TRUSTINGLY_INTRODUCED</code>. A user that has been trustingly introduced and
   * directly verified is <code>DUPLEX_VERIFIED</code>, the strongest level.
   * A user can always manually reset the trust to be unverified.
   *
   *
   *
   */
  enum class VerifiedStatus {
    DEFAULT, MANUALLY_VERIFIED, UNVERIFIED, DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED;

    fun toInt(): Int {
      return when (this) {
        DEFAULT -> 0
        MANUALLY_VERIFIED -> 1
        UNVERIFIED -> 2
        DIRECTLY_VERIFIED -> 3
        INTRODUCED -> 4
        DUPLEX_VERIFIED -> 5
      }
    }

    companion object {
      @JvmStatic
      fun forState(state: Int): VerifiedStatus {
        return when (state) {
          0 -> DEFAULT
          1 -> MANUALLY_VERIFIED
          2 -> UNVERIFIED
          3 -> DIRECTLY_VERIFIED
          4 -> INTRODUCED
          5 -> DUPLEX_VERIFIED
          else -> throw java.lang.AssertionError("No such state: $state")
        }
      }

      @JvmStatic
      fun toVanilla(state: Int): Int {
        val s = forState(state)
        return when (s) {
          DEFAULT -> 0
          DIRECTLY_VERIFIED -> 1
          INTRODUCED -> 1
          DUPLEX_VERIFIED -> 1
          MANUALLY_VERIFIED -> 1
          UNVERIFIED -> 2
        }
      }

      @JvmStatic
      fun toVanilla(status: VerifiedStatus): IdentityTable.VerifiedStatus{
        return IdentityTable.VerifiedStatus.forState(status.toInt())
      }

      /**
       * Much of the code relies on checks of the verification status that are not interested in the finer details.
       * This function can now be called instead of doing 4 comparisons manually.
       * Do not use this to decide if trusted introduction is allowed.
       * @return True is verified, false otherwise.
       */
      @JvmStatic
      fun isVerified(id: RecipientId, verifiedStatus: IdentityTable.VerifiedStatus): Boolean{
        // TODO: Verify cache, adapt if necesssary, then return value.
        assert(false)
        return false
        /**
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          INTRODUCED -> true
          DUPLEX_VERIFIED -> true
          MANUALLY_VERIFIED -> true
          DEFAULT -> false
          UNVERIFIED -> false
        }**/
      }


      /**
       * Adding this in order to be able to change my mind easily on what should unlock a TI.
       * For now, only direct verification unlocks forwarding a contact's public key,
       * in order not to propagate malicious verifications further than one connection.
       *
       * @param verifiedStatus the verification status to be checked
       * @return true if strongly enough verified to unlock forwarding this contact as a
       * trusted introduction, false otherwise
       */
      @JvmStatic
      fun ti_forwardUnlocked(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          DUPLEX_VERIFIED -> true
          INTRODUCED -> false
          MANUALLY_VERIFIED -> false
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }

      /**
       * A recipient can only receive TrustedIntroductions iff they have previously been strongly verified.
       * This function exists as it's own thing to allow for flexible changes.
       *
       * @param verifiedStatus The verification status of the recipient.
       * @return True if this recipient can receive trusted introductions.
       */
      @JvmStatic
      fun ti_recipientUnlocked(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          DUPLEX_VERIFIED -> true
          //INTRODUCED: false (if someone is being MiTmed, an introduction could be sensitive data. So you should be sure who you are talking to before you forward)
          //TODO: Both versions of this have their own pros and cons... Which one should it be?
          // for now, opting to unlock also on introduced in order to give more room to play for the study
          INTRODUCED -> true
          MANUALLY_VERIFIED -> false
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }

      /**
       * Returns true for any non-trivial positive verification status.
       * Used to prompt user when clearing a verification status that is not trivially recoverable and to decide
       * if a channel is secure enough to forward an introduction over.
       */
      @JvmStatic
      fun stronglyVerified(verifiedStatus: VerifiedStatus): Boolean{
        return when (verifiedStatus){
          DIRECTLY_VERIFIED -> true
          DUPLEX_VERIFIED -> true
          INTRODUCED -> true
          MANUALLY_VERIFIED -> false
          DEFAULT -> false
          UNVERIFIED -> false
        }
      }
    }
  }
}

data class IdentityTableExports(val FIRST_USE: String, val TIMESTAMP: String, val NONBLOCKING_APPROVAL: String, val allDatabaseKeys: ArrayList<String>)