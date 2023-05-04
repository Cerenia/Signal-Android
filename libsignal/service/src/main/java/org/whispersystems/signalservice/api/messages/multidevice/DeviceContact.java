/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.List;
import java.util.Optional;

public class DeviceContact {

  private final SignalServiceAddress                    address;
  private final Optional<String>                        name;
  private final Optional<SignalServiceAttachmentStream> avatar;
  private final Optional<String>                        color;
  private final Optional<VerifiedMessage>               verified;
  private final Optional<ProfileKey>                    profileKey;
  private final boolean                                 blocked;
  private final Optional<Integer>                       expirationTimer;
  private final Optional<Integer>                       inboxPosition;
  private final boolean                                 archived;
  private final Optional<List<IntroducedMessage>>       introductions;

  public DeviceContact(SignalServiceAddress address,
                       Optional<String> name,
                       Optional<SignalServiceAttachmentStream> avatar,
                       Optional<String> color,
                       Optional<VerifiedMessage> verified,
                       Optional<ProfileKey> profileKey,
                       boolean blocked,
                       Optional<Integer> expirationTimer,
                       Optional<Integer> inboxPosition,
                       boolean archived,
                       Optional<List<IntroducedMessage>> introductions)
  {
    this.address         = address;
    this.name            = name;
    this.avatar          = avatar;
    this.color           = color;
    this.verified        = verified;
    this.profileKey      = profileKey;
    this.blocked         = blocked;
    this.expirationTimer = expirationTimer;
    this.inboxPosition   = inboxPosition;
    this.archived        = archived;
    this.introductions   = introductions;
  }

  public Optional<SignalServiceAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public SignalServiceAddress getAddress() {
    return address;
  }

  public Optional<String> getColor() {
    return color;
  }

  public Optional<VerifiedMessage> getVerified() {
    return verified;
  }

  public Optional<ProfileKey> getProfileKey() {
    return profileKey;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public Optional<Integer> getExpirationTimer() {
    return expirationTimer;
  }

  public Optional<Integer> getInboxPosition() {
    return inboxPosition;
  }

  public boolean isArchived() {
    return archived;
  }

  public Optional<List<IntroducedMessage>> getIntroductions() {
    return introductions;
  }
}
