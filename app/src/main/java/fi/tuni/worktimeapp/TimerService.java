package fi.tuni.worktimeapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

/**
 * @author      Joni Alanko <joni.alanko@tuni.fi>
 * @version     20190422
 * @since       1.8
 *
 * Main service class that handles timer functionality.
 */
public class TimerService extends Service {

    private boolean run;
    private long startTime;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        LocalBroadcastManager.getInstance(this).registerReceiver(new myBroadcastReceiver(), new IntentFilter("PAUSE"));
        run = true;

        startTime = System.currentTimeMillis();
        new Thread(this::timer).start();

        return START_STICKY;
    }

    /**
     * Calculates time and broadcasts it every second.
     */
    private void timer() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent("Broadcast");
        while (run) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (run) {
                long runTime = (System.currentTimeMillis() - startTime) / 1000;
                intent.putExtra("time", runTime);
            }

            manager.sendBroadcast(intent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        run = false;
    }

    /**
     * Handles broadcasts coming to TimerService. Placeholder.
     */
    private class myBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra("pause", true)) {

            } else {

            }
        }
    }
}
