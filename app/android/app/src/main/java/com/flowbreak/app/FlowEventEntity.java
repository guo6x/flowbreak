package com.flowbreak.app;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.Nullable;

@Entity(tableName = "flow_events", indices = {@Index(value = {"idempotencyKey"}, unique = true)})
public class FlowEventEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long timestamp;
    public String type;
    public String packageName;
    public String activity;
    public long durationSeconds;
    public String metadata;
    @Nullable public String idempotencyKey;

    @Ignore
    public FlowEventEntity(
            long timestamp,
            String type,
            String packageName,
            String activity,
            long durationSeconds,
            String metadata
    ) {
        this(timestamp, type, packageName, activity, durationSeconds, metadata, null);
    }

    public FlowEventEntity(
            long timestamp,
            String type,
            String packageName,
            String activity,
            long durationSeconds,
            String metadata,
            @Nullable String idempotencyKey
    ) {
        this.timestamp = timestamp;
        this.type = type;
        this.packageName = packageName;
        this.activity = activity;
        this.durationSeconds = durationSeconds;
        this.metadata = metadata;
        this.idempotencyKey = idempotencyKey;
    }
}
