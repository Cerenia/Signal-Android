package org.thoughtcrime.securesms.trustedIntroductions.receive

import androidx.annotation.WorkerThread
import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database
import org.whispersystems.signalservice.api.util.Preconditions
import java.util.Objects

class ManageViewModel(
  private val manager: ManageManager,
  @get:JvmName("getForgottenPlaceholder") val forgottenPlaceholder: String
) : ViewModel() {

  companion object {
    private val TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageViewModel::class.java))
  }

  private val filter = MutableLiveData("")
  private val introductions = MutableLiveData<List<Pair<TI_Data, IntroducerInformation>>>()
  private var introductionsLoaded = false

  // Filters
  private val showTrusted = MutableLiveData(true)
  private val showDistrusted = MutableLiveData(true)
  private val showStale = MutableLiveData(true)
  private val showConflicting = MutableLiveData(true)

  // UI filters
  fun setShowTrusted(state: Boolean) {
    showTrusted.postValue(state)
  }

  fun setShowDistrusted(state: Boolean) {
    showDistrusted.postValue(state)
  }

  fun setShowStale(state: Boolean) {
    showStale.postValue(state)
  }

  fun setShowConflicting(state: Boolean) {
    showConflicting.postValue(state)
  }

  fun showConflicting(): LiveData<Boolean> = showConflicting
  fun showStale(): LiveData<Boolean> = showStale
  fun showTrusted(): LiveData<Boolean> = showTrusted
  fun showDistrusted(): LiveData<Boolean> = showDistrusted

  fun setTextFilter(filter: String) {
    this.filter.value = filter
  }

  fun getTextFilter(): LiveData<String> = filter

  // Introductions
  fun loadIntroductions() {
    manager.getIntroductions { introductions.postValue(it) }
    introductionsLoaded = true
  }

  fun introductionsLoaded() = introductionsLoaded

  fun deleteIntroduction(introductionId: Long) {
    iterateAndModify(introductionId, object : Modify {
      override fun modifyIntroductionItem(introductionItem: Pair<TI_Data, IntroducerInformation>): Pair<TI_Data, IntroducerInformation>? = null

      override fun databaseCall(introduction: TI_Data): Boolean {
        Preconditions.checkArgument(introduction.id != null)
        return SignalDatabase.tiDatabase.deleteIntroduction(introduction.id!!)
      }

      override fun errorMessage(introductionId: Long): String {
        return "The deletion of introduction $introductionId did not succeed!"
      }
    })
  }

  fun forgetIntroducer(introductionId: Long) {
    iterateAndModify(introductionId, object : Modify {
      override fun modifyIntroductionItem(introductionItem: Pair<TI_Data, IntroducerInformation>): Pair<TI_Data, IntroducerInformation> {
        val oldIntro = introductionItem.first
        val newIntroduction = TI_Data(
          oldIntro.id, oldIntro.state, TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID,
          oldIntro.introduceeServiceId, oldIntro.introduceeName, oldIntro.introduceeNumber,
          oldIntro.introduceeIdentityKey, oldIntro.predictedSecurityNumber, oldIntro.timestamp
        )
        return Pair(newIntroduction, IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder))
      }

      @WorkerThread
      override fun databaseCall(introduction: TI_Data): Boolean {
        return SignalDatabase.tiDatabase.clearIntroducer(introduction)
      }

      override fun errorMessage(introductionId: Long): String {
        return "Error while trying to forget Introducer for introduction: $introductionId"
      }
    })
  }

  fun acceptIntroduction(introductionId: Long) {
    iterateAndModify(introductionId, object : Modify {
      override fun modifyIntroductionItem(introductionItem: Pair<TI_Data, IntroducerInformation>): Pair<TI_Data, IntroducerInformation> {
        val oldIntroduction = introductionItem.first
        val newAcceptState = if (SignalDatabase.tiDatabase.isRecipientUnknown(oldIntroduction.introduceeServiceId))
          TI_Database.State.ACCEPTED_UNKNOWN else TI_Database.State.ACCEPTED

        val newIntroduction = TI_Data(
          oldIntroduction.id, newAcceptState, oldIntroduction.introducerServiceId,
          oldIntroduction.introduceeServiceId, oldIntroduction.introduceeName,
          oldIntroduction.introduceeNumber, oldIntroduction.introduceeIdentityKey,
          oldIntroduction.predictedSecurityNumber, oldIntroduction.timestamp
        )
        return Pair(newIntroduction, introductionItem.second)
      }

      override fun databaseCall(introduction: TI_Data): Boolean {
        return SignalDatabase.tiDatabase.acceptIntroduction(introduction)
      }

      override fun errorMessage(introductionId: Long): String {
        return "Failed to accept introduction: $introductionId"
      }
    })
  }

  fun rejectIntroduction(introductionId: Long) {
    iterateAndModify(introductionId, object : Modify {
      override fun modifyIntroductionItem(introductionItem: Pair<TI_Data, IntroducerInformation>): Pair<TI_Data, IntroducerInformation> {
        val oldIntroduction = introductionItem.first
        val newRejectedState = if (SignalDatabase.tiDatabase.isRecipientUnknown(oldIntroduction.introduceeServiceId))
          TI_Database.State.REJECTED_UNKNOWN else TI_Database.State.REJECTED

        val newIntroduction = TI_Data(
          oldIntroduction.id, newRejectedState, oldIntroduction.introducerServiceId,
          oldIntroduction.introduceeServiceId, oldIntroduction.introduceeName,
          oldIntroduction.introduceeNumber, oldIntroduction.introduceeIdentityKey,
          oldIntroduction.predictedSecurityNumber, oldIntroduction.timestamp
        )
        return Pair(newIntroduction, introductionItem.second)
      }

      override fun databaseCall(introduction: TI_Data): Boolean {
        return SignalDatabase.tiDatabase.rejectIntroduction(introduction)
      }

      override fun errorMessage(introductionId: Long): String {
        return "Failed to reject introduction: $introductionId"
      }
    })
  }

  /**
   * Generic iterator for manipulating the introductions list
   *
   * @param introductionId which introduction to manipulate
   * @param m function handles for modification and database call
   * Does not modify the original introduction
   */
  private fun iterateAndModify(introductionId: Long, m: Modify) {
    val all = introductions.value ?: return
    var current = all[0]
    var i = 1
    while (current.first.id != introductionId && i < all.size) {
      current = all[i++]
    }
    i--

    if (current.first.id != introductionId) {
      throw AssertionError("$TAG: the introduction id was not present in the viewModels List")
    }

    val mutableAll = all.toMutableList()
    mutableAll.removeAt(i)

    val modifiedIntroduction = current.first
    val modifiedCurrent = m.modifyIntroductionItem(current)

    if (modifiedCurrent != null) {
      mutableAll.add(modifiedCurrent)
    }

    val finalIntroduction = modifiedCurrent?.first ?: modifiedIntroduction
    introductions.postValue(mutableAll)

    Log.i(TAG, "Introduction modification complete!")
    SignalExecutors.BOUNDED.execute {
      val res = m.databaseCall(finalIntroduction)
      if (!res) {
        Log.e(TAG, m.errorMessage(introductionId))
      }
    }
  }

  fun getIntroductions(): LiveData<List<Pair<TI_Data, IntroducerInformation>>> = introductions

  private interface Modify {
    /**
     * @param introductionItem the item to be modified. Implementations must return a modified copy and leave the original item untouched.
     * @return a modified introduction list item
     */
    fun modifyIntroductionItem(introductionItem: Pair<TI_Data, IntroducerInformation>): Pair<TI_Data, IntroducerInformation>?

    @WorkerThread
    fun databaseCall(introduction: TI_Data): Boolean

    fun errorMessage(introductionId: Long): String
  }

  data class IntroducerInformation(public val name: String, public val number: String)

  class Factory(private val forgottenPlaceholder: String) : ViewModelProvider.Factory {
    private val manager = ManageManager(SignalDatabase.tiDatabase, forgottenPlaceholder)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return Objects.requireNonNull(modelClass.cast(ManageViewModel(manager, forgottenPlaceholder))) as T
    }
  }
}