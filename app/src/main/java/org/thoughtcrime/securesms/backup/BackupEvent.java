package org.thoughtcrime.securesms.backup;

public class BackupEvent {
  public enum Type {
    PROGRESS,
    PROGRESS_VERIFYING,
    FINISHED
  }

  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  private final long estimatedTotalCountTI;
  private final long tiCount;
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end
  private final Type type;
  private final long count;
  private final long estimatedTotalCount;



  public BackupEvent(Type type, long count, long tiCount, long estimatedTotalCount, long estimatedTotalCountTI) {
    this.type                  = type;
    this.count                 = count;
    this.estimatedTotalCount   = estimatedTotalCount;
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
    this.tiCount               = tiCount;
    this.estimatedTotalCountTI = estimatedTotalCountTI;
    // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end
  }

  public Type getType() {
    return type;
  }

  public long getCount() {
    return count;
  }

  public long getEstimatedTotalCount() {
    return estimatedTotalCount;
  }

  public double getCompletionPercentage() {
    if (estimatedTotalCount == 0) {
      return 0;
    }

    return Math.min(99.9f, (double) count * 100L / (double) estimatedTotalCount);
  }

  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH start
  public long getEstimatedTotalCountTI() {
    return estimatedTotalCountTI;
  }

  public long getTICount() {
    return tiCount;
  }
  // TI_GLUE: eNT9XAHgq0lZdbQs2nfH end
}
