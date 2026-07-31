package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Room migration tests that run on the JVM via Robolectric.
 *
 * <p>Instead of relying on {@link androidx.room.testing.MigrationTestHelper} (which requires the
 * exported schema JSON files to be present in the merged test assets), these tests create old-version
 * databases directly with {@link FrameworkSQLiteOpenHelperFactory} and execute the {@code migrate}
 * callback manually. The post-migration schema is then verified through {@code PRAGMA table_info}
 * queries and the production DAO, covering the same data-safety guarantees:
 * <ul>
 *     <li>1 → 2 preserves core rows and creates {@code daily_summary}</li>
 *     <li>2 → 3 preserves summary rows and adds NOT NULL DEFAULT 0 columns</li>
 *     <li>1 → 3 preserves all rows and enables full DAO read/write</li>
 *     <li>fresh v3 install has the correct schema and working DAO</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FlowDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test.db";

    private SupportSQLiteDatabase db;

    @After
    public void tearDown() {
        if (db != null) {
            try {
                db.close();
            } catch (Exception ignored) {
                // Best-effort cleanup; the test outcome is already determined.
            }
            db = null;
        }
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Creates a fresh database at the requested version by opening a
     * {@link SupportSQLiteOpenHelper} whose {@code onCreate} callback builds the schema that
     * matches the Room-generated JSON for that version.
     */
    private SupportSQLiteDatabase createDatabaseAtVersion(int version) {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper.Callback callback = new SupportSQLiteOpenHelper.Callback(version) {
            @Override
            public void onCreate(SupportSQLiteDatabase db) {
                createTablesForVersion(db, version);
            }

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                // Migrations are driven manually by the tests.
            }

            @Override
            public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                // Not used.
            }
        };

        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(TEST_DB)
                .callback(callback)
                .build();

        FrameworkSQLiteOpenHelperFactory factory = new FrameworkSQLiteOpenHelperFactory();
        SupportSQLiteOpenHelper helper = factory.create(config);
        return helper.getWritableDatabase();
    }

    /**
     * Builds the table definitions that match the Room-generated schema JSON for the given
     * version. The SQL mirrors the {@code createSql} entries in
     * {@code schemas/com.flowbreak.app.FlowDatabase/<version>.json}.
     */
    private void createTablesForVersion(SupportSQLiteDatabase db, int version) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_usage` (`date` TEXT NOT NULL, `packageName` TEXT NOT NULL, `seconds` INTEGER NOT NULL, PRIMARY KEY(`date`, `packageName`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `flow_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `type` TEXT, `packageName` TEXT, `activity` TEXT, `durationSeconds` INTEGER NOT NULL, `metadata` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `progress` (`id` INTEGER NOT NULL, `points` INTEGER NOT NULL, `streak` INTEGER NOT NULL, `lastRestDay` TEXT, `achievementsJson` TEXT, PRIMARY KEY(`id`))");

        if (version >= 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `daily_summary` (`date` TEXT NOT NULL, `legacyScreenSeconds` INTEGER NOT NULL, `restCount` INTEGER NOT NULL, `interventionCount` INTEGER NOT NULL, `blockCount` INTEGER NOT NULL, `graceSeconds` INTEGER NOT NULL, PRIMARY KEY(`date`))");
        }

        if (version >= 3) {
            db.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `successfulPullbackCount` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `pullbackOutcomeCount` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `postRestReturnCount` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `postRestTargetSeconds` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `reflectionValue` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `daily_summary` ADD COLUMN `reflectionUpdatedAt` INTEGER NOT NULL DEFAULT 0");
        }
    }

    private void setUserVersion(SupportSQLiteDatabase db, int version) {
        db.execSQL("PRAGMA user_version = " + version);
    }

    private int getUserVersion(SupportSQLiteDatabase db) {
        try (Cursor c = db.query("PRAGMA user_version")) {
            assertTrue(c.moveToFirst());
            return c.getInt(0);
        }
    }

    private int countUserTables(SupportSQLiteDatabase db) {
        try (Cursor c = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' "
                        + "AND name NOT LIKE 'android_%' "
                        + "AND name NOT LIKE 'room_%' "
                        + "AND name NOT LIKE 'sqlite_%'")) {
            int count = 0;
            while (c.moveToNext()) {
                count++;
            }
            return count;
        }
    }

    private int countColumns(SupportSQLiteDatabase db, String table) {
        try (Cursor c = db.query("PRAGMA table_info(" + table + ")")) {
            int count = 0;
            while (c.moveToNext()) {
                count++;
            }
            return count;
        }
    }

    // ---------------------------------------------------------------------
    // 1 → 2
    // ---------------------------------------------------------------------

    @Test
    public void migrate1To2_preservesCoreDataAndCreatesDailySummary() throws Exception {
        db = createDatabaseAtVersion(1);
        db.execSQL("INSERT INTO daily_usage VALUES ('2026-01-10','com.example.video',321)");
        db.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES (123456789,'rest_complete','com.example.video','breathe',180,'{\"source\":\"migration-test\"}')");
        db.execSQL("INSERT INTO progress VALUES (1,42,3,'2026-01-10','[\"first_rest\"]')");

        FlowDatabase.MIGRATION_1_2.migrate(db);
        setUserVersion(db, 2);

        try (Cursor c = db.query("SELECT date,packageName,seconds FROM daily_usage")) {
            assertTrue(c.moveToFirst());
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("date")));
            assertEquals("com.example.video", c.getString(c.getColumnIndexOrThrow("packageName")));
            assertEquals(321L, c.getLong(c.getColumnIndexOrThrow("seconds")));
            assertFalse(c.moveToNext());
        }
        try (Cursor c = db.query("SELECT timestamp,type,packageName,activity,durationSeconds,metadata FROM flow_events")) {
            assertTrue(c.moveToFirst());
            assertEquals(123456789L, c.getLong(c.getColumnIndexOrThrow("timestamp")));
            assertEquals("rest_complete", c.getString(c.getColumnIndexOrThrow("type")));
            assertEquals("com.example.video", c.getString(c.getColumnIndexOrThrow("packageName")));
            assertEquals("breathe", c.getString(c.getColumnIndexOrThrow("activity")));
            assertEquals(180L, c.getLong(c.getColumnIndexOrThrow("durationSeconds")));
            assertEquals("{\"source\":\"migration-test\"}", c.getString(c.getColumnIndexOrThrow("metadata")));
        }
        try (Cursor c = db.query("SELECT id,points,streak,lastRestDay,achievementsJson FROM progress")) {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("id")));
            assertEquals(42, c.getInt(c.getColumnIndexOrThrow("points")));
            assertEquals(3, c.getInt(c.getColumnIndexOrThrow("streak")));
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("lastRestDay")));
            assertEquals("[\"first_rest\"]", c.getString(c.getColumnIndexOrThrow("achievementsJson")));
        }
        // daily_summary table must exist with exactly the v2 columns (6 fields, no v3 additions).
        assertEquals(6, countColumns(db, "daily_summary"));
        try (Cursor c = db.query("PRAGMA table_info(daily_summary)")) {
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                assertFalse(name.equals("successfulPullbackCount") || name.equals("pullbackOutcomeCount")
                        || name.equals("postRestReturnCount") || name.equals("postRestTargetSeconds")
                        || name.equals("reflectionValue") || name.equals("reflectionUpdatedAt"));
            }
        }
        assertEquals(2, getUserVersion(db));
        // 4 entity tables; sqlite_sequence (created by AUTOINCREMENT) is filtered out.
        assertEquals(4, countUserTables(db));
    }

    // ---------------------------------------------------------------------
    // 2 → 3
    // ---------------------------------------------------------------------

    @Test
    public void migrate2To3_preservesSummaryAndAddsDefaults() throws Exception {
        db = createDatabaseAtVersion(2);
        db.execSQL("INSERT INTO daily_usage VALUES ('2026-01-11','com.example.social',500)");
        db.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES (999999,'block','com.example.social','',0,'')");
        db.execSQL("INSERT INTO progress VALUES (1,10,1,'2026-01-11','[]')");
        db.execSQL("INSERT INTO daily_summary VALUES ('2026-01-11',987,4,5,2,600)");

        FlowDatabase.MIGRATION_2_3.migrate(db);
        setUserVersion(db, 3);

        try (Cursor c = db.query("SELECT seconds FROM daily_usage WHERE date='2026-01-11'")) {
            assertTrue(c.moveToFirst());
            assertEquals(500L, c.getLong(0));
        }
        try (Cursor c = db.query("SELECT legacyScreenSeconds,restCount,interventionCount,blockCount,graceSeconds FROM daily_summary WHERE date='2026-01-11'")) {
            assertTrue(c.moveToFirst());
            assertEquals(987L, c.getLong(c.getColumnIndexOrThrow("legacyScreenSeconds")));
            assertEquals(4, c.getInt(c.getColumnIndexOrThrow("restCount")));
            assertEquals(5, c.getInt(c.getColumnIndexOrThrow("interventionCount")));
            assertEquals(2, c.getInt(c.getColumnIndexOrThrow("blockCount")));
            assertEquals(600L, c.getLong(c.getColumnIndexOrThrow("graceSeconds")));
        }
        // v3 new columns must exist, be NOT NULL, and default to 0.
        try (Cursor c = db.query("SELECT successfulPullbackCount,pullbackOutcomeCount,postRestReturnCount,postRestTargetSeconds,reflectionValue,reflectionUpdatedAt FROM daily_summary WHERE date='2026-01-11'")) {
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
                String n = c.getString(c.getColumnIndexOrThrow("name"));
                if (n.equals("successfulPullbackCount") || n.equals("pullbackOutcomeCount")
                        || n.equals("postRestReturnCount") || n.equals("postRestTargetSeconds")
                        || n.equals("reflectionValue") || n.equals("reflectionUpdatedAt")) {
                    assertEquals("INTEGER", c.getString(c.getColumnIndexOrThrow("type")));
                    assertEquals(1, c.getInt(c.getColumnIndexOrThrow("notnull")));
                    assertEquals("0", c.getString(c.getColumnIndexOrThrow("dflt_value")));
                }
            }
        }
        assertEquals(3, getUserVersion(db));
    }

    // ---------------------------------------------------------------------
    // 1 → 3
    // ---------------------------------------------------------------------

    @Test
    public void migrate1To3_preservesAllDataAndEnablesDao() throws Exception {
        db = createDatabaseAtVersion(1);
        db.execSQL("INSERT INTO daily_usage VALUES ('2026-01-10','com.example.video',321)");
        db.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES (123456789,'rest_complete','com.example.video','breathe',180,'{\"source\":\"migration-test\"}')");
        db.execSQL("INSERT INTO progress VALUES (1,42,3,'2026-01-10','[\"first_rest\"]')");

        FlowDatabase.MIGRATION_1_2.migrate(db);
        FlowDatabase.MIGRATION_2_3.migrate(db);
        setUserVersion(db, 3);

        try (Cursor c = db.query("SELECT date,packageName,seconds FROM daily_usage")) {
            assertTrue(c.moveToFirst());
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("date")));
            assertEquals("com.example.video", c.getString(c.getColumnIndexOrThrow("packageName")));
            assertEquals(321L, c.getLong(c.getColumnIndexOrThrow("seconds")));
        }
        try (Cursor c = db.query("SELECT timestamp,type,metadata FROM flow_events")) {
            assertTrue(c.moveToFirst());
            assertEquals(123456789L, c.getLong(c.getColumnIndexOrThrow("timestamp")));
            assertEquals("rest_complete", c.getString(c.getColumnIndexOrThrow("type")));
            assertEquals("{\"source\":\"migration-test\"}", c.getString(c.getColumnIndexOrThrow("metadata")));
        }
        try (Cursor c = db.query("SELECT points,streak,lastRestDay,achievementsJson FROM progress WHERE id=1")) {
            assertTrue(c.moveToFirst());
            assertEquals(42, c.getInt(c.getColumnIndexOrThrow("points")));
            assertEquals(3, c.getInt(c.getColumnIndexOrThrow("streak")));
            assertEquals("2026-01-10", c.getString(c.getColumnIndexOrThrow("lastRestDay")));
            assertEquals("[\"first_rest\"]", c.getString(c.getColumnIndexOrThrow("achievementsJson")));
        }
        assertEquals(12, countColumns(db, "daily_summary"));
        assertEquals(4, countUserTables(db));
        assertEquals(3, getUserVersion(db));
        db.close();
        db = null;

        // Open the migrated database through the production Room builder and exercise the DAO.
        Context context = ApplicationProvider.getApplicationContext();
        FlowDatabase database = FlowDatabase.builder(context, TEST_DB)
                .allowMainThreadQueries()
                .build();
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
            dao.saveReflection("2026-01-10", 2, 1234567890L, new FlowEventEntity(1234567890L, "reflection", "", "", 0, ""));
            DailySummaryEntity updated = dao.summaryForDay("2026-01-10");
            assertEquals(2, updated.reflectionValue);
            assertEquals(1234567890L, updated.reflectionUpdatedAt);
            assertTrue(dao.allEvents().size() >= 2);
        } finally {
            database.close();
        }
    }

    // ---------------------------------------------------------------------
    // Fresh v3 install
    // ---------------------------------------------------------------------

    @Test
    public void freshInstallV3_hasCorrectSchemaAndDaoWorks() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);
        FlowDatabase database = FlowDatabase.builder(context, TEST_DB)
                .allowMainThreadQueries()
                .build();
        try {
            FlowDao dao = database.flowDao();
            SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
            assertEquals(3, getUserVersion(db));
            assertEquals(4, countUserTables(db));
            assertEquals(12, countColumns(db, "daily_summary"));
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
            dao.saveReflection("2026-01-12", 3, 8888888L, new FlowEventEntity(8888888L, "reflection", "", "", 0, ""));
            DailySummaryEntity reflected = dao.summaryForDay("2026-01-12");
            assertEquals(3, reflected.reflectionValue);
            assertEquals(8888888L, reflected.reflectionUpdatedAt);
        } finally {
            database.close();
        }
    }
}
