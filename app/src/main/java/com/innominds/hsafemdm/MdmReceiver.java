package com.innominds.hsafemdm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class MdmReceiver extends BroadcastReceiver {
    public static final String TAG = "test-dps-receiver";
    final String boot_completed = "android.intent.action.BOOT_COMPLETED";

    public MdmReceiver(Context ctx) { super(); }

    public MdmReceiver() {
        super();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Intent action received=" + action);
        switch (action) {
            case boot_completed:
                Log.d(TAG, "Version=22.2");
                Log.d(TAG, "Boot completed!!");
                Log.d(TAG,"Triggering DPS service");
                Intent dps_service = new Intent(context, DPSService.class);
                context.startForegroundService(dps_service);
                break;
        }
    }
}
