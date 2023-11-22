/**
 * ConnectionManager is the class that manages all wifi connection and information
 *
 * @author      Cédric Morel Francoz
 * @since       1.0
 */

package com.lynx.lynxandroidsystemcom;



import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.WIFI_SERVICE;


public class ConnectionManager {

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 123;
    private Context context;
    private Activity activity;
    private static final String WPA = "WPA";
    private static final String WEP = "WEP";
    private static final String OPEN = "Open";
    private final static String TAG = "WiFiConnector";

    public ConnectionManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    public void enableWifi() {

        /*
        Log.d("LynxAndroidSystem", "enableWifi() called");

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {

            wifiManager.setWifiEnabled(true);

            Log.d("LynxAndroidSystem", "Wifi Turned On");
            //new ShowToast(context, "Wifi Turned On");
        }
        */

        // Cédric : be careful with Android Q (10)

        //Android 10 (Q) onwards wifi can not be enabled/disabled you need to open the setting intent,
        Log.d("LynxAndroidSystem", "enableWifi() called with StartIntent : ");

        // for android Q and above
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        //{

            Intent panelIntent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            activity.startActivityForResult(panelIntent, 0);


        /*
        } else
         {
            // for previous android version
            WifiManager wifiManager = (WifiManager)
            context.getSystemService(WIFI_SERVICE);
            wifiManager.setWifiEnabled(true);
         }
        */

    }

    public int requestWIFIConnection(String networkSSID, String networkPass) {

        Log.d("LynxAndroidSystem","requestWIFIConnection 1 called with : " + networkSSID);

        try {
            Log.d("LynxAndroidSystem","requestWIFIConnection 2 called with : " + networkSSID);

            WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);

            /*
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Log.d("LynxAndroidSystem","before request permissions : ");


                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
                //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method

            }else{

                Log.d("LynxAndroidSystem","do something, permission was previously granted; or legacy device");

                scanWifi(wifiManager, networkSSID);
                //do something, permission was previously granted; or legacy device
            }
            */

            //Check ssid exists
            if (scanWifi(wifiManager, networkSSID))
            {
                // cedrock force return now :
                Log.d("LynxAndroidSystem","Scan wifi has return true so SSID has been found");

                // cedric
                if (getCurrentSSID(wifiManager) != null && getCurrentSSID(wifiManager).equals("\"" + networkSSID + "\""))
                {
                    //new ShowToast(context, "Already Connected With " + networkSSID);
                    Log.d("LynxAndroidSystem","Already Connected With " + networkSSID);
                    return 100;//SyncStateContract.Constants.ALREADY_CONNECTED;
                }

                //Security type detection
                String SECURE_TYPE = checkSecurity(wifiManager, networkSSID);
                if (SECURE_TYPE == null)
                {
                    Log.d("LynxAndroidSystem","Unable to find Security type for " + networkSSID);
                    //new ShowToast(context, "Unable to find Security type for " + networkSSID);
                    return 200;//SyncStateContract.Constants.UNABLE_TO_FIND_SECURITY_TYPE;
                }

                if (SECURE_TYPE.equals(WPA)) {
                    Log.d("LynxAndroidSystem","SECURE_TYPE.equals(WPA)");
                    WPA(networkSSID, networkPass, wifiManager);
                } else if (SECURE_TYPE.equals(WEP)) {
                    Log.d("LynxAndroidSystem","SECURE_TYPE.equals(WEP)");
                    WEP(networkSSID, networkPass);
                } else {
                    Log.d("LynxAndroidSystem","OPEN");
                    OPEN(wifiManager, networkSSID);
                }
                return 300;//SyncStateContract.Constants.CONNECTION_REQUESTED;
            }// cedric

            /*connectME();*/
            } catch (Exception e) {
                Log.d("LynxAndroidSystem","Error Connecting WIFI " + e);
                //new ShowToast(context, "Error Connecting WIFI " + e);
            }

