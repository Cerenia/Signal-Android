package org.thoughtcrime.securesms.trustedIntroductions;

public class MinimalStringItem {
  private final String name;

  MinimalStringItem(String s){
    name = s;
  }

  String get(){
    // Shorten it
    String res;
    if (name.contains(" ")){
      res = name.split(" ")[1];
    } else {
      res = name;
    }
    return res;
  }

  String getFullName(){
    return name;
  }

}
