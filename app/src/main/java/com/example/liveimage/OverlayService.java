package com.example.liveimage;

import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;

public class OverlayService extends Service {
    private WindowManager wm;
    private ImageView view;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        removeOverlay();
        String path = intent.getStringExtra("path");
        if (path == null) { stopSelf(); return START_NOT_STICKY; }
        float nx = intent.getFloatExtra("x", 0.1f);
        float ny = intent.getFloatExtra("y", 0.1f);
        int dp = intent.getIntExtra("size", 120);
        view = new ImageView(this);
        view.setImageBitmap(BitmapFactory.decodeFile(path));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int px = (int)(dp * getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(px, px,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = Math.max(0, (int)(nx * getResources().getDisplayMetrics().widthPixels));
        p.y = Math.max(0, (int)(ny * getResources().getDisplayMetrics().heightPixels));
        try { wm.addView(view, p); } catch (Exception e) { stopSelf(); }
        return START_NOT_STICKY;
    }
    private void removeOverlay() {
        if (wm != null && view != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
            view = null;
        }
    }
    @Override public void onDestroy() { removeOverlay(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}