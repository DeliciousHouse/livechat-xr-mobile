package com.livechatxr.probe;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.view.Display;
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
 *   am broadcast -a com.livechatxr.probe.SHOW --es text "hi" --ei display 7   (put it on a specific display)
 *   am broadcast -a com.livechatxr.probe.SHOW --es text "hi" --es mode ovr [--es as <pkg>]  (OVR Metrics Tool overlay)
 *   am broadcast -a com.livechatxr.probe.STOP                               (stop the 20 s repeat)
 * Results go to logcat tag LCXRProbe.
 */
public class BannerService extends AccessibilityService {
    static final String TAG = "LCXRProbe";
    static final String SHOW = "com.livechatxr.probe.SHOW", STOP = "com.livechatxr.probe.STOP";

    private WindowManager wm, shownOn;
    private TextView view;
    private int count = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hide = new Runnable() {
        public void run() {
            if (view != null) {
                try { shownOn.removeView(view); } catch (Exception ignored) { }
                view = null;
            }
        }
    };

    private final Runnable tick = new Runnable() {
        public void run() {
            show("🎁 Probe banner #" + (++count) + ": accessibility overlay test", false, 0);
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
            if ("ovr".equals(i.getStringExtra("mode"))) {  // text into OVR Metrics Tool's overlay
                ovr(text != null ? text : "LiveChat XR probe", i.getStringExtra("as"));
                return;
            }
            show(text != null ? text : "LiveChat XR probe", "app".equals(i.getStringExtra("mode")), i.getIntExtra("display", 0));
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

    private void show(String text, boolean appOverlay, int displayId) {
        hide.run();
        handler.removeCallbacks(hide);
        Context ctx = this;
        WindowManager target = wm;
        if (displayId != 0) {  // Quest renders panels from per-panel virtual displays, not display 0
            Display d = ((DisplayManager) getSystemService(DISPLAY_SERVICE)).getDisplay(displayId);
            if (d == null) { Log.e(TAG, "no display " + displayId); return; }
            ctx = createDisplayContext(d);
            target = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
        }
        TextView tv = new TextView(ctx);
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
            target.addView(tv, lp);
            shownOn = target;
            view = tv;
            Log.i(TAG, "shown (" + (appOverlay ? "app" : "accessibility") + " overlay, display " + displayId + "): " + text);
            handler.postDelayed(hide, 7000);
        } catch (Exception e) {
            Log.e(TAG, "addView failed (" + (appOverlay ? "app" : "accessibility") + " overlay, display " + displayId + ")", e);
        }
    }

    // ---- OVR Metrics Tool overlay text (Meta's metrics service; needs OVR Metrics Tool from the Store) ----
    static final String METRICS_PKG = "com.oculus.ovrmonitormetricsservice";
    static final String METRICS_IFACE = "com.oculus.metrics.OVRMonitorMetricsServiceInterface";
    static final int TX_SET_OVERLAY = 6, TX_SET_OVERLAY2 = 13;  // setOverlayDebugString / ...2
    static final long V3_MIN_SERVICE_VERSION = 480513431L;
    private IBinder metrics;
    private String[] pending;

    private final ServiceConnection metricsConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
            metrics = b;
            Log.i(TAG, "metrics service connected");
            if (pending != null) { sendOvr(pending[0], pending[1]); pending = null; }
        }
        public void onServiceDisconnected(ComponentName n) { metrics = null; Log.i(TAG, "metrics service disconnected"); }
    };

    static final int TX_UPDATE_METRICS3 = 10;  // updateMetrics3(app, activity, timeMs, json)
    static final String[] HEARTBEAT_ACTIVITIES = {"LiveChatXR.a", "LiveChatXR.b"};

    // Metrics heartbeat: registers this app as active (MultiAppManager, valid for 2 s) and gives it a
    // HistoryRecord, which the service needs before it will show our debug string. Two activity names,
    // because multi-app mode needs more than two active app/activity pairs (plus the foreground game).
    private final Runnable heartbeat = new Runnable() {
        public void run() {
            if (metrics != null) {
                for (String act : HEARTBEAT_ACTIVITIES) {
                    Parcel d = Parcel.obtain(), r = Parcel.obtain();
                    try {
                        d.writeInterfaceToken(METRICS_IFACE);
                        d.writeString(getPackageName());
                        d.writeString(act);
                        d.writeLong(System.currentTimeMillis());
                        d.writeString("{}");
                        metrics.transact(TX_UPDATE_METRICS3, d, r, 0);
                        r.readException();
                        if (r.readInt() == 0) Log.w(TAG, "heartbeat rejected for " + act);
                    } catch (Exception e) {
                        Log.e(TAG, "heartbeat failed", e);
                    } finally {
                        d.recycle();
                        r.recycle();
                    }
                }
            }
            handler.postDelayed(this, 1000);
        }
    };
    private boolean heartbeatOn = false;

    private void ovr(String text, String asPackage) {
        if (!heartbeatOn) { heartbeatOn = true; handler.post(heartbeat); Log.i(TAG, "metrics heartbeat started"); }
        if (metrics != null) { sendOvr(text, asPackage); return; }
        pending = new String[] {text, asPackage};
        Intent i = new Intent().setComponent(new ComponentName(METRICS_PKG, METRICS_PKG + ".MetricsService"));
        try {
            Log.i(TAG, "bind metrics service: " + bindService(i, metricsConn, BIND_AUTO_CREATE));
        } catch (Exception e) {
            Log.e(TAG, "bind metrics service failed", e);
        }
    }

    /** Raw binder call: (pkg, [activity,] timeMs, text) -> boolean. Tries the v2 call on new services, then v1. */
    private void sendOvr(String text, String asPackage) {
        long ver = 0;
        try { ver = getPackageManager().getPackageInfo(METRICS_PKG, 0).getLongVersionCode(); } catch (Exception ignored) { }
        String pkg = asPackage != null ? asPackage : getPackageName();
        int[] order = ver >= V3_MIN_SERVICE_VERSION ? new int[] {TX_SET_OVERLAY2, TX_SET_OVERLAY} : new int[] {TX_SET_OVERLAY};
        for (int tx : order) {
            Parcel d = Parcel.obtain(), r = Parcel.obtain();
            try {
                d.writeInterfaceToken(METRICS_IFACE);
                d.writeString(pkg);
                if (tx == TX_SET_OVERLAY2) d.writeString(HEARTBEAT_ACTIVITIES[0]);
                d.writeLong(System.currentTimeMillis());
                d.writeString(text);
                metrics.transact(tx, d, r, 0);
                r.readException();
                boolean ok = r.readInt() != 0;
                Log.i(TAG, "overlay debug string tx " + tx + " as " + pkg + " (service v" + ver + ") -> " + ok + ": " + text);
                if (ok) return;
            } catch (Exception e) {
                Log.e(TAG, "overlay debug string tx " + tx + " failed", e);
            } finally {
                d.recycle();
                r.recycle();
            }
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
