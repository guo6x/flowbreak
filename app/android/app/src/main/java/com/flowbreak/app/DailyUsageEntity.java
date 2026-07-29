package com.flowbreak.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "daily_usage", primaryKeys = {"date", "packageName"})
public class DailyUsageEntity {
    @NonNull public String date;
    @NonNull public String packageName;
    public long seconds;

    public DailyUsageEntity(@NonNull String date, @NonNull String packageName, long seconds) {
        this.date = date;
        this.packageName = packageName;
        this.seconds = seconds;
    }
}
