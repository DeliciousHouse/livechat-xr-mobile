package com.livechatxr.probe;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

/**
 * Feasibility probe: can a third-party accessibility service draw a banner that is visible
 * while an immersive app (e.g. Population: ONE) is running on Meta Quest?
 *
 * Once enabled, shows a numbered banner for 7 s every 20 s, so it can be checked in-game with no PC.
 * adb control:
 *   am broadcast -a com.livechatxr.probe.SHOW --es text "hello"            (accessibility overlay)
 *   am broadcast -a com.livechatxr.probe.SHOW --es text "hi" --es mode app  (SYSTEM_ALERT_WINDOW overlay)
 *   am broadcast -a com.livechatxr.probe.STOP                               (stop the 20 s repeat)
 * Results go to logcat tag LCXRProbe.
 */
public class BannerService extends AccessibilityService {
    static final String TAG = "LCXRProbe";
    static final String SHOW = "com.livechatxr.probe.SHOW", STOP = "com.livechatxr.probe.STOP";

    private WindowManager wm;
    private TextView view;
    private int count = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hide = new Runnable() {
        public void run() {
            if (view != null) {
                try { wm.removeView(view); } catch (Exception ignored) { }
                view = null;
            }
        }
    };

    private final Runnable tick = new Runnable() {
        public void run() {
            show("🎁 Probe banner #" + (++count) + ": accessibility overlay test", false);
            handler.postDelayed(this, 20000);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (STOP.equals(i.getAction())) {
                handler.removeCallbacks(tick);
                Log.i(TAG, "repeat stopped");
                return;
            }
            String text = i.getStringExtra("text");
            show(text != null ? text : "LiveChat XR probe", "app".equals(i.getStringExtra("mode")));
        }
    };

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter f = new IntentFilter(SHOW);
        f.addAction(STOP);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, f);
        Log.i(TAG, "service connected, sdk " + Build.VERSION.SDK_INT);
        handler.post(tick);
    }

    private void show(String text, boolean appOverlay) {
        hide.run();
        handler.removeCallbacks(hide);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(22);
        tv.setTextColor(0xFFFFFFFF);
        tv.setBackgroundColor(0xCC111118);
        tv.setPadding(32, 20, 32, 20);
        int type = appOverlay ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                              : WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = 40;
        try {
            wm.addView(tv, lp);
            view = tv;
            Log.i(TAG, "shown (" + (appOverlay ? "app" : "accessibility") + " overlay): " + text);
            handler.postDelayed(hide, 7000);
        } catch (Exception e) {
            Log.e(TAG, "addView failed (" + (appOverlay ? "app" : "accessibility") + " overlay)", e);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hide.run();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
