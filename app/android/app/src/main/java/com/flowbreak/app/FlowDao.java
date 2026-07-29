package com.flowbreak.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class FlowDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertUsage(DailyUsageEntity usage);

    @Query("UPDATE daily_usage SET seconds = seconds + :seconds WHERE date = :date AND packageName = :packageName")
    abstract void incrementUsage(String date, String packageName, long seconds);

    @Transaction
    public void addUsage(String date, String packageName, long seconds) {
        insertUsage(new DailyUsageEntity(date, packageName, 0));
        incrementUsage(date, packageName, Math.max(0, seconds));
    }

    @Insert
    abstract long insertEvent(FlowEventEntity event);

    @Query("SELECT * FROM daily_usage WHERE date = :date ORDER BY seconds DESC")
    abstract List<DailyUsageEntity> usageForDay(String date);

    @Query("SELECT * FROM daily_usage ORDER BY date ASC, packageName ASC")
    abstract List<DailyUsageEntity> allUsage();

    @Query("SELECT * FROM daily_usage WHERE date >= :fromDate ORDER BY date ASC, packageName ASC")
    abstract List<DailyUsageEntity> usageSince(String fromDate);

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM daily_usage WHERE date = :date")
    abstract long totalUsageForDay(String date);

    @Query("SELECT * FROM flow_events ORDER BY timestamp DESC LIMIT :limit")
    abstract List<FlowEventEntity> recentEvents(int limit);

    @Query("SELECT * FROM flow_events ORDER BY timestamp ASC")
    abstract List<FlowEventEntity> allEvents();

    @Query("SELECT COUNT(*) FROM flow_events WHERE type = :type AND timestamp >= :since")
    abstract int countEventsSince(String type, long since);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void saveProgress(ProgressEntity progress);

    @Query("SELECT * FROM progress WHERE id = 1")
    abstract ProgressEntity getProgress();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertSummary(DailySummaryEntity summary);

    @Query("UPDATE daily_summary SET legacyScreenSeconds = MAX(legacyScreenSeconds, :screenSeconds), restCount = MAX(restCount, :restCount), interventionCount = MAX(interventionCount, :interventionCount) WHERE date = :date")
    abstract void mergeLegacySummary(String date, long screenSeconds, int restCount, int interventionCount);

    @Query("UPDATE daily_summary SET restCount = restCount + 1, graceSeconds = graceSeconds + :graceSeconds WHERE date = :date")
    abstract void incrementRest(String date, long graceSeconds);

    @Query("UPDATE daily_summary SET blockCount = blockCount + 1 WHERE date = :date")
    abstract void incrementBlock(String date);

    @Query("UPDATE daily_summary SET interventionCount = interventionCount + 1 WHERE date = :date")
    abstract void incrementIntervention(String date);

    @Query("UPDATE daily_summary SET postRestReturnCount = postRestReturnCount + 1 WHERE date = :date")
    abstract void incrementPostRestReturn(String date);

    @Query("UPDATE daily_summary SET pullbackOutcomeCount = pullbackOutcomeCount + 1, successfulPullbackCount = successfulPullbackCount + :successDelta, postRestTargetSeconds = postRestTargetSeconds + :targetSeconds WHERE date = :date")
    abstract void incrementPullbackOutcome(String date, int successDelta, long targetSeconds);

    @Query("UPDATE daily_summary SET reflectionValue = :value, reflectionUpdatedAt = :updatedAt WHERE date = :date")
    abstract void updateReflection(String date, int value, long updatedAt);

    @Query("SELECT * FROM daily_summary WHERE date >= :fromDate ORDER BY date ASC")
    abstract List<DailySummaryEntity> summariesSince(String fromDate);

    @Query("SELECT * FROM daily_summary WHERE date = :date LIMIT 1")
    abstract DailySummaryEntity summaryForDay(String date);

    @Query("SELECT * FROM daily_summary ORDER BY date ASC")
    abstract List<DailySummaryEntity> allSummaries();

    @Transaction
    public void mergeLegacySummarySafely(String date, long screenSeconds, int restCount, int interventionCount) {
        insertSummary(emptySummary(date));
        mergeLegacySummary(date, screenSeconds, restCount, interventionCount);
    }

    @Transaction
    public void recordRest(String date, long graceSeconds) {
        insertSummary(emptySummary(date));
        incrementRest(date, graceSeconds);
    }

    @Transaction
    public void recordBlock(String date) {
        insertSummary(emptySummary(date));
        incrementBlock(date);
    }

    @Transaction
    public void recordIntervention(String date) {
        insertSummary(emptySummary(date));
        incrementIntervention(date);
    }

    @Transaction
    public void recordPostRestReturn(String date, FlowEventEntity event) {
        insertSummary(emptySummary(date));
        incrementPostRestReturn(date);
        insertEvent(event);
    }

    @Transaction
    public void recordPullbackOutcome(
            String date,
            boolean success,
            long targetSeconds,
            FlowEventEntity event
    ) {
        insertSummary(emptySummary(date));
        incrementPullbackOutcome(date, success ? 1 : 0, Math.max(0L, targetSeconds));
        insertEvent(event);
    }

    @Transaction
    public void saveReflection(String date, int value, long updatedAt, FlowEventEntity event) {
        insertSummary(emptySummary(date));
        updateReflection(date, Math.max(1, Math.min(3, value)), updatedAt);
        insertEvent(event);
    }

    @Query("SELECT COUNT(*) FROM flow_events")
    abstract int eventCount();

    @Query("SELECT COUNT(*) FROM daily_usage")
    abstract int usageRowCount();

    @Query("SELECT COALESCE(MAX(timestamp), 0) FROM flow_events")
    abstract long latestEventAt();

    private static DailySummaryEntity emptySummary(String date) {
        return new DailySummaryEntity(date, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Query("DELETE FROM daily_usage")
    abstract void clearUsage();

    @Query("DELETE FROM flow_events")
    abstract void clearEvents();

    @Query("DELETE FROM progress")
    abstract void clearProgress();

    @Query("DELETE FROM daily_summary")
    abstract void clearSummaries();

    @Transaction
    public void clearAll() {
        clearUsage();
        clearEvents();
        clearProgress();
        clearSummaries();
    }
}
