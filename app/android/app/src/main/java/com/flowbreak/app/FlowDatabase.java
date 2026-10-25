package com.flowbreak.app;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

@Database(
        entities = {DailyUsageEntity.class, FlowEventEntity.class, ProgressEntity.class, DailySummaryEntity.class},
        version = FlowDatabaseSchema.VERSION,
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

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `flow_events` ADD COLUMN `idempotencyKey` TEXT");
            backfillLegacyPullbackIdempotencyKeys(database);
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_flow_events_idempotencyKey` "
                            + "ON `flow_events` (`idempotencyKey`)"
            );
        }
    };

    /**
     * v3 wrote pullback edges before its SharedPreferences checkpoint, but without a stable
     * database key. Bind one legacy row per event/session to the v4 key so a post-upgrade replay
     * cannot increment the daily summary a second time.
     */
    private static void backfillLegacyPullbackIdempotencyKeys(SupportSQLiteDatabase database) {
        List<Long> eventIds = new ArrayList<>();
        List<String> idempotencyKeys = new ArrayList<>();
        Set<String> assignedKeys = new HashSet<>();
        try (Cursor cursor = database.query(
                "SELECT `id`, `type`, `metadata` FROM `flow_events` "
                        + "WHERE `idempotencyKey` IS NULL AND `type` IN "
                        + "('post_rest_return', 'pullback_success', 'pullback_unresolved') "
                        + "ORDER BY `id` ASC"
        )) {
            while (cursor.moveToNext()) {
                String key = legacyPullbackIdempotencyKey(
                        cursor.getString(1),
                        cursor.getString(2)
                );
                if (key == null || !assignedKeys.add(key)) continue;
                eventIds.add(cursor.getLong(0));
                idempotencyKeys.add(key);
            }
        }
        for (int index = 0; index < eventIds.size(); index++) {
            database.execSQL(
                    "UPDATE `flow_events` SET `idempotencyKey` = ? WHERE `id` = ?",
                    new Object[] {idempotencyKeys.get(index), eventIds.get(index)}
            );
        }
    }

    private static String legacyPullbackIdempotencyKey(String type, String metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            long sessionId = new JSONObject(metadata).optLong("sessionId", 0L);
            if (sessionId <= 0L) return null;
            if ("post_rest_return".equals(type)) {
                return "pullback:" + sessionId + ":return";
            }
            if ("pullback_success".equals(type) || "pullback_unresolved".equals(type)) {
                return "pullback:" + sessionId + ":outcome";
            }
        } catch (JSONException ignored) {
            // Keep malformed/legacy non-session events unkeyed; no safe replay identity exists.
        }
        return null;
    }

    public abstract FlowDao flowDao();

    static RoomDatabase.Builder<FlowDatabase> builder(
            Context context,
            String databaseName
    ) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                FlowDatabase.class,
                databaseName
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4);
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
