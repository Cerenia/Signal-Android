package org.thoughtcrime.securesms.trustedIntroductions;

public class MinimalStringModel {
  private final String name;

  MinimalStringModel(String s){
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

  String getName(){
    return name;
  }

}
