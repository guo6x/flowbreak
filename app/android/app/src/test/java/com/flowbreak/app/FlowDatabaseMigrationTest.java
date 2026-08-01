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
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Room migration tests that run on the JVM via Robolectric.
 *
 * <p>Old-version databases are built by reading the committed Room schema JSON files
 * ({@code schemas/com.flowbreak.app.FlowDatabase/1.json}, {@code 2.json}) from the test
 * assets. The JSON is parsed with {@code org.json} — no third-party dependency.
 *
 * <p>Two categories of tests:
 * <ul>
 *     <li><b>Manual migration tests</b> — create an old-version database, call
 *         {@code Migration.migrate()} directly, and verify the SQL-level outcome.</li>
 *     <li><b>Production builder tests</b> — create an old-version database, close it,
 *         then open it through {@code FlowDatabase.builder()} so Room itself selects and
 *         executes the full upgrade path (including identity-hash validation).</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FlowDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test.db";

    private SupportSQLiteDatabase db;
    private SupportSQLiteOpenHelper legacyHelper;

    @After
    public void tearDown() {
        closeLegacyResources();
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);
    }

    private void closeLegacyResources() {
        if (db != null) {
            try {
                db.close();
            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
            db = null;
        }
        if (legacyHelper != null) {
            try {
                legacyHelper.close();
            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
            legacyHelper = null;
        }
    }

    // ---------------------------------------------------------------------
    // Schema JSON helpers
    // ---------------------------------------------------------------------

    /**
     * Loads the committed Room schema JSON for the given database version from the test assets.
     * The path is {@code com.flowbreak.app.FlowDatabase/<version>.json}.
     */
    private JSONObject loadSchemaJson(int version) throws Exception {
        String path = "com.flowbreak.app.FlowDatabase/" + version + ".json";
        Context context = ApplicationProvider.getApplicationContext();
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = context.getAssets().open(path);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            }
            if (is != null) {
                try { is.close(); } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Creates a fresh database at the requested version by reading the committed Room schema
     * JSON from the test assets. The schema JSON is the sole source of truth for v1 and v2
     * table definitions — there is no hand-copied SQL fallback.
     */
    private SupportSQLiteDatabase createDatabaseAtVersion(int version) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);
        closeLegacyResources();

        JSONObject schema = loadSchemaJson(version);

        SupportSQLiteOpenHelper.Callback callback = new SupportSQLiteOpenHelper.Callback(version) {
            @Override
            public void onCreate(SupportSQLiteDatabase db) {
                try {
                    JSONObject database = schema.getJSONObject("database");
                    JSONArray entities = database.getJSONArray("entities");
                    for (int i = 0; i < entities.length(); i++) {
                        JSONObject entity = entities.getJSONObject(i);
                        String tableName = entity.getString("tableName");
                        String createSql = entity.getString("createSql");
                        String sql = createSql.replace("${TABLE_NAME}", "`" + tableName + "`");
                        db.execSQL(sql);
                    }
                    JSONArray setupQueries = database.getJSONArray("setupQueries");
                    for (int i = 0; i < setupQueries.length(); i++) {
                        db.execSQL(setupQueries.getString(i));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create schema from JSON for version " + version, e);
                }
            }

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                // Not used — migrations are either manual or driven by the production builder.
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
        legacyHelper = factory.create(config);
        db = legacyHelper.getWritableDatabase();
        return db;
    }

    // ---------------------------------------------------------------------
    // Common assertions
    // ---------------------------------------------------------------------

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

    // =====================================================================
    // Manual migration tests — verify individual Migration SQL correctness
    // =====================================================================

    /**
     * 1 → 2: preserves core rows and creates {@code daily_summary}.
     */
    @Test
    public void migrate1To2_preservesCoreDataAndCreatesDailySummary() throws Exception {
        db = createDatabaseAtVersion(1);
        db.execSQL("INSERT INTO daily_usage VALUES ('2026-01-10','com.example.video',321)");
        db.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES (123456789,'rest_complete','com.example.video','breathe',180,'{\"source\":\"migration-test\"}')");
        db.execSQL("INSERT INTO progress VALUES (1,42,3,'2026-01-10','[\"first_rest\"]')");

        FlowDatabase.MIGRATION_1_2.migrate(db);
        db.execSQL("PRAGMA user_version = 2");

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
        assertEquals(4, countUserTables(db));
    }

    /**
     * 2 → 3: preserves summary rows and adds {@code NOT NULL DEFAULT 0} columns.
     */
    @Test
    public void migrate2To3_preservesSummaryAndAddsDefaults() throws Exception {
        db = createDatabaseAtVersion(2);
        db.execSQL("INSERT INTO daily_usage VALUES ('2026-01-11','com.example.social',500)");
        db.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES (999999,'block','com.example.social','',0,'')");
        db.execSQL("INSERT INTO progress VALUES (1,10,1,'2026-01-11','[]')");
        db.execSQL("INSERT INTO daily_summary VALUES ('2026-01-11',987,4,5,2,600)");

        FlowDatabase.MIGRATION_2_3.migrate(db);
        db.execSQL("PRAGMA user_version = 3");

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

    /**
     * 1 → 3: manual migration preserves all data and enables DAO read/write.
     */
    @Test
    public void migrate1To3_preservesAllDataAndEnablesDao() throws Exception {
        db = createDatabaseAtVersion(1);
        db.execSQL("INSERT INTO daily_usage VALUES ('2026-01-10','com.example.video',321)");
        db.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES (123456789,'rest_complete','com.example.video','breathe',180,'{\"source\":\"migration-test\"}')");
        db.execSQL("INSERT INTO progress VALUES (1,42,3,'2026-01-10','[\"first_rest\"]')");

        FlowDatabase.MIGRATION_1_2.migrate(db);
        FlowDatabase.MIGRATION_2_3.migrate(db);
        db.execSQL("PRAGMA user_version = 3");

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
        closeLegacyResources();

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
            dao.saveReflection("2026-01-10", 2, 1234567890L,
                    new FlowEventEntity(1234567890L, "reflection", "", "", 0, ""));
            DailySummaryEntity updated = dao.summaryForDay("2026-01-10");
            assertEquals(2, updated.reflectionValue);
            assertEquals(1234567890L, updated.reflectionUpdatedAt);
            assertTrue(dao.allEvents().size() >= 2);
        } finally {
            database.close();
        }
    }

    /**
     * Fresh v3 install has the correct schema and working DAO.
     */
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
            dao.saveReflection("2026-01-12", 3, 8888888L,
                    new FlowEventEntity(8888888L, "reflection", "", "", 0, ""));
            DailySummaryEntity reflected = dao.summaryForDay("2026-01-12");
            assertEquals(3, reflected.reflectionValue);
            assertEquals(8888888L, reflected.reflectionUpdatedAt);
        } finally {
            database.close();
        }
    }

    // =====================================================================
    // Production builder tests — Room drives the full upgrade path
    // =====================================================================

    /**
     * Creates a legacy database from the committed schema JSON, inserts test data,
     * then <b>completely closes</b> the legacy helper so Room can take over.
     */
    private void prepareAndCloseLegacyDatabase(int version, String date, String pkg, long seconds,
                                               long eventTs, String eventType) throws Exception {
        SupportSQLiteDatabase d = createDatabaseAtVersion(version);
        d.execSQL("INSERT INTO daily_usage VALUES ('" + date + "','" + pkg + "'," + seconds + ")");
        d.execSQL("INSERT INTO flow_events (timestamp,type,packageName,activity,durationSeconds,metadata) VALUES ("
                + eventTs + ",'" + eventType + "','" + pkg + "','test',30,'{}')");
        d.execSQL("INSERT INTO progress VALUES (1,42,3,'" + date + "','[\"first_rest\"]')");
        if (version >= 2) {
            d.execSQL("INSERT INTO daily_summary VALUES ('" + date + "',987,4,5,2,600)");
        }
        closeLegacyResources();
    }

    /**
     * Room must detect a v1 database and execute both MIGRATION_1_2 and MIGRATION_2_3.
     * This test does <b>not</b> call {@code Migration.migrate()} or {@code PRAGMA user_version}
     * manually — the production {@code FlowDatabase.builder()} is the sole driver.
     */
    @Test
    public void productionBuilder_migratesV1ToV3() throws Exception {
        prepareAndCloseLegacyDatabase(1, "2026-01-20", "com.example.v1app", 999,
                111111L, "rest_complete");

        Context context = ApplicationProvider.getApplicationContext();
        FlowDatabase database = FlowDatabase.builder(context, TEST_DB)
                .allowMainThreadQueries()
                .build();
        try {
            FlowDao dao = database.flowDao();
            SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();

            // Room should have executed 1→2→3 automatically.
            assertEquals(3, getUserVersion(db));

            // Original three tables: data intact.
            List<DailyUsageEntity> usage = dao.allUsage();
            assertFalse(usage.isEmpty());
            assertEquals("2026-01-20", usage.get(0).date);
            assertEquals("com.example.v1app", usage.get(0).packageName);
            assertEquals(999L, usage.get(0).seconds);

            List<FlowEventEntity> events = dao.allEvents();
            assertFalse(events.isEmpty());
            assertEquals("rest_complete", events.get(0).type);
            assertEquals(111111L, events.get(0).timestamp);

            ProgressEntity progress = dao.getProgress();
            assertNotNull(progress);
            assertEquals(42, progress.points);
            assertEquals(3, progress.streak);

            // daily_summary exists with 12 columns (6 original + 6 new).
            assertEquals(12, countColumns(db, "daily_summary"));
            assertEquals(4, countUserTables(db));

            // New v3 fields default to 0.
            dao.recordRest("2026-01-20", 120L);
            DailySummaryEntity summary = dao.summaryForDay("2026-01-20");
            assertNotNull(summary);
            assertEquals(1, summary.restCount);
            assertEquals(120L, summary.graceSeconds);
            assertEquals(0, summary.successfulPullbackCount);
            assertEquals(0, summary.pullbackOutcomeCount);
            assertEquals(0, summary.postRestReturnCount);
            assertEquals(0L, summary.postRestTargetSeconds);
            assertEquals(0, summary.reflectionValue);
            assertEquals(0L, summary.reflectionUpdatedAt);

            // DAO write: reflection.
            dao.saveReflection("2026-01-20", 3, 999999L,
                    new FlowEventEntity(999999L, "reflection", "", "", 0, ""));
            DailySummaryEntity updated = dao.summaryForDay("2026-01-20");
            assertEquals(3, updated.reflectionValue);
            assertEquals(999999L, updated.reflectionUpdatedAt);
            assertTrue(dao.allEvents().size() >= 2);
        } finally {
            database.close();
        }
    }

    /**
     * Room must detect a v2 database and execute MIGRATION_2_3 automatically.
     * No manual migration calls, no manual {@code PRAGMA user_version}.
     */
    @Test
    public void productionBuilder_migratesV2ToV3() throws Exception {
        prepareAndCloseLegacyDatabase(2, "2026-01-21", "com.example.v2app", 500,
                222222L, "block");

        Context context = ApplicationProvider.getApplicationContext();
        FlowDatabase database = FlowDatabase.builder(context, TEST_DB)
                .allowMainThreadQueries()
                .build();
        try {
            FlowDao dao = database.flowDao();
            SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();

            assertEquals(3, getUserVersion(db));

            // Original data preserved.
            List<DailyUsageEntity> usage = dao.allUsage();
            assertFalse(usage.isEmpty());
            assertEquals("2026-01-21", usage.get(0).date);
            assertEquals("com.example.v2app", usage.get(0).packageName);
            assertEquals(500L, usage.get(0).seconds);

            // daily_summary old fields preserved.
            DailySummaryEntity summary = dao.summaryForDay("2026-01-21");
            assertNotNull(summary);
            assertEquals(987L, summary.legacyScreenSeconds);
            assertEquals(4, summary.restCount);
            assertEquals(5, summary.interventionCount);
            assertEquals(2, summary.blockCount);
            assertEquals(600L, summary.graceSeconds);

            // New fields default to 0.
            assertEquals(0, summary.successfulPullbackCount);
            assertEquals(0, summary.pullbackOutcomeCount);
            assertEquals(0, summary.postRestReturnCount);
            assertEquals(0L, summary.postRestTargetSeconds);
            assertEquals(0, summary.reflectionValue);
            assertEquals(0L, summary.reflectionUpdatedAt);

            assertEquals(12, countColumns(db, "daily_summary"));
            assertEquals(4, countUserTables(db));

            // DAO writes work.
            dao.recordBlock("2026-01-21");
            DailySummaryEntity updated = dao.summaryForDay("2026-01-21");
            assertEquals(3, updated.blockCount); // was 2, +1

            dao.saveReflection("2026-01-21", 1, 777777L,
                    new FlowEventEntity(777777L, "reflection", "", "", 0, ""));
            DailySummaryEntity reflected = dao.summaryForDay("2026-01-21");
            assertEquals(1, reflected.reflectionValue);
            assertEquals(777777L, reflected.reflectionUpdatedAt);
        } finally {
            database.close();
        }
    }
}
