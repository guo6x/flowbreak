package com.flowbreak.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** Daily product outcomes that cannot be reconstructed from app-usage rows. */
@Entity(tableName = "daily_summary", primaryKeys = {"date"})
public class DailySummaryEntity {
    @NonNull public String date;
    public long legacyScreenSeconds;
    public int restCount;
    public int interventionCount;
    public int blockCount;
    public long graceSeconds;
    public int pullbackOutcomeCount;
    public int successfulPullbackCount;
    public int postRestReturnCount;
    public long postRestTargetSeconds;
    /** 0=未反馈, 1=没帮助, 2=一般, 3=有帮助. */
    public int reflectionValue;
    public long reflectionUpdatedAt;

    public DailySummaryEntity(
            @NonNull String date,
            long legacyScreenSeconds,
            int restCount,
            int interventionCount,
            int blockCount,
            long graceSeconds,
            int pullbackOutcomeCount,
            int successfulPullbackCount,
            int postRestReturnCount,
            long postRestTargetSeconds,
            int reflectionValue,
            long reflectionUpdatedAt
    ) {
        this.date = date;
        this.legacyScreenSeconds = Math.max(0, legacyScreenSeconds);
        this.restCount = Math.max(0, restCount);
        this.interventionCount = Math.max(0, interventionCount);
        this.blockCount = Math.max(0, blockCount);
        this.graceSeconds = Math.max(0, graceSeconds);
        this.pullbackOutcomeCount = Math.max(0, pullbackOutcomeCount);
        this.successfulPullbackCount = Math.max(0, successfulPullbackCount);
        this.postRestReturnCount = Math.max(0, postRestReturnCount);
        this.postRestTargetSeconds = Math.max(0, postRestTargetSeconds);
        this.reflectionValue = Math.max(0, Math.min(3, reflectionValue));
        this.reflectionUpdatedAt = Math.max(0L, reflectionUpdatedAt);
    }
}
