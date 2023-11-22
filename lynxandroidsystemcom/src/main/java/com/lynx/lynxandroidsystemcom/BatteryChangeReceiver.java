/**
 * BatteryChangeReceiver is the class that gives information about battery level
 * and if the device is charging or not.
 *
 * @author      Cédric Morel Francoz
 * @since       1.0
 */


package com.lynx.lynxandroidsystemcom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

public class BatteryChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent)
    {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float battPct = (float)level/(float)scale * 100.0f;
        int   iBattPercent = (int) battPct;
        //Log.d("LynxAndroidSystem", "--------- battery level change with battPct : " + iBattPercent);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;

        int isChargingFlag = 1000;

        int valueToSendToUnity=0;
        if (isCharging)
        {
            valueToSendToUnity=iBattPercent + isChargingFlag;
        }
        else
        {
            valueToSendToUnity=iBattPercent;
        }

        //Log.d("LynxAndroidSystem", "--------- isCharging : " + isCharging);

        UnityPlayer.UnitySendMessage(LynxAndroidSystemComMng.mUnityGameObjectForCallback, "BatteryLevelChange", Integer.toString(valueToSendToUnity));
    }
}





