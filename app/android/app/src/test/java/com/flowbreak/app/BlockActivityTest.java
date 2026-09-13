package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BlockActivityTest {
    private SharedPreferences prefs;
    private ActivityController<TestableBlockActivity> controller;

    @Before public void setUp() {
        prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(FlowServiceStateStore.PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .clear()
                .putString("blockState", BlockStateMachine.State.BLOCKED.name())
                .commit();
    }

    @After public void tearDown() {
        if (controller != null) controller.destroy();
        prefs.edit().clear().commit();
    }

    @Test public void staleRuntimeClosesActivityBeforeRestEntry() {
        TestableBlockActivity activity = launch();
        Button rest = findButton(activity.getWindow().getDecorView());
        assertNotNull(rest);

        activity.coreRuntimeHealthy = false;
        rest.performClick();

        assertTrue(activity.isFinishing());
        assertEquals(0, activity.startActivityAttempts);
    }

    @Test public void resumedActivityClosesWhenRuntimeBecomesStale() {
        TestableBlockActivity activity = launch();

        activity.coreRuntimeHealthy = false;
        activity.onResume();

        assertTrue(activity.isFinishing());
    }

    @Test public void healthyBlockedEntryStillNavigatesToRest() {
        TestableBlockActivity activity = launch();
        Button rest = findButton(activity.getWindow().getDecorView());
        assertNotNull(rest);

        rest.performClick();

        assertEquals(1, activity.startActivityAttempts);
        assertNotNull(activity.startedIntent);
        assertEquals("rest", activity.startedIntent.getStringExtra("navigateTo"));
    }

    @Test public void resolvedBlockedStateDoesNotRenderFallbackActivity() {
        prefs.edit().putString("blockState", BlockStateMachine.State.GRACE.name()).commit();

        controller = Robolectric.buildActivity(TestableBlockActivity.class).create();

        assertTrue(controller.get().isFinishing());
    }

    private TestableBlockActivity launch() {
        controller = Robolectric.buildActivity(TestableBlockActivity.class)
                .create()
                .start()
                .resume()
                .visible();
        return controller.get();
    }

    private static Button findButton(View view) {
        if (view instanceof Button) return (Button) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button button = findButton(group.getChildAt(i));
            if (button != null) return button;
        }
        return null;
    }

    static class TestableBlockActivity extends BlockActivity {
        boolean coreRuntimeHealthy = true;
        int startActivityAttempts;
        Intent startedIntent;

        @Override boolean currentCoreRuntimeHealthy() {
            return coreRuntimeHealthy;
        }

        @Override public void startActivity(Intent intent) {
            startActivityAttempts++;
            startedIntent = intent;
        }
    }
}
