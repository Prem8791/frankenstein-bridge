package com.frankenbridge.assistant;

import android.view.KeyEvent;

final class NavigationKeyEvents {
    private NavigationKeyEvents() {
    }

    static int homeKeyCode() {
        return KeyEvent.KEYCODE_HOME;
    }

    static int backKeyCode() {
        return KeyEvent.KEYCODE_BACK;
    }
}
