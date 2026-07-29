package com.flowbreak.app;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "progress")
public class ProgressEntity {
    @PrimaryKey public int id = 1;
    public int points;
    public int streak;
    public String lastRestDay;
    public String achievementsJson;

    public ProgressEntity(int points, int streak, String lastRestDay, String achievementsJson) {
        this.points = points;
        this.streak = streak;
        this.lastRestDay = lastRestDay;
        this.achievementsJson = achievementsJson;
    }
}
