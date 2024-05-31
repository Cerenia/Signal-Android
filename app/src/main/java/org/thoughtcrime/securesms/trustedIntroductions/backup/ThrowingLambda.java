package org.thoughtcrime.securesms.trustedIntroductions.backup;

public interface ThrowingLambda<T, E extends Exception> {
  void accept(T t) throws E;
}
