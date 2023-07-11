package org.whispersystems.signalservice.api.messages.multidevice;

public class IntroducedMessage {
  public enum IntroducedState {
    PENDING,
    ACCEPTED,
    REJECTED,
    CONFLICTING,
    STALE_PENDING,
    STALE_ACCEPTED,
    STALE_REJECTED,
    STALE_CONFLICTING
  }

  private IntroducedState fromInt(int stateValue){
    return IntroducedState.values()[stateValue];
  }

  // Needed to allow the other party to know how to update the
  public enum SyncType {
    CREATED,
    UPDATED_STATE,
    MASKED,
    DELETED
  }

  private SyncType syncFromInt(int updateValue) {
    return SyncType.values()[updateValue];
  }

  private long introductionId;
  private String introducerServiceId;
  private String serviceId;
  private String identityKey;
  private String name;
  private String number;
  private String predictedFingerprint;

  private       IntroducedState state;
  private       SyncType        syncType;
  private final long            timestamp;

  public IntroducedMessage(long introductionId, String introducerServiceId, String serviceId, String identityKey, String name, String number, String predictedFingerprint, int stateValue, long timestamp) {
    this.introductionId       = introductionId;
    this.introducerServiceId  = introducerServiceId;
    this.serviceId            = serviceId;
    this.identityKey          = identityKey;
    this.name                 = name;
    this.number               = number;
    this.predictedFingerprint = predictedFingerprint;
    this.state                = fromInt(stateValue);
    this.syncType             = SyncType.CREATED;
    this.timestamp            = timestamp;
  }

  public IntroducedMessage(long introductionId, String introducerServiceId, String serviceId, String identityKey, String name, String number, String predictedFingerprint, int stateValue, int syncType, long timestamp) {
    this.introductionId       = introductionId;
    this.introducerServiceId  = introducerServiceId;
    this.serviceId            = serviceId;
    this.identityKey          = identityKey;
    this.name                 = name;
    this.number               = number;
    this.predictedFingerprint = predictedFingerprint;
    this.state                = fromInt(stateValue);
    this.syncType             = syncFromInt(syncType);
    this.timestamp            = timestamp;
  }

  public long getIntroductionId() {
    return introductionId;
  }

  public String getIntroducerServiceId() {
    return introducerServiceId;
  }

  public String getServiceId() {
    return serviceId;
  }

  public String getIdentityKey() {
    return identityKey;
  }

  public String getName() {
    return name;
  }

  public String getNumber() {
    return number;
  }

  public String getPredictedFingerprint() {
    return predictedFingerprint;
  }

  public IntroducedState getState() {
    return state;
  }

  public SyncType getSyncType() {return syncType;}
  public long getTimestamp() {
    return timestamp;
  }
}
