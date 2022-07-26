package org.thoughtcrime.securesms.trustedIntroductions;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MinimalChipViewModel extends ViewModel {
  private final List<String> selected;

  MinimalChipViewModel(){
    selected = new ArrayList<>();
  }

  void add(String contact){
    selected.add(contact);
  }

  void remove(String contact){
    Object toRemove = null;
    for(String s: selected){
      if (contact.equals(s)){
        toRemove = s;
      }
    }
    if (toRemove != null){
      selected.remove(toRemove);
    }
  }

}
