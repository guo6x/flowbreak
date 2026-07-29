package com.flowbreak.app;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "flow_events")
public class FlowEventEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long timestamp;
    public String type;
    public String packageName;
    public String activity;
    public long durationSeconds;
    public String metadata;

    public FlowEventEntity(
            long timestamp,
            String type,
            String packageName,
            String activity,
            long durationSeconds,
            String metadata
    ) {
        this.timestamp = timestamp;
        this.type = type;
        this.packageName = packageName;
        this.activity = activity;
        this.durationSeconds = durationSeconds;
        this.metadata = metadata;
    }
}
