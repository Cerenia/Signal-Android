package org.thoughtcrime.securesms.trustedIntroductions

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.contacts.SelectedContacts
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

class MinimalChipViewModel : ViewModel() {

  private var selected: ArrayList<String> = ArrayList<String>()

  fun add(selectedString: String){
    selected.add(selectedString)
  }

  fun remove(selectedString: String){
    selected = selected.filterNot { str -> str.equals(selectedString) } as ArrayList<String>
  }
}