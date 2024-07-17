package org.thoughtcrime.securesms.backup;

public class BackupEvent {
  public enum Type {
    PROGRESS,
    PROGRESS_VERIFYING,
    FINISHED
  }

  private final Type type;
  private final long count;
  private final long tiCount;
  private final long estimatedTotalCount;
  private final long estimatedTotalCountTI;


  public BackupEvent(Type type, long count, long tiCount, long estimatedTotalCount, long estimatedTotalCountTI) {
    this.type                  = type;
    this.count                 = count;
    this.tiCount               = tiCount;
    this.estimatedTotalCount   = estimatedTotalCount;
    this.estimatedTotalCountTI = estimatedTotalCountTI;
  }

  public Type getType() {
    return type;
  }

  public long getCount() {
    return count;
  }

  public long getTICount() {
    return tiCount;
  }

  public long getEstimatedTotalCount() {
    return estimatedTotalCount;
  }

  public long getEstimatedTotalCountTI() {
    return estimatedTotalCountTI;
  }

  public double getCompletionPercentage() {
    if (estimatedTotalCount == 0) {
      return 0;
    }

    return Math.min(99.9f, (double) count * 100L / (double) estimatedTotalCount);
  }
}
