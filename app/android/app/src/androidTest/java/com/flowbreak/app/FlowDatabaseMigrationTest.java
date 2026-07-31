package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FlowDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test.db";

    @Rule
    public MigrationTestHelper helper;

    public FlowDatabaseMigrationTest() {
        helper = new MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                FlowDatabase.class
        );
    }

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);
    }

    @Test
    public void migrate1To2_preservesCoreDataAndCreatesDailySummary() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        db.execSQL("INSERT INTO `daily_usage` (`date`, `packageName`, `seconds`) VALUES ('2026-01-10', 'com.example.video', 321)");
        db.execSQL("INSERT INTO `flow_events` (`timestamp`, `type`, `packageName`, `activity`, `durationSeconds`, `metadata`) VALUES (123456789, 'rest_complete', 'com.example.video', 'breathe', 180, '{\"source\":\"migration-test\"}')");
        db.execSQL("INSERT INTO `progress` (`id`, `points`, `streak`, `lastRestDay`, `achievementsJson`) VALUES (1, 42, 3, '2026-01-10', '[\"first_rest\"]')");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, FlowDatabase.MIGRATION_1_2);

        try (Cursor c = db.query("SELECT date, packageName, seconds FROM daily_usage")) {
            assertTrue(c.moveToFirst());
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("date")));
            assertEquals("com.example.video", c.getString(c.getColumnIndexOrThrow("packageName")));
            assertEquals(321L, c.getLong(c.getColumnIndexOrThrow("seconds")));
            assertFalse(c.moveToNext());
        }
        try (Cursor c = db.query("SELECT timestamp, type, packageName, activity, durationSeconds, metadata FROM flow_events")) {
            assertTrue(c.moveToFirst());
            assertEquals(123456789L, c.getLong(c.getColumnIndexOrThrow("timestamp")));
            assertEquals("rest_complete", c.getString(c.getColumnIndexOrThrow("type")));
            assertEquals("com.example.video", c.getString(c.getColumnIndexOrThrow("packageName")));
            assertEquals("breathe", c.getString(c.getColumnIndexOrThrow("activity")));
            assertEquals(180L, c.getLong(c.getColumnIndexOrThrow("durationSeconds")));
            assertEquals("{\"source\":\"migration-test\"}", c.getString(c.getColumnIndexOrThrow("metadata")));
        }
        try (Cursor c = db.query("SELECT id, points, streak, lastRestDay, achievementsJson FROM progress")) {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("id")));
            assertEquals(42, c.getInt(c.getColumnIndexOrThrow("points")));
            assertEquals(3, c.getInt(c.getColumnIndexOrThrow("streak")));
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("lastRestDay")));
            assertEquals("[\"first_rest\"]", c.getString(c.getColumnIndexOrThrow("achievementsJson")));
        }
        try (Cursor c = db.query("PRAGMA table_info(daily_summary)")) {
            int fieldCount = 0;
            boolean hasV3Field = false;
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                fieldCount++;
                if (name.equals("successfulPullbackCount") || name.equals("pullbackOutcomeCount")
                        || name.equals("postRestReturnCount") || name.equals("postRestTargetSeconds")
                        || name.equals("reflectionValue") || name.equals("reflectionUpdatedAt")) {
                    hasV3Field = true;
                }
            }
            assertEquals(6, fieldCount);
            assertFalse(hasV3Field);
        }
        try (Cursor c = db.query("PRAGMA user_version")) {
            assertTrue(c.moveToFirst());
            assertEquals(2, c.getInt(0));
        }
        try (Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'")) {
            int tableCount = 0;
            while (c.moveToNext()) {
                tableCount++;
                String name = c.getString(0);
                assertTrue(name.equals("daily_usage") || name.equals("flow_events") || name.equals("progress") || name.equals("daily_summary"));
            }
            assertEquals(4, tableCount);
        }
        db.close();
    }

    @Test
    public void migrate2To3_preservesSummaryAndAddsDefaults() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.execSQL("INSERT INTO `daily_usage` (`date`, `packageName`, `seconds`) VALUES ('2026-01-11', 'com.example.social', 500)");
        db.execSQL("INSERT INTO `flow_events` (`timestamp`, `type`, `packageName`, `activity`, `durationSeconds`, `metadata`) VALUES (999999, 'block', 'com.example.social', '', 0, '')");
        db.execSQL("INSERT INTO `progress` (`id`, `points`, `streak`, `lastRestDay`, `achievementsJson`) VALUES (1, 10, 1, '2026-01-11', '[]')");
        db.execSQL("INSERT INTO `daily_summary` (`date`, `legacyScreenSeconds`, `restCount`, `interventionCount`, `blockCount`, `graceSeconds`) VALUES ('2026-01-11', 987, 4, 5, 2, 600)");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, FlowDatabase.MIGRATION_2_3);

        try (Cursor c = db.query("SELECT seconds FROM daily_usage WHERE date='2026-01-11'")) {
            assertTrue(c.moveToFirst());
            assertEquals(500L, c.getLong(0));
        }
        try (Cursor c = db.query("SELECT legacyScreenSeconds, restCount, interventionCount, blockCount, graceSeconds FROM daily_summary WHERE date='2026-01-11'")) {
            assertTrue(c.moveToFirst());
            assertEquals(987L, c.getLong(c.getColumnIndexOrThrow("legacyScreenSeconds")));
            assertEquals(4, c.getInt(c.getColumnIndexOrThrow("restCount")));
            assertEquals(5, c.getInt(c.getColumnIndexOrThrow("interventionCount")));
            assertEquals(2, c.getInt(c.getColumnIndexOrThrow("blockCount")));
            assertEquals(600L, c.getLong(c.getColumnIndexOrThrow("graceSeconds")));
        }
        try (Cursor c = db.query("SELECT successfulPullbackCount, pullbackOutcomeCount, postRestReturnCount, postRestTargetSeconds, reflectionValue, reflectionUpdatedAt FROM daily_summary WHERE date='2026-01-11'")) {
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("successfulPullbackCount")));
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("pullbackOutcomeCount")));
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("postRestReturnCount")));
            assertEquals(0L, c.getLong(c.getColumnIndexOrThrow("postRestTargetSeconds")));
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("reflectionValue")));
            assertEquals(0L, c.getLong(c.getColumnIndexOrThrow("reflectionUpdatedAt")));
        }
        try (Cursor c = db.query("PRAGMA table_info(daily_summary)")) {
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                String type = c.getString(c.getColumnIndexOrThrow("type"));
                int notNull = c.getInt(c.getColumnIndexOrThrow("notnull"));
                String dflt = c.getString(c.getColumnIndexOrThrow("dflt_value"));
                if (name.equals("successfulPullbackCount") || name.equals("pullbackOutcomeCount") || name.equals("postRestReturnCount") || name.equals("reflectionValue") || name.equals("postRestTargetSeconds") || name.equals("reflectionUpdatedAt")) {
                    assertEquals("INTEGER", type);
                    assertEquals(1, notNull);
                    assertEquals("0", dflt);
                }
            }
        }
        try (Cursor c = db.query("PRAGMA user_version")) {
            assertTrue(c.moveToFirst());
            assertEquals(3, c.getInt(0));
        }
        db.close();
    }

    @Test
    public void migrate1To3_preservesAllDataAndEnablesDao() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        db.execSQL("INSERT INTO `daily_usage` (`date`, `packageName`, `seconds`) VALUES ('2026-01-10', 'com.example.video', 321)");
        db.execSQL("INSERT INTO `flow_events` (`timestamp`, `type`, `packageName`, `activity`, `durationSeconds`, `metadata`) VALUES (123456789, 'rest_complete', 'com.example.video', 'breathe', 180, '{\"source\":\"migration-test\"}')");
        db.execSQL("INSERT INTO `progress` (`id`, `points`, `streak`, `lastRestDay`, `achievementsJson`) VALUES (1, 42, 3, '2026-01-10', '[\"first_rest\"]')");
        db.close();

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, FlowDatabase.MIGRATION_1_2, FlowDatabase.MIGRATION_2_3);

        try (Cursor c = db.query("SELECT date, packageName, seconds FROM daily_usage")) {
            assertTrue(c.moveToFirst());
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("date")));
            assertEquals("com.example.video", c.getString(c.getColumnIndexOrThrow("packageName")));
            assertEquals(321L, c.getLong(c.getColumnIndexOrThrow("seconds")));
        }
        try (Cursor c = db.query("SELECT timestamp, type, metadata FROM flow_events")) {
            assertTrue(c.moveToFirst());
            assertEquals(123456789L, c.getLong(c.getColumnIndexOrThrow("timestamp")));
            assertEquals("rest_complete", c.getString(c.getColumnIndexOrThrow("type")));
            assertEquals("{\"source\":\"migration-test\"}", c.getString(c.getColumnIndexOrThrow("metadata")));
        }
        try (Cursor c = db.query("SELECT points, streak, lastRestDay, achievementsJson FROM progress WHERE id=1")) {
            assertTrue(c.moveToFirst());
            assertEquals(42, c.getInt(c.getColumnIndexOrThrow("points")));
            assertEquals(3, c.getInt(c.getColumnIndexOrThrow("streak")));
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("lastRestDay")));
            assertEquals("[\"first_rest\"]", c.getString(c.getColumnIndexOrThrow("achievementsJson")));
        }
        try (Cursor c = db.query("PRAGMA table_info(daily_summary)")) {
            int fieldCount = 0;
            while (c.moveToNext()) fieldCount++;
            assertEquals(12, fieldCount);
        }
        try (Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'")) {
            int tableCount = 0;
            while (c.moveToNext()) {
                tableCount++;
                String name = c.getString(0);
                assertTrue(name.equals("daily_usage") || name.equals("flow_events") || name.equals("progress") || name.equals("daily_summary"));
            }
            assertEquals(4, tableCount);
        }
        db.close();

        Context context = ApplicationProvider.getApplicationContext();
        FlowDatabase database = FlowDatabase.builder(context, TEST_DB).build();
        try {
            FlowDao dao = database.flowDao();

            List<DailyUsageEntity> usage = dao.allUsage();
            assertFalse(usage.isEmpty());
            assertEquals("2026-01-10", usage.get(0).date);
            assertEquals("com.example.video", usage.get(0).packageName);
            assertEquals(321L, usage.get(0).seconds);

            List<FlowEventEntity> events = dao.allEvents();
            assertFalse(events.isEmpty());
            assertEquals("rest_complete", events.get(0).type);

            ProgressEntity progress = dao.getProgress();
            assertNotNull(progress);
            assertEquals(42, progress.points);
            assertEquals(3, progress.streak);

            dao.recordRest("2026-01-10", 120L);
            DailySummaryEntity summary = dao.summaryForDay("2026-01-10");
            assertNotNull(summary);
            assertEquals(1, summary.restCount);
            assertEquals(120L, summary.graceSeconds);
            assertEquals(0, summary.successfulPullbackCount);
            assertEquals(0, summary.reflectionValue);

            dao.saveReflection("2026-01-10", 2, 1234567890L,
                    new FlowEventEntity(1234567890L, "reflection", "", "", 0, ""));
            DailySummaryEntity updated = dao.summaryForDay("2026-01-10");
            assertEquals(2, updated.reflectionValue);
            assertEquals(1234567890L, updated.reflectionUpdatedAt);

            List<FlowEventEntity> allEvents = dao.allEvents();
            assertTrue(allEvents.size() >= 2);
        } finally {
            database.close();
        }
    }

    @Test
    public void freshInstallV3_hasCorrectSchemaAndDaoWorks() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);

        FlowDatabase database = FlowDatabase.builder(context, TEST_DB).build();
        try {
            FlowDao dao = database.flowDao();
            SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();

            try (Cursor c = db.query("PRAGMA user_version")) {
                assertTrue(c.moveToFirst());
                assertEquals(3, c.getInt(0));
            }
            try (Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'")) {
                int tableCount = 0;
                while (c.moveToNext()) {
                    tableCount++;
                    String name = c.getString(0);
                    assertTrue(name.equals("daily_usage") || name.equals("flow_events") || name.equals("progress") || name.equals("daily_summary"));
                }
                assertEquals(4, tableCount);
            }
            try (Cursor c = db.query("PRAGMA table_info(daily_summary)")) {
                int fieldCount = 0;
                while (c.moveToNext()) fieldCount++;
                assertEquals(12, fieldCount);
            }

            dao.addUsage("2026-01-12", "com.example.test", 100);
            List<DailyUsageEntity> usage = dao.allUsage();
            assertFalse(usage.isEmpty());
            assertEquals(100L, usage.get(0).seconds);

            dao.insertEvent(new FlowEventEntity(5555555L, "test_event", "com.example.test", "test", 30, "{}"));
            assertTrue(dao.eventCount() >= 1);

            dao.saveProgress(new ProgressEntity(50, 5, "2026-01-12", "[\"test\"]"));
            ProgressEntity progress = dao.getProgress();
            assertNotNull(progress);
            assertEquals(50, progress.points);
            assertEquals(5, progress.streak);

            dao.recordBlock("2026-01-12");
            DailySummaryEntity summary = dao.summaryForDay("2026-01-12");
            assertNotNull(summary);
            assertEquals(1, summary.blockCount);

            dao.saveReflection("2026-01-12", 3, 8888888L,
                    new FlowEventEntity(8888888L, "reflection", "", "", 0, ""));
            DailySummaryEntity reflected = dao.summaryForDay("2026-01-12");
            assertEquals(3, reflected.reflectionValue);
            assertEquals(8888888L, reflected.reflectionUpdatedAt);
        } finally {
            database.close();
        }
    }
}
