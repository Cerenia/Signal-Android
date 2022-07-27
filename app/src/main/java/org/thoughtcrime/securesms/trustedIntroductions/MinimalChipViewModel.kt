package org.thoughtcrime.securesms.trustedIntroductions

import androidx.lifecycle.ViewModel


class MinimalChipViewModel : ViewModel() {

  private var selected: ArrayList<String> = ArrayList<String>()

  fun add(selectedString: String){
    selected.add(selectedString)
  }

  fun remove(selectedString: String){
    selected = selected.filterNot { str -> str.equals(selectedString) } as ArrayList<String>
  }

  fun numberOfChips(): Int{
    return selected.size
  }
}