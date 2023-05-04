/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class DeviceContactsOutputStream extends ChunkedOutputStream {

  public DeviceContactsOutputStream(OutputStream out) {
    super(out);
  }

  public void write(DeviceContact contact) throws IOException {
    writeContactDetails(contact);
    writeAvatarImage(contact);
  }

  public void close() throws IOException {
    out.close();
  }

  private void writeAvatarImage(DeviceContact contact) throws IOException {
    if (contact.getAvatar().isPresent()) {
      writeStream(contact.getAvatar().get().getInputStream());
    }
  }

  private void writeContactDetails(DeviceContact contact) throws IOException {
    SignalServiceProtos.ContactDetails.Builder contactDetails = SignalServiceProtos.ContactDetails.newBuilder();

    contactDetails.setUuid(contact.getAddress().getServiceId().toString());

    if (contact.getAddress().getNumber().isPresent()) {
      contactDetails.setNumber(contact.getAddress().getNumber().get());
    }

    if (contact.getName().isPresent()) {
      contactDetails.setName(contact.getName().get());
    }

    if (contact.getAvatar().isPresent()) {
      SignalServiceProtos.ContactDetails.Avatar.Builder avatarBuilder = SignalServiceProtos.ContactDetails.Avatar.newBuilder();
      avatarBuilder.setContentType(contact.getAvatar().get().getContentType());
      avatarBuilder.setLength((int)contact.getAvatar().get().getLength());
      contactDetails.setAvatar(avatarBuilder);
    }

    if (contact.getColor().isPresent()) {
      contactDetails.setColor(contact.getColor().get());
    }

    if (contact.getVerified().isPresent()) {
      SignalServiceProtos.Verified.State state;

      switch (contact.getVerified().get().getVerified()) {
        case VERIFIED:   state = SignalServiceProtos.Verified.State.VERIFIED;   break;
        case UNVERIFIED: state = SignalServiceProtos.Verified.State.UNVERIFIED; break;
        case DIRECTLY_VERIFIED: state = SignalServiceProtos.Verified.State.DIRECTLY_VERIFIED; break;
        case INTRODUCED: state = SignalServiceProtos.Verified.State.INTRODUCED; break;
        case DUPLEX_VERIFIED: state = SignalServiceProtos.Verified.State.DUPLEX_VERIFIED; break;
        default:         state = SignalServiceProtos.Verified.State.DEFAULT;    break;
      }

      SignalServiceProtos.Verified.Builder verifiedBuilder = SignalServiceProtos.Verified.newBuilder()
                                                                                         .setIdentityKey(ByteString.copyFrom(contact.getVerified().get().getIdentityKey().serialize()))
                                                                                         .setDestinationUuid(contact.getVerified().get().getDestination().getServiceId().toString())
                                                                                         .setState(state);

      contactDetails.setVerified(verifiedBuilder.build());
    }

    if (contact.getProfileKey().isPresent()) {
      contactDetails.setProfileKey(ByteString.copyFrom(contact.getProfileKey().get().serialize()));
    }

    if (contact.getExpirationTimer().isPresent()) {
      contactDetails.setExpireTimer(contact.getExpirationTimer().get());
    }

    if (contact.getInboxPosition().isPresent()) {
      contactDetails.setInboxPosition(contact.getInboxPosition().get());
    }

    contactDetails.setBlocked(contact.isBlocked());
    contactDetails.setArchived(contact.isArchived());

    if (contact.getIntroductions().isPresent()){
      List<IntroducedMessage> intros = contact.getIntroductions().get();
      int idx=0;

      for (IntroducedMessage im : intros) {
        SignalServiceProtos.Introduced.State state = null;
        switch (im.getState()) {
          case PENDING:
            state = SignalServiceProtos.Introduced.State.PENDING;
            break;
          case ACCEPTED:
            state = SignalServiceProtos.Introduced.State.ACCEPTED;
            break;
          case REJECTED:
            state = SignalServiceProtos.Introduced.State.REJECTED;
            break;
          case CONFLICTING:
            state = SignalServiceProtos.Introduced.State.CONFLICTING;
            break;
          case STALE_PENDING:
            state = SignalServiceProtos.Introduced.State.STALE_PENDING;
            break;
          case STALE_ACCEPTED:
            state = SignalServiceProtos.Introduced.State.STALE_ACCEPTED;
            break;
          case STALE_REJECTED:
            state = SignalServiceProtos.Introduced.State.STALE_REJECTED;
            break;
          case STALE_CONFLICTING:
            state = SignalServiceProtos.Introduced.State.STALE_CONFLICTING;
            break;
        }
        SignalServiceProtos.Introduced intro = SignalServiceProtos.Introduced.newBuilder()
            .setIntroductionId(im.getIntroductionId())
            .setIntroducerServiceId(im.getIntroducerServiceId())
            .setServiceId(im.getServiceId())
            .setIdentityKey(im.getIdentityKey())
            .setName(im.getName()).setNumber(im.getNumber())
            .setPredictedFingerprint(im.getPredictedFingerprint())
            .setState(state)
            .setTimestamp(im.getTimestamp()).build();
        contactDetails.addIntroductions(idx, intro);
        idx++;
      }
    }

    byte[] serializedContactDetails = contactDetails.build().toByteArray();

    writeVarint32(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }

}
