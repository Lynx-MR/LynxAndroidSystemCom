/**
 * NetworkChangeReceiver gives information about network state (Wifi)
 *
 * @author      Cédric Morel Francoz
 * @since       1.0
 */


package com.lynx.lynxandroidsystemcom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

public class NetworkChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent)
    {
        Log.i("LynxAndroidSystem", "NetworkChangeReceiver onReceive called with ");

        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        //Log.i("LynxAndroidSystem", "With detailed state : " + info.getDetailedState().toString());

        // callback send to Unity :
        // Important :
        //UnityPlayer.UnitySendMessage(LynxAndroidSystemComMng.mUnityGameObjectForCallback, "BatteryLevelChange", Integer.toString(valueToSendToUnity));
    }
}





