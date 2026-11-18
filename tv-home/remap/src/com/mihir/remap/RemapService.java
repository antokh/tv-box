package com.mihir.remap;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Catches mapped remote keys before the system's global-key handler and opens the chosen app instead. */
public class RemapService extends AccessibilityService {
    static final String TAG = "RemoteRemap";

    /** Set by the settings screen while it waits for "press the button you want to remap". */
    interface Learner { void onKey(int keyCode); }
    static volatile Learner learner;

    private final Handler main = new Handler(Looper.getMainLooper());
    private int swallowUp = -1;

    @Override
    protected void onServiceConnected() {
        Remaps.seed(this);
        Log.i(TAG, "connected, remaps=" + Remaps.all(this));
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (!down && code == swallowUp) { swallowUp = -1; return true; }

        Learner l = learner;
        if (l != null) {
            if (down && event.getRepeatCount() == 0) {
                swallowUp = code;
                main.post(() -> l.onKey(code));
            }
            return true;
        }

        String target = Remaps.get(this, code);
        if (target == null) return false;
        Intent i = Remaps.targetIntent(this, target);
        if (i == null) return false; // app gone: let the button do what it normally does
        if (down && event.getRepeatCount() == 0) {
            Log.i(TAG, KeyEvent.keyCodeToString(code) + " -> " + target);
            startActivity(i);
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