        return 500;//SyncStateContract.Constants.SSID_NOT_FOUND;
    }

    private void WPA(String networkSSID, String networkPass, WifiManager wifiManager)
    {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + networkSSID + "\"";
        wc.preSharedKey = "\"" + networkPass + "\"";
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

        int id = wifiManager.addNetwork(wc);
        wifiManager.disconnect();

        Log.d("LynxAndroidSystem","wifiManager.enableNetwork() in WPA ");

        wifiManager.enableNetwork(id, true);// cedric : change le 27/10/2020 to false :
        wifiManager.reconnect();

    }

    private void WEP(String networkSSID, String networkPass) {
    }

    private void OPEN(WifiManager wifiManager, String networkSSID) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + networkSSID + "\"";
        wc.hiddenSSID = true;
        wc.priority = 0xBADBAD;
        wc.status = WifiConfiguration.Status.ENABLED;
        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        int id = wifiManager.addNetwork(wc);
        wifiManager.disconnect();

        Log.d("LynxAndroidSystem","wifiManager.enableNetwork() in OPEN ");

        wifiManager.enableNetwork(id, true);// cedric : change le 27/10/2020 to false :
        wifiManager.reconnect();
    }

    boolean scanWifi(WifiManager wifiManager, String networkSSID) {

        Log.i("LynxAndroidSystem", "scanWifi called");

        List<ScanResult> scanList = wifiManager.getScanResults();
        boolean found = false;

        for (ScanResult i : scanList)
        {
            if (i.SSID != null) {
                Log.i("LynxAndroidSystem", "SSID: " + i.SSID);
            }

            if (i.SSID != null && i.SSID.equals(networkSSID)) {
                Log.i(TAG, "Found SSID: " + i.SSID);
                found = true;
                //return true;
            }
        }

        if (found) return true;

        Log.d("LynxAndroidSystem","SSID " + networkSSID + " Not Found");
        //new ShowToast(context, "SSID " + networkSSID + " Not Found");
        return false;
    }

    List<String> getWifiSSIDList() {

        Log.i("LynxAndroidSystem", "get Wifi SSID List called");

        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);

        List<ScanResult> scanList = wifiManager.getScanResults();

        List<String> SSIDList = new ArrayList<String>();

        for (ScanResult i : scanList)
        {
            if (i.SSID != null)
            {
                SSIDList.add(i.SSID);
            }
        }

        return SSIDList;
    }

    List<WifiData> getWifiDataList() {

        Log.i("LynxAndroidSystem", "getWifiDataList called");

        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);

        List<ScanResult> scanList = wifiManager.getScanResults();

        List<WifiData> wifiDataList = new ArrayList<WifiData>();

        for (ScanResult i : scanList)
        {
            //Log.i("LynxAndroidSystem","----------- in Scanresult : ");

            if (i.SSID != null) {
                //Log.i("LynxAndroidSystem", "wifi SSID: " + i.SSID);
                //Log.i("LynxAndroidSystem", "wifi level: " + i.level);
                //Log.i("LynxAndroidSystem", "wifi capabilities: " + i.capabilities);
                //Log.i("LynxAndroidSystem", " ");

                WifiData wifiData = new WifiData();

                wifiData.ssid = i.SSID;
                wifiData.level = i.level;

                if (i.capabilities.contains("WPA2")) {
                    wifiData.security = 1;
                }else{
                    wifiData.security = 0;
                }

                wifiDataList.add(wifiData);
            }
        }

        return wifiDataList;
    }





    public String GetCurrentSSID() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        return getCurrentSSID(wifiManager);
    }

    public String getCurrentSSID(WifiManager wifiManager) {
        String ssid = null;
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (networkInfo.isConnected())
        {
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();

            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {

                ssid = connectionInfo.getSSID();

                // Level of current connection
                int rssi = wifiManager.getConnectionInfo().getRssi();
                int level = WifiManager.calculateSignalLevel(rssi, 5);
                //System.out.println("Level is " + level + " out of 5");

                //Log.i("LynxAndroidSystem", "Level is " + level + " out of 5");

            }
        }
        return ssid;
    }


    public WifiData getCurrentWifiData()
    {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        final WifiInfo connectionInfo = wifiManager.getConnectionInfo();

        if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {

            String ssid = connectionInfo.getSSID();

            // Level of current connection
            int rssi  = connectionInfo.getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 5);

            //Log.i("LynxAndroidSystem", "Level is " + level + " out of 5");
            // cedrock again
            WifiData wifiData = new WifiData();

            wifiData.ssid = ssid;
            wifiData.level = level; // level here is between 0 and 4. not in dB.
            wifiData.security = 0; // dummy;

            return wifiData;
        }

        return null;
    }









    private String checkSecurity(WifiManager wifiManager, String ssid) {
        List<ScanResult> networkList = wifiManager.getScanResults();
        for (ScanResult network : networkList) {
            if (network.SSID.equals(ssid)) {
                String Capabilities = network.capabilities;
                if (Capabilities.contains("WPA")) {
                    return WPA;
                } else if (Capabilities.contains("WEP")) {
                    return WEP;
                } else {
                    return OPEN;
                }

            }
        }
        return null;
    }




}