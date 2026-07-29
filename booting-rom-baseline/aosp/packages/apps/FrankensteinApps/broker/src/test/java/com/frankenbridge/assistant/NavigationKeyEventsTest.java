package com.frankenbridge.assistant;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

public class NavigationKeyEventsTest {
    @Test
    public void homeUsesHomeKeyCode() {
        assertEquals(KeyEvent.KEYCODE_HOME, NavigationKeyEvents.homeKeyCode());
    }

    @Test
    public void backUsesBackKeyCode() {
        assertEquals(KeyEvent.KEYCODE_BACK, NavigationKeyEvents.backKeyCode());
    }
}
