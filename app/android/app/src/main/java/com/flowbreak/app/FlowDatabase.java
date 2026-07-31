package com.flowbreak.app;

import android.content.Context;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {DailyUsageEntity.class, FlowEventEntity.class, ProgressEntity.class, DailySummaryEntity.class},
        version = 3,
        exportSchema = true
)
public abstract class FlowDatabase extends RoomDatabase {
    private static volatile FlowDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `daily_summary` (`date` TEXT NOT NULL, `legacyScreenSeconds` INTEGER NOT NULL, `restCount` INTEGER NOT NULL, `interventionCount` INTEGER NOT NULL, `blockCount` INTEGER NOT NULL, `graceSeconds` INTEGER NOT NULL, PRIMARY KEY(`date`))");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `successfulPullbackCount` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `pullbackOutcomeCount` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `postRestReturnCount` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `postRestTargetSeconds` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `reflectionValue` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `reflectionUpdatedAt` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract FlowDao flowDao();

    static RoomDatabase.Builder<FlowDatabase> builder(
            Context context,
            String databaseName
    ) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                FlowDatabase.class,
                databaseName
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3);
    }

    public static FlowDatabase get(Context context) {
        if (instance == null) {
            synchronized (FlowDatabase.class) {
                if (instance == null) {
                    instance = builder(context, "flowbreak.db").build();
                }
            }
        }
        return instance;
    }
}
