package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PreferenceUtilsStrongBlockingTest {
    private static final String TEST_PREFS = "PreferenceUtilsStrongBlockingTest";
    private SharedPreferences preferences;

    @Before public void setUp() {
        preferences = RuntimeEnvironment.getApplication()
                .getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @After public void tearDown() {
        preferences.edit().clear().commit();
    }

    @Test public void missingDomesticPreferenceDefaultsOffWithoutWritingDuringRead() {
        assertFalse(PreferenceUtils.isStrongBlockingEnabled(preferences, true));
        assertFalse(preferences.contains(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED));
    }

    @Test public void explicitlySavedDomesticPreferenceSurvivesUpgradeRead() {
        preferences.edit()
                .putBoolean(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED, true)
                .commit();

        assertTrue(PreferenceUtils.isStrongBlockingEnabled(preferences, true));
        assertTrue(preferences.contains(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED));
    }

    @Test public void explicitlySavedDisabledPreferenceRemainsDisabled() {
        preferences.edit()
                .putBoolean(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED, false)
                .commit();

        assertFalse(PreferenceUtils.isStrongBlockingEnabled(preferences, true));
        assertTrue(preferences.contains(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED));
    }

    @Test public void playChannelNeverEnablesDomesticStrongBlocking() {
        preferences.edit()
                .putBoolean(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED, true)
                .commit();

        assertFalse(PreferenceUtils.isStrongBlockingEnabled(preferences, false));
    }
}
