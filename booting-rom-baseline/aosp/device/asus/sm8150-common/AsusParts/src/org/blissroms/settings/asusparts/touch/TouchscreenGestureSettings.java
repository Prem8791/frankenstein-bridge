/**
 * Copyright (C) 2016 The CyanogenMod project
 *               2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.blissroms.settings.asusparts.touch;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.MenuItem;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.blissroms.settings.asusparts.R;
import org.blissroms.settings.asusparts.FileUtils;
import org.blissroms.settings.asusparts.util.ResourceUtils;

import java.lang.System;

public class TouchscreenGestureSettings extends CollapsingToolbarBaseActivity
        implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, getNewFragment())
                    .commit();
        }
    }

    private PreferenceFragment getNewFragment() {
        return new MainSettingsFragment();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment,
            Preference preference) {
        Fragment instantiate = Fragment.instantiate(this, preference.getFragment(),
            preference.getExtras());
        getFragmentManager().beginTransaction().replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame, instantiate).addToBackStack(preference.getKey()).commit();

        return true;
    }

    public static class MainSettingsFragment extends PreferenceFragment {

        private static final String KEY_TOUCHSCREEN_GESTURE = "touchscreen_gesture";
        private static final String TOUCHSCREEN_GESTURE_TITLE = KEY_TOUCHSCREEN_GESTURE + "_%s_title";

        private Gesture[] mGestures;

        private static class Gesture {
            final int id;
            final int keycode;
            final String name;
            final String path;

            Gesture(int id, int keycode, String name, String path) {
                this.id = id;
                this.keycode = keycode;
                this.name = name;
                this.path = path;
            }
        }

        private static final Gesture[] GESTURES = {
            new Gesture(0, 17,  "Letter W", "/sys/devices/platform/goodix_ts.0/gesture/gesture_w"),
            new Gesture(1, 31,  "Letter S", "/sys/devices/platform/goodix_ts.0/gesture/gesture_s"),
            new Gesture(2, 18,  "Letter e", "/sys/devices/platform/goodix_ts.0/gesture/gesture_e"),
            new Gesture(3, 46,  "Letter C", "/sys/devices/platform/goodix_ts.0/gesture/gesture_c"),
            new Gesture(4, 44,  "Letter Z", "/sys/devices/platform/goodix_ts.0/gesture/gesture_z"),
            new Gesture(5, 47,  "Letter V", "/sys/devices/platform/goodix_ts.0/gesture/gesture_v"),
            new Gesture(6, 103, "SwipeUp Gesture", "/sys/devices/platform/goodix_ts.0/gesture/swipeup"),
        };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            setPreferencesFromResource(R.xml.touchscreen_gestures, rootKey);


            if (isTouchscreenGesturesSupported()) {
                initTouchscreenGestures();
            }
        }

        private void initTouchscreenGestures() {
            mGestures = GESTURES;
            final int[] actions = getDefaultGestureActions(getContext(), mGestures);
            for (final Gesture gesture : mGestures) {
                getPreferenceScreen().addPreference(new TouchscreenGesturePreference(
                        getContext(), gesture, actions[gesture.id]));
            }
        }

        private class TouchscreenGesturePreference extends ListPreference {
            private final Context mContext;
            private final Gesture mGesture;

            public TouchscreenGesturePreference(final Context context,
                                                final Gesture gesture,
                                                final int defaultAction) {
                super(context);
                mContext = context;
                mGesture = gesture;

                setKey(buildPreferenceKey(gesture));
                setEntries(R.array.touchscreen_gesture_action_entries);
                setEntryValues(R.array.touchscreen_gesture_action_values);
                setDefaultValue(String.valueOf(defaultAction));

                setSummary("%s");
                setDialogTitle(R.string.touchscreen_gesture_action_dialog_title);
                setTitle(ResourceUtils.getLocalizedString(
                        context.getResources(), gesture.name, TOUCHSCREEN_GESTURE_TITLE));
            }

            @Override
            public boolean callChangeListener(final Object newValue) {
                final int action = Integer.parseInt(String.valueOf(newValue));
                FileUtils.setValue(mGesture.path, action > 0 ? "1" : "0");
                return super.callChangeListener(newValue);
            }

            @Override
            protected boolean persistString(String value) {
                if (!super.persistString(value)) {
                    return false;
                }
                final int action = Integer.parseInt(String.valueOf(value));
                sendUpdateBroadcast(mContext, mGestures);
                return true;
            }
        }

        public static void restoreTouchscreenGestureStates(final Context context) {
            if (!isTouchscreenGesturesSupported()) {
                return;
            }

            final Gesture[] gestures = GESTURES;
            final int[] actionList = buildActionList(context, gestures);
            for (final Gesture gesture : gestures) {
                FileUtils.setValue(gesture.path, actionList[gesture.id] > 0 ? "1" : "0");
            }

            sendUpdateBroadcast(context, gestures);
        }

        private static boolean isTouchscreenGesturesSupported() {
            // Check if at least one gesture sysfs node exists
            for (Gesture gesture : GESTURES) {
                if (new java.io.File(gesture.path).exists()) {
                    return true;
                }
            }
            return false;
        }

        private static int[] getDefaultGestureActions(final Context context,
                final Gesture[] gestures) {
            final int[] defaultActions = context.getResources().getIntArray(
                    R.array.config_defaultTouchscreenGestureActions);
            if (defaultActions.length >= gestures.length) {
                return defaultActions;
            }

            final int[] filledDefaultActions = new int[gestures.length];
            System.arraycopy(defaultActions, 0, filledDefaultActions, 0, defaultActions.length);
            return filledDefaultActions;
        }

        private static int[] buildActionList(final Context context,
                final Gesture[] gestures) {
            final int[] result = new int[gestures.length];
            final int[] defaultActions = getDefaultGestureActions(context, gestures);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            for (final Gesture gesture : gestures) {
                final String key = buildPreferenceKey(gesture);
                final String defaultValue = String.valueOf(defaultActions[gesture.id]);
                result[gesture.id] = Integer.parseInt(prefs.getString(key, defaultValue));
            }
            return result;
        }

        private static String buildPreferenceKey(final Gesture gesture) {
            return "touchscreen_gesture_" + gesture.id;
        }

        private static void sendUpdateBroadcast(final Context context,
                final Gesture[] gestures) {
            final Intent intent = new Intent(TouchscreenGestureConstants.UPDATE_PREFS_ACTION);
            final int[] keycodes = new int[gestures.length];
            final int[] actions = buildActionList(context, gestures);
            for (final Gesture gesture : gestures) {
                keycodes[gesture.id] = gesture.keycode;
            }
            intent.putExtra(TouchscreenGestureConstants.UPDATE_EXTRA_KEYCODE_MAPPING, keycodes);
            intent.putExtra(TouchscreenGestureConstants.UPDATE_EXTRA_ACTION_MAPPING, actions);
            intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
