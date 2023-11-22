/**
 * LynxAndroidSystemComMng is the class that gives a maximum of information
 * about the android system that is installed on a lynx headset.
 * It contains a set of functions that give for example the wifi and the bluetooth state
 * , the current level of battery, the current level of audio volume, the number of application
 * installed on the device etc...
 * Note that you can obtain information but you can act on the system too :
 * You can enable or not bluetooth and wifi, change the current volume, delete an installed application etc..
 *
 * Main of the member functions of this class are static functions callable easily from a high level application
 * like our Unity launcher from c sharp code.
 * @author      Cédric Morel Francoz
 * @since       1.0
 */

package com.lynx.lynxandroidsystemcom;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;


import com.unity3d.player.UnityPlayer;

import java.io.BufferedReader;
import 	java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import static android.content.Context.BATTERY_SERVICE;
import static android.hardware.Sensor.TYPE_PROXIMITY;

public class LynxAndroidSystemComMng {

    /** This library .jar version  */
    static String mLibVersion = "1.0.0"; // 0.5 : - add TimeZone features.
                                       //       - add lynx system version.
                                       //       - Remove some logs.
                                       // 0.5.5 : possibility to remove audio volume listener -> UnregisterVolumeChangeReceiver and registerVolumeChangeReceiver.
                                       // 0.5.6 : Add Ultraleap disable analytics part.
                                       // 1.0.0 : big cleaning phase before publishing all this code.

    // Broadcast receiver part :
    /** The package changes receiver, this one manage package added, deleted etc. */
    static BroadcastReceiver       mPackageChangeReceiver = null;

    /** the Settings Content Observer manage all change relative to Audio volume
     * @see SettingsContentObserver
     */
    static SettingsContentObserver mSettingsContentObserver = null;

    /** Informs if the settings content receiver id registered or not */
    static boolean                 mSettingsContentReceiverRegistered = false;

    /** the Battery Change receiver is able to give the battery level and know if it is charging or not.
     * @see BatteryChangeReceiver
     */
    static BatteryChangeReceiver   mBatteryChangeReceiver   = null;

    /** NetworkChangeReceiver
     * @see NetworkChangeReceiver
     */
    static NetworkChangeReceiver   mNetworkChangeReceiver   = null;

    // Bluetooth part :
    static Set<String> mBTDeviceNameSet = new HashSet<String>();
    static ArrayList<BluetoothData> mBluetoothDataForAvailableDevice  =  new ArrayList<BluetoothData>();
    static ArrayList<BluetoothDevice> mBluetoothDeviceList =  new ArrayList<BluetoothDevice>();
    static BluetoothDevice mDeviceToBePaired = null;
    static BluetoothDevice mDeviceToBeUnPaired = null;
    static BroadcastReceiver mBluetoothBroadcastReceiver = null;

    /** The unity game object name to use in UnityPlayer.UnitySendMessage functions */
    static String mUnityGameObjectForCallback = null;

    // To get information about an app installed on device :
    static Set<String> mMainAndLauncherAppSet = new HashSet<String>();
    static Set<String> mSVRAppSet             = new HashSet<String>();

    // Time on device part :
    static Map<String, String> mTimeZonesMap = new TreeMap();
    static ArrayList<String>   mTimeZonesReadableList = new ArrayList<String>();

    /** The package name of the virtual display application. This one is launch from the lynx launcher on a 2 classical application not an OpenXR one*/
    static String mLynxVirtualDisplayPackageName = "com.lynx.virtualdisplay";

    // Ultraleap analytics part :
    static Uri mUltraleapTrackingServiceUri = Uri.parse("content://com.ultraleap.tracking.service/settings");

    /**
     * Returns the version of this android library.
     *
     * @return the version of this android library
     */
    public static String getVersion()
    {
        return mLibVersion;
    }

    /**
     * Set to this library the name of the unity object to call during a SendMessage invocation.
     * To call Unity, we use the API  : Player.UnitySendMessage("GameObjectName", "Function", "Param");
     * setUnityGameObjectName fix the GameObjectName that contains a c# script which contains the function to call.
     *
     * @param  name of the unity object in the hierarchy of the scene that receive messages from the library
     */
    public static void setUnityGameObjectName(String name)
    {
        mUnityGameObjectForCallback = name;
    }


    /**
     * Register the broadcast receiver about package changes
     * Register Audio Volume of the device Observer
     * Register Battery changes receiver
     * Register Network changes receiver
     * and defines all the time zone data
     * <p>
     * This method is called at the beginning of the calling application
     * to registers all this important observers
     *
     * @param  currentActivity  the android activity of the calling application
     * @param  context          the android context of the calling application
     */
    public static void registerChangesReceivers(Activity currentActivity,Context context)
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_FULLY_REMOVED");

        filter.addDataScheme("package");

        mPackageChangeReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("LynxAndroidSystem", "--------- Package Change Receiver onReceive called");

                Uri data = intent.getData();

                Log.d("LynxAndroidSystem", "Action: " + intent.getAction());
                Log.d("LynxAndroidSystem", "DATA: " + data);

                if (intent.getAction() == "android.intent.action.PACKAGE_ADDED")
                {
                    Log.d("LynxAndroidSystem", "--------- android.intent.action.PACKAGE_ADDED received");
                    UnityPlayer.UnitySendMessage(LynxAndroidSystemComMng.mUnityGameObjectForCallback, "AndroidNewPackageInstalled", data.toString());
                }

                if (intent.getAction() == "android.intent.action.PACKAGE_FULLY_REMOVED")
                {
                    Log.d("LynxAndroidSystem", "--------- android.intent.action.PACKAGE_FULLY_REMOVED called so say it to Unity");
                    UnityPlayer.UnitySendMessage(LynxAndroidSystemComMng.mUnityGameObjectForCallback, "AndroidPackageRemoved", data.toString());
                }
            }
        };

        context.registerReceiver(mPackageChangeReceiver, filter);

        if (!mSettingsContentReceiverRegistered) {
            Log.d("LynxAndroidSystem", "--------- Register Volume Observer");
            SettingsContentObserver settingsContentObserverObj = new SettingsContentObserver(context, new Handler());
            mSettingsContentObserver = settingsContentObserverObj;
            context.getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, settingsContentObserverObj);
            mSettingsContentReceiverRegistered = true;
        }

        // Create the battery change receiver :
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mBatteryChangeReceiver = new BatteryChangeReceiver();
        context.registerReceiver(mBatteryChangeReceiver, batteryLevelFilter);

        // Create the network change receiver :
        IntentFilter networkChangeFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mNetworkChangeReceiver = new NetworkChangeReceiver();
        context.registerReceiver(mNetworkChangeReceiver, networkChangeFilter);

        mTimeZonesMap.put("00- GMT+0 Europe/London","Europe/London" );
        mTimeZonesMap.put("01- GMT+1 Europe/Paris","Europe/Paris");
        mTimeZonesMap.put("02- GMT+2 Europe/Moscow","Europe/Moscow");
        mTimeZonesMap.put("03- GMT+3 Asia/Dubai","Asia/Dubai");
        mTimeZonesMap.put("04- GMT+4 Asia/Karachi","Asia/Karachi");
        mTimeZonesMap.put("05- GMT+5 Asia/Omsk","Asia/Omsk");
        mTimeZonesMap.put("06- GMT+6 Asia/Novosibirsk","Asia/Novosibirsk");
        mTimeZonesMap.put("07- GMT+7 Asia/Hong Kong","Asia/Hong_Kong");
        mTimeZonesMap.put("08- GMT+8 Asia/Tokyo","Asia/Tokyo");
        mTimeZonesMap.put("09- GMT+9 Australia/Sydney","Australia/Sydney");
        mTimeZonesMap.put("10- GMT+10 Asia/Sakhalin","Asia/Sakhalin");
        mTimeZonesMap.put("11- GMT+11 Pacific/Auckland","Pacific/Auckland");
        mTimeZonesMap.put("12- GMT+12 Pacific/Apia","Pacific/Apia");

        mTimeZonesMap.put("13- GMT-1 Atlantic/Reykjavik","Atlantic/Reykjavik");
        mTimeZonesMap.put("14- GMT-2 Atlantic/Azores","Atlantic/Azores");
        mTimeZonesMap.put("15- GMT-3 America/Nuuk","America/Nuuk");
        mTimeZonesMap.put("16- GMT-4 Atlantic/South Georgia","Atlantic/South_Georgia");
        mTimeZonesMap.put("17- GMT-5 America/New York","America/New_York");
        mTimeZonesMap.put("18- GMT-6 America/Bogota","America/Bogota");
        mTimeZonesMap.put("19- GMT-7 America/Denver","America/Denver");
        mTimeZonesMap.put("20- GMT-8 America/Los Angeles","America/Los_Angeles");
        mTimeZonesMap.put("21- GMT-9 America/Anchorage","America/Anchorage");
        mTimeZonesMap.put("22- GMT-10 Pacific/Gambier","Pacific/Gambier");
        mTimeZonesMap.put("23- GMT-11 Pacific/Honolulu","Pacific/Honolulu");
        mTimeZonesMap.put("24- GMT-12 Pacific/Niue","Pacific/Niue");

        for (Map.Entry mapentry : mTimeZonesMap.entrySet())
        {
            /*
            System.out.println("key : "+mapentry.getKey()
                    + " | value : " + mapentry.getValue());
            */

            mTimeZonesReadableList.add(mapentry.getKey().toString());
        }

        // new :
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        Log.d("mSensorManager : " , mSensorManager.toString());

        mAccelerometer = mSensorManager.getDefaultSensor(TYPE_PROXIMITY /*Sensor.TYPE_ACCELEROMETER*/);

        Log.d("mAccelerometer : " , mAccelerometer.toString());

        ProximitySensorMng proximitySensorMng = new ProximitySensorMng();

        mSensorManager.registerListener((SensorEventListener)proximitySensorMng, mAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);

    }


    /**
     * Get a representative icon of a given application installed on an android device.
     * thanks the application info object.
     *
     * @param  pm              the android package manager
     * @param  applicationInfo an android object that gives information about an application.
     *
     * * @return a byte array that represent the icon of the application. it's compressed to a PNG image.
     */
    public static byte[] getIcon(PackageManager pm, ApplicationInfo applicationInfo) {
        try {
            BitmapDrawable icon = (BitmapDrawable) pm.getApplicationIcon(applicationInfo);
            Bitmap bmp = icon.getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            return byteArray;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a representative Icon of a give application installed on an android device.
     * thanks the package name of the application (for exemple : com.lynx.MyAplication)
     *
     * @param  pm          the android package manager
     * @param  PackageName the package name of the application
     *
     * * @return a byte array that represent the icon of the application. it's compressed to a PNG image.
     */
    public static byte[] GetIcon(PackageManager pm, String PackageName)
    {
        try {
            PackageInfo packageInfo=pm.getPackageInfo(PackageName,PackageManager.GET_META_DATA);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            BitmapDrawable icon = (BitmapDrawable) pm.getApplicationIcon(applicationInfo);
            Bitmap bmp = icon.getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            return byteArray;
        } catch (Exception e) {
            Log.e("LynxAndroidSystem", "--------- Error in GetIcon with error :");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Say if an application is an android system app or not
     *
     * @param  applicationInfo the package name of the application
     * @return a boolean
     */
    public static boolean isSystem(ApplicationInfo applicationInfo){
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM ) != 0;
    }

    /**
     * Get the date of the last update of an application
     *
     * @param  packageInfo an android object that gives info of a android package
     * @return the date of the last update (a long)
     */
    public static long getLastUpdateDate(PackageInfo packageInfo){
        return packageInfo.lastUpdateTime;
    }

    /**
     * Delete a package on a android device.
     *
     * @param  currentActivity the android activity of the calling application
     * @param  packageName     the package name of the application (for exemple : com.lynx.MyAplication)
     */
    public static void deletePackage(Activity currentActivity, String packageName)
    {
        String uriString = "package:"+packageName;
        //Uri packageURI = Uri.parse("package:com.DefaultCompany.LeapMotionTestOK");
        Uri packageURI = Uri.parse(uriString);
        Log.i("LynxAndroidSystem", packageURI.toString());

        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);

        //uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        currentActivity.startActivity(uninstallIntent);
    }

    /**
     * Get the current battery percentage
     *
     * @param  context the android activity of the calling application
     * @return the battery percentage : an integer between 0 and 100
     */
    public static int getBatteryPercentage(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * Inform if Battery is charging or not
     *
     * @param  context the android activity of the calling application
     * @return 0 if the battery is not charging , 1 if yes
     */
    public static int isBatteryCharging(Context context) {

        BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);

        if (bm.isCharging())
        {
            Log.i("LynxAndroidSystem", "**** Battery is Charging");
           return 1;
        }

        Log.i("LynxAndroidSystem", "**** Battery is NOT Charging");
        return 0;

    }

    /**
     * Get Network Info
     *
     * @param  context the android activity of the calling application
     * @return 0 if the battery is not charging , 1 if yes
     */
    public static int getNetworkInfo(Context context)
    {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        boolean isWifiConn = false;
        boolean isMobileConn = false;
        int ret = 0;

        for (Network network : connMgr.getAllNetworks())
        {
            NetworkInfo networkInfo = connMgr.getNetworkInfo(network);

            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                isWifiConn |= networkInfo.isConnected();
                ret = ret + 1;
            }
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                isMobileConn |= networkInfo.isConnected();
                ret = ret + 2;
            }
        }

        return ret;
    }

    /**
     * Get Bluetooth Info
     *
     * @return int
     */
    public static int getBluetoothInfo()
    {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return -1;
        } else if (!bluetoothAdapter.isEnabled()) {
            return 0;
        } else {
            return 1;
        }
    }

    /**
     * enable Blue tooth
     *
     * @param  enable or not
     */
    public static void enableBluetooth(boolean enable)
    {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isEnabled = bluetoothAdapter.isEnabled();
        if (enable && !isEnabled) {
            bluetoothAdapter.enable();
        }
        else if(!enable && isEnabled) {
            bluetoothAdapter.disable();
        }
    }

    /**
     * get Device Name
     *
     * @return String
     */
    public static String getDeviceName() {

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        String name = bluetoothAdapter.getName();

        Log.i("LynxAndroidSystem", "getDeviceName called and name is : " + name );

        /*
        if(name == null){
            Log.i("LynxAndroidSystem", "bluetooth device name is NULL" );
            name = bluetoothAdapter.getAddress();
        }
        */

        return name;
    }

    /**
     * set Device Name
     *
     * @param  name
     */
    public static void setDeviceName(String name)
    {
        if (name!=null)
        {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if(bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON){
                bluetoothAdapter.setName(name);
            }

            Log.i("LynxAndroidSystem", "setDeviceName called with name :" + name);
        }
    }

    /**
     * get Bluetooth Paired Devices
     *
     * @return List<BluetoothData>
     */
    public static List<BluetoothData> getBluetoothPairedDevices()
    {
        Log.i("LynxAndroidSystem", "getBluetoothPairedDevices() called : " );

        BluetoothAdapter     bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices    = bluetoothAdapter.getBondedDevices();

        List<BluetoothData> bluetoothDatas = new ArrayList<BluetoothData>();

        for(BluetoothDevice bt : pairedDevices) {

            Log.i("LynxAndroidSystem", "Bluetooth name : " + bt.getName());

            BluetoothClass btClass = bt.getBluetoothClass();
            int iDeviceType = getBluetoothDeviceTypeCode(btClass.getMajorDeviceClass());

            BluetoothData bluetoothData = new BluetoothData();

            bluetoothData.name = bt.getName();
            bluetoothData.type = iDeviceType;

            bluetoothDatas.add(bluetoothData);
        }

        return bluetoothDatas;
    }

    /**
     * launchBluetoothSurroundingDevicesSearch
     *
     * @param  context          the android context of the calling application
     */
    public static void launchBluetoothSurroundingDevicesSearch(Context context)
    {
        Log.i("LynxAndroidSystem", "launchBluetoothSurroundingDevicesSearch() called : " );

        mBTDeviceNameSet.clear();
        mBluetoothDataForAvailableDevice.clear();
        mBluetoothDeviceList.clear();

        mBluetoothBroadcastReceiver = null;

        mBluetoothBroadcastReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                //ArrayList<String> mDeviceList = new ArrayList<String>();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (device.getName() !=null)
                    { // cedric : register only device with non null name.

                        mBluetoothDeviceList.add(device);

                        boolean bTest = mBTDeviceNameSet.add(device.getName()); // keep only name.

                        if (bTest)
                        {
                            Log.i("LynxAndroidSystem", "BT device detecte with name : " + device.getName());

                            // create a bluetooth data record :
                            BluetoothData bluetoothData = new BluetoothData();

                            bluetoothData.name     = device.getName();
                            BluetoothClass btClass = device.getBluetoothClass();
                            int iDeviceType = getBluetoothDeviceTypeCode(btClass.getMajorDeviceClass());
                            bluetoothData.type = iDeviceType;

                            mBluetoothDataForAvailableDevice.add(bluetoothData);
                        }


                    }
                }
            }
        };

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.startDiscovery();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mBluetoothBroadcastReceiver, filter);
    }

    /**
     * getBluetoothSurroundingDevices
     *
     * @param  context          the android context of the calling application
     * @return List<BluetoothData>
     */
    public static List<BluetoothData> getBluetoothSurroundingDevices(Context context)
    {
        Log.i("LynxAndroidSystem", "getBluetoothSurroundingDevices() called : " );

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.cancelDiscovery();

        if (mBluetoothBroadcastReceiver != null)
        {
            context.unregisterReceiver(mBluetoothBroadcastReceiver);
            mBluetoothBroadcastReceiver = null;
        }
        else
        {
            Log.i("LynxAndroidSystem", "mBluetoothBroadcastReceiver Null : " );
        }

        return mBluetoothDataForAvailableDevice;

    }

    /**
     * pairDevice
     *
     * @param  deviceName
     * @param  context          the android context of the calling application=
     */
    public static void pairDevice(String deviceName, Context context)
    {
        BluetoothDevice device = null;
        Boolean bFound  = false;

        Log.i("LynxAndroidSystem", "In pairDevice with device name : " + deviceName);

        for(BluetoothDevice bt : mBluetoothDeviceList)
        {
            if ( deviceName.equals(bt.getName()) )
            {
                Log.i("LynxAndroidSystem", "Bluetoothdevice found : " + bt.getName());
                device = bt;
                bFound = true;
            }
        }


        if (bFound)
        {
            // new
            mBluetoothBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                        mDeviceToBePaired = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (mDeviceToBePaired.getBondState() == BluetoothDevice.BOND_BONDED) {
                            //means device paired
                            Log.d("LynxAndroidSystem", "bonded");
                            deviceBonded(context);
                        }
                        else if(mDeviceToBePaired.getBondState() == BluetoothDevice.BOND_BONDING) {
                            Log.d("LynxAndroidSystem", "bonding");
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            context.registerReceiver(mBluetoothBroadcastReceiver, filter);

            pairDevice(device);
        }
        else
        {
            Log.i("LynxAndroidSystem", "Bluetoothdevice NOT found, impossible to pair it");
        }
    }

    /**
     * unpairDevice
     *
     * @param  deviceName
     * @param  context          the android context of the calling application=
     */
    public static void unpairDevice(String deviceName, Context context)
    {
        BluetoothAdapter     bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices    = bluetoothAdapter.getBondedDevices();

        BluetoothDevice device = null;
        Boolean         bFound = false;

        for(BluetoothDevice bt : pairedDevices)
        {
            Log.i("LynxAndroidSystem", "bt.getName() in the loop : " + bt.getName());

            if ( deviceName.equals(bt.getName()))
            {
                Log.i("LynxAndroidSystem", "Bluetoothdevice found : " + bt.getName());
                device = bt;
                bFound = true;
            }
        }

        if (bFound)
        {
            mBluetoothBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    String action = intent.getAction();

                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action))
                    {
                        mDeviceToBeUnPaired = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                        if (mDeviceToBeUnPaired.getBondState() == BluetoothDevice.BOND_NONE) {
                            //means device paired
                            Log.d("LynxAndroidSystem", "bonded");
                            deviceUnbonded(context);
                        }

                    }
                }
            };

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            context.registerReceiver(mBluetoothBroadcastReceiver, filter);

            unpairDevice(device);
        }
        else
        {
            Log.i("LynxAndroidSystem", "Bluetoothdevice NOT found, impossible to unpair it");
        }
    }

    /**
     * pairDevice
     *
     * @param  device
     */
    private static void pairDevice(BluetoothDevice device) {

        try {
            Log.i("LynxAndroidSystem", "in pairDevice(BluetoothDevice device)");

            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * deviceBonded
     *
     * @param  context the android context of the calling application
     */
    private static void deviceBonded(Context context) {

        try {
            if (mBluetoothBroadcastReceiver !=null) {
                context.unregisterReceiver(mBluetoothBroadcastReceiver);
                mBluetoothBroadcastReceiver = null;
            }

            UnityPlayer.UnitySendMessage(mUnityGameObjectForCallback, "NewBluetoophDeviceBonded", mDeviceToBePaired.getName());

            Log.i("LynxAndroidSystem", "new deviceBonded called");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * deviceUnbonded
     *
     * @param  context the android context of the calling application
     */
    private static void deviceUnbonded(Context context) {

        try {
            if (mBluetoothBroadcastReceiver !=null) {
                context.unregisterReceiver(mBluetoothBroadcastReceiver);
                mBluetoothBroadcastReceiver = null;
            }

            Log.i("LynxAndroidSystem", "new deviceUnBonded called");

            UnityPlayer.UnitySendMessage(mUnityGameObjectForCallback, "BluetoophDeviceUnbonded", mDeviceToBeUnPaired.getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * unpairDevice
     *
     * @param  device
     */
    private static void unpairDevice(BluetoothDevice device)
    {
        try {
            Log.i("LynxAndroidSystem", "in unpairDevice(BluetoothDevice device)");

            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * BroadcastReceiver
     *
     */
    private final BroadcastReceiver mPairReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state 		= intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState	= intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    //showToast("Paired");
                    Log.i("LynxAndroidSystem", "Paired " );
                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    //showToast("Unpaired");
                    Log.i("LynxAndroidSystem", "UnPaired " );
                }

                //mAdapter.notifyDataSetChanged();
            }
        }
    };

    /**
     * Enable or disable Wifi Network
     * it's important to notice that the calling application needs to be a system app
     * for that this function works.
     * @param  context the android context of the calling application
     * @param  enable  enable or not wifi
     */
    public static void enableWifi(Context context, boolean enable)
    {
        Log.i("LynxAndroidSystem", "enableWifi :" + enable);

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(enable);
    }

    /**
     * launchApplication

     * @param  pm
     * @param  currentActivity
     * @param  packageName
     */
    public static void launchApplication(PackageManager pm, Activity currentActivity, String packageName)
    {
        Log.i("LynxAndroidSystem", "launchApplication : " + packageName);

        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);

        Log.i("LynxAndroidSystem", "launchIntent == null");
        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("market://details?id=" + "tv.arte.plus7"));
            currentActivity.startActivity(intent);
        }else{
            Log.i("LynxAndroidSystem", "new Intent == null");
        }
    }

    /**
     * launchAppInVirtualDisplay

     * @param  context          the android context of the calling application
     * @param  packageName
     */
    public static void launchAppInVirtualDisplay(Context context,String packageName)
    {
        Log.i("LynxAndroidSystem", "launchVirtualDisplay called");

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(mLynxVirtualDisplayPackageName);

        if (launchIntent != null)
        {
            Log.i("LynxAndroidSystem", "launchIntent != null"); // cedric : normally, we pass here.
            launchIntent.putExtra("packageNameVirtualDisplayKey", packageName);
            context.startActivity(launchIntent);
        }
        else
        {
            Log.i("LynxAndroidSystem", "launchIntent == null so create it : ");
            Intent intent = new Intent(Intent.ACTION_VIEW);

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                String packageNameAppToLaunch = "com.android.settings";
                intent.putExtra("packageNameToLaunch", packageNameAppToLaunch);
                context.startActivity(intent);
            }else{
                Log.i("LynxAndroidSystem", "new Intent == null");
            }
        }
    }

    /**
     * launchOnceOnBoarding

     * @param  context          the android context of the calling application
     * @param  OBPackageName
     */
    public static void launchOnceOnBoarding(Context context,String OBPackageName)
    {
        Log.i("LynxAndroidSystem", "launchOnceOnBoarding called");

        //"com.Lynx.TestOtherAppStart"

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(OBPackageName);

        if (launchIntent != null)
        {
            Log.i("LynxAndroidSystem", "launchIntent NON null"); // cedric : normally, we pass here.

            Bundle appBundle = launchIntent.getExtras();

            if (appBundle==null)
            {
                Log.i("LynxAndroidSystem", "app Bundle is NULL, launch the Lynx OnBoarding application :");
                context.startActivity(launchIntent);
                return;
            }

            if (appBundle.isEmpty())
            {
                Log.i("LynxAndroidSystem", "app Bundle is empty, launch the Lynx OnBoarding application :");
                context.startActivity(launchIntent);
                return;
            }
            else
            {
                int value = appBundle.getInt("number_of_launch",0);

                if (value == 0)
                {
                    Log.i("LynxAndroidSystem", "Bundle of OnBoarding exists BUT number_of_launch = 0, launch the application :");
                    context.startActivity(launchIntent);
                }
                else {
                    Log.i("LynxAndroidSystem", "Bundle of OnBoarding exists AND number_of_launch != 0, Don't launch the Lynx OnBoarding application :");
                }
            }
        }
        else
        {

            Log.i("LynxAndroidSystem", "not ok launchIntent null so create it : ");

            Intent intent = new Intent(Intent.ACTION_VIEW);

            if (intent != null)
            {
                Log.i("LynxAndroidSystem", "intent != null after creation ");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //String packageNameAppToLaunch = "com.android.settings";
                //intent.putExtra("packageNameToLaunch", packageNameAppToLaunch);
                //context.startActivity(intent);
            }else
            {
                Log.i("LynxAndroidSystem", "new Intent == null");
            }
        }
    }

    /**
     * endOfOnBoarding
     *
     * @param  activity
     */
    public static void endOfOnBoarding(Activity activity)
    {
        Log.i("LynxAndroidSystem", "endOfOnBoarding called");

        if (activity == null) {
            Log.e("LynxAndroidSystem", "activity == null");
            return;
        }

        Intent AppIntent = activity.getIntent();

        if (AppIntent != null)
        {
            Log.i("LynxAndroidSystem", "AppIntent NON null, set to 1 launch counter :"); // cedric : normally, we pass here.
            int value = 1;
            AppIntent.putExtra("number_of_launch", value);
            Bundle bu = AppIntent.getExtras();

        }
        else
        {
            Log.e("LynxAndroidSystem", "error in endOfOnBoarding AppIntent is null");
        }
    }

    /*
    *   Audio Management :
    */

    /**
     * Set the audio volume of the headset. The audio channel affected is STREAM_MUSIC
     *
     * @param  context the android context of the calling application
     * @param  volume
     */
    public static void setAudioVolume(Context context, int volume)
    {
        if (volume<0 || volume>15)
        {
            Log.w("LynxAndroidSystem", "try to set an audio volume not between 0 to 15");
            return;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    /**
     * getAudioVolume
     *
     * @param  context the android context of the calling application
     * @return the device audio volume. an int between 0 and 15 included.
     */
    public static int getAudioVolume(Context context)
    {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * getMaxAudioVolume
     *
     * @param  context the android context of the calling application
     * @return the maximum of the audio volume for the device. Normally, it 's 15.
     */
    public static int getMaxAudioVolume(Context context)
    {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // Max volume is normally between 0 and 15.
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * setMicrophoneMute
     *
     * @param  context the android context of the calling application
     * @param  mute boolean for mute or not.
     */
    public static void setMicrophoneMute(Context context, boolean mute)
    {
        Log.i("LynxAndroidSystem", "setMicrophoneMute : " + mute);

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(mute);
    }

    /**
     * setMicrophoneMute
     *
     * @param  context the android context of the calling application
     * @return boolean if microphone is muted or not.
     */
    public static boolean isMicrophoneMute(Context context)
    {
        //Log.i("LynxAndroidSystem", "isMicrophoneMute called");

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isMicrophoneMute();
    }


    /**
     * Unregister Volume Change Receiver
     *
     * @param  context the android context of the calling application
     */
    public static void UnregisterVolumeChangeReceiver(Context context)
    {
        if (mSettingsContentReceiverRegistered ) {
            Log.d("LynxAndroidSystem", "--------- UnRegisterVolumeReceiver called");
            context.getContentResolver().unregisterContentObserver(mSettingsContentObserver);
            mSettingsContentReceiverRegistered = false;
        }
    }

    /**
     * Register Volume Change Receiver
     *
     * @param  context the android context of the calling application
     */
    public static void RegisterVolumeChangeReceiver(Context context)
    {
        if (!mSettingsContentReceiverRegistered ) {
            Log.d("LynxAndroidSystem", "--------- RegisterVolumeChangeReceiver called");
            context.getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mSettingsContentObserver);
            mSettingsContentReceiverRegistered = true;
        }
    }



    /*
     *
     *   end of Audio Management :
     */


    /*
     *
     *   Android version& device Management :
     */

    /**
     * getDeviceAndSystemInfo
     *
     * @return String
     */
    public static String getDeviceAndSystemInfo()
    {
        Log.d("LynxAndroidSystem","Model : "+ android.os.Build.MODEL +" brand = "+ android.os.Build.BRAND +" OS version = "+ android.os.Build.VERSION.RELEASE +" Base OS = "+ Build.VERSION.BASE_OS + " version incremental :"+ android.os.Build.VERSION.INCREMENTAL + " Device : "+ Build.DEVICE + " SDK version = " +android.os.Build.VERSION.SDK_INT + " Build.FINGERPRINT = " + Build.FINGERPRINT + " Build.DISPLAY = " + Build.DISPLAY);
        String info;
        info = android.os.Build.MODEL +"$"+Build.DISPLAY+"$"+android.os.Build.VERSION.SDK_INT+"$"+android.os.Build.VERSION.INCREMENTAL;
        return info;
    }

    /**
     * Return the lynx system version
     *
     * @return String
     */
    public static String getLynxAndroidSystemVersion()
    {
        // exemple of Build.FINGERPRINT :
        //Build.FINGERPRINT = Lynx-R/kona/kona:12/v1.1.2/eng.jmv.20230509.181908:userdebug/test-keys

        String ret = "Unknown version";
        String strBuildFingerPrint = Build.FINGERPRINT;

        Log.d("LynxAndroidSystem","Build.FINGERPRINT from which the android version number is  = " + Build.FINGERPRINT);

        String[] tabString = strBuildFingerPrint.split("/");

        if (tabString.length>0)
        {
            ret = tabString[3];
            Log.d("LynxAndroidSystem","Lynx Android System Version = " + tabString[3]);
        }
        else
        {
            Log.e("LynxAndroidSystem","***** Issue in getLynxAndroidSystemVersion()");
        }

        return ret;
    }

    /**
     * enableLocationPermission
     *
     * @param  context         the android context of the calling application
     * @param  currentActivity the android activity of the calling application
     */
    public static void enableLocationPermission(Context context, Activity currentActivity)
    {
        Log.d("LynxAndroidSystem", "enableLocationPermission called");

        Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        currentActivity.startActivity(myIntent);
    }

    /**
     * requestWIFIConnection
     *
     * @param  context         the android context of the calling application
     * @param  currentActivity the android activity of the calling application
     * @param  networkSSID
     * @param  networkPass
     */
    public static void requestWIFIConnection(Context context , Activity currentActivity, String networkSSID, String networkPass)
    {
        Log.d("LynxAndroidSystem", "requestWIFIConnection ssid : " + networkSSID);

        ConnectionManager connectionManager = new ConnectionManager(context , currentActivity);
        connectionManager.requestWIFIConnection(networkSSID,networkPass);
    }

    /**
     * GetAllWifiAvailable
     *
     * @param  context         the android context of the calling application
     * @param  currentActivity the android activity of the calling application
     * @return List<String>
     */
    public static List<String> GetAllWifiAvailable(Context context , Activity currentActivity) {
        ConnectionManager connectionManager = new ConnectionManager(context , currentActivity);
        return connectionManager.getWifiSSIDList();
    }

    /**
     * GetAllAvailableWifiInfo
     *
     * @param  context         the android context of the calling application
     * @param  currentActivity the android activity of the calling application
     * @return List<WifiData>
     */
    public static List<WifiData> GetAllAvailableWifiInfo(Context context , Activity currentActivity) {
        ConnectionManager connectionManager = new ConnectionManager(context , currentActivity);
        return connectionManager.getWifiDataList();
    }

    /**
     * GetCurrentSSID
     *
     * @param  context         the android context of the calling application
     * @param  currentActivity the android activity of the calling application
     * @return String
     */
    public static String GetCurrentSSID(Context context , Activity currentActivity) {
        ConnectionManager connectionManager = new ConnectionManager(context , currentActivity);
        return connectionManager.GetCurrentSSID();
    }

    /**
     * GetCurrentWifiInfo
     *
     * @param  context         the android context of the calling application
     * @param  currentActivity the android activity of the calling application
     * @return WifiData
     */
    public static WifiData GetCurrentWifiInfo (Context context , Activity currentActivity) {
        ConnectionManager connectionManager = new ConnectionManager(context , currentActivity);
        return connectionManager.getCurrentWifiData();
    }

    /**
     * GetTotalRAMInGB
     *
     * @param  context         the android context of the calling application
     * @return float
     */
    public static float GetTotalRAMInGB(Context context)
    {
        ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        long totalMemory = memInfo.totalMem;
        float memInGB = totalMemory /(1024.0f * 1024.0f *1024.0f);
        return memInGB;
    }

    /**
     * GetFreeInternalStorageinGB
     *
     * @return float
     */
    public static float GetFreeInternalStorageinGB()
    {
        long freeInternalStorage = getFreeMemory(Environment.getDataDirectory());
        float freeInternalStorageInGB = freeInternalStorage /  (1024.0f * 1024.0f *1024.0f);
        return freeInternalStorageInGB;
    }

    /**
     * GetTotalInternalStorageInGB
     *
     * @return float
     */
    public static float GetTotalInternalStorageInGB()
    {
        File path        = Environment.getDataDirectory();
        StatFs stat      = new StatFs(path.getPath());
        long blockSize   = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        float totalStorageInGB = (totalBlocks * blockSize) / (1024.0f * 1024.0f *1024.0f);
        return totalStorageInGB;
    }

    /**
     * Get external (SDCARD) free space
     *
     * @return long
     */
    public static long GetFreeExternalMemory()
    {
        return getFreeMemory(Environment.getExternalStorageDirectory());
    }


    /**
     *  Get Android OS (system partition) free space
     *
     * @return long
     */
    public static long GetFreeSystemMemory()
    {
        return getFreeMemory(Environment.getRootDirectory());
    }

    /**
     *  Get free space for provided path
     *  Note that this will throw IllegalArgumentException for invalid paths
     *
     * @param  path
     * @return long
     */
    public static long getFreeMemory(File path)
    {
        StatFs stats = new StatFs(path.getAbsolutePath());
        return stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
    }

    /**
     *  SetTimeAndDateTest
     *
     * @param  context the android context of the calling application
     */
    public static void SetTimeAndDateTest(Context context)
    {
        Calendar c = Calendar.getInstance();
        c.set(2013, 8, 15, 12, 34, 56);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setTime(c.getTimeInMillis());
    }

    /**
     *  getBrightness
     *
     * @param  context the android context of the calling application
     * @return int
     */
    public static int getBrightness(Context context)
    {
        int brightness= 0;

        try {
            brightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        Log.d("LynxAndroidSystem", "BackLightValue : " + brightness);
        return brightness;
    }

    /**
     *  setBrightness
     *
     * @param  context    the android context of the calling application
     * @param  brightness
     */
    public static void setBrightness(Context context, int brightness)
    {
        if (checkSystemWritePermission(context))
        {
            Log.d("LynxAndroidSystem", "checkSystemWritePermission returns true");
            Log.d("LynxAndroidSystem", "brightness : " + brightness);

            //constrain the value of brightness
            if (brightness < 0)
                brightness = 0;
            else if (brightness > 2048)
                brightness = 2048;

            ContentResolver cResolver = context.getContentResolver();
            Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
        }
    }

    /**
     *  checkSystemWritePermission
     *
     * @param  context    the android context of the calling application
     * @return boolean
     */
    private static boolean checkSystemWritePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(Settings.System.canWrite(context))
                return true;
            else
                openAndroidPermissionsMenu(context);
        }
        return false;
    }

    /**
     *  openAndroidPermissionsMenu
     *
     * @param  context    the android context of the calling application
     */
    private static void openAndroidPermissionsMenu(Context context)
    {
        Log.d("LynxAndroidSystem", "in openAndroidPermissionsMenu  : ");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     *  getBluetoothDeviceTypeCode
     *
     * @param  AndroidBTDeviceTypeCode
     * @return int
     */
    private static int  getBluetoothDeviceTypeCode(int AndroidBTDeviceTypeCode)
    {
        int ret = 0;

        switch (AndroidBTDeviceTypeCode)
        {
            case BluetoothClass.Device.Major.PHONE : {
                ret = 1;
            } break;
            case BluetoothClass.Device.Major.COMPUTER :{
                ret = 2;
            } break;
            case BluetoothClass.Device.Major.AUDIO_VIDEO :{
                ret = 3;
            } break;
            default:
                    ret = 0;
                    break;
        }

        return ret;

    }


    /**
     *  startHomeApplication
     *
     * @param  context          the android context of the calling application
     */
    public static void startHomeApplication(Context context)
    {
        Log.d("LynxAndroidSystem", "in startHomeApplication");

        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        i.addCategory(Intent.CATEGORY_HOME);
        context.startActivity(i);
    }

    /*
    public static void finchControllersServiceCall(Context context)
    {
        try {
            Log.d("LynxAndroidSystem", "in finchControllersServiceCall :");

            Intent serviceIntent = new Intent();
            ComponentName serviceComponentName = new ComponentName("com.finchtechnologies.sxr.finchservice","com.finchtechnologies.sxr.finchservice.FinchService");

            Log.d("LynxAndroidSystem", "serviceIntent : " + serviceIntent);

            serviceIntent.setComponent(serviceComponentName);
            context.startForegroundService(serviceIntent);

        } catch (Exception e) {
            e.printStackTrace();
            Log.d("LynxAndroidSystem", "Exception string : " + e.toString());
        }

        Log.d("LynxAndroidSystem", "no problem");
    }


    public static void CloseSystemDialog(Context context)
    {

        try {
            Log.d("LynxAndroidSystem", "CloseSystemDialog");

            Intent closeDialog =  new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(closeDialog);

        } catch (Exception e) {
            e.printStackTrace();
            Log.d("LynxAndroidSystem", "Exception string : " + e.toString());
        }

        Log.d("LynxAndroidSystem", "no problem");
    }
    */


    /**
     * KillApplication
     *
     * @param  packageName
     * @param  context          the android context of the calling application
     */
    public static void KillApplication(String packageName, Context context)
    {
        Log.d("LynxAndroidSystem", "--------- KillApplication with package name :" + packageName);

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        try {
            am.killBackgroundProcesses(packageName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * RestartApp
     *
     * @param  context          the android context of the calling application
     */
    public static void RestartApp(Context context) {

        Log.d("LynxAndroidSystem", " -------- RestartApp called");

        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }

    /**
     * computeLauncherAndMainApplicationList
     *
     * @param  context          the android context of the calling application
     */
    public static void computeLauncherAndMainApplicationList(Context context)
    {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> pkgAppsList = context.getPackageManager().queryIntentActivities(mainIntent, 0);
        CharSequence[] entries = new CharSequence[pkgAppsList.size()];
        CharSequence[] entryValues = new CharSequence[pkgAppsList.size()];

        mMainAndLauncherAppSet.clear();

        int i = 0;

        for (ResolveInfo P : pkgAppsList)
        {
            entries[i] = P.loadLabel(context.getPackageManager());
            //Log.d("LynxAndroidSystem", " -------- IntentActivities[i] " + entries[i]);
            mMainAndLauncherAppSet.add(entries[i].toString());

            ++i;
        }
    }

    /**
     * computeSVRAppList
     *
     * @param  context          the android context of the calling application
     */
    public static void computeSVRAppList(Context context)
    {
        Log.d("LynxAndroidSystem", " -------- Compute SVR Application list" );

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory("com.qti.intent.category.SNAPDRAGON_VR");
        final List<ResolveInfo> pkgAppsList = context.getPackageManager().queryIntentActivities(mainIntent, 0);
        CharSequence[] entries = new CharSequence[pkgAppsList.size()];
        CharSequence[] entryValues = new CharSequence[pkgAppsList.size()];

        mSVRAppSet.clear();

        int i = 0;

        Log.d("LynxAndroidSystem", " -------- SVR applications count : " + pkgAppsList.size() );

        for (ResolveInfo P : pkgAppsList)
        {
            entries[i] = P.loadLabel(context.getPackageManager());
            Log.d("LynxAndroidSystem", " -------- svr app name : " + entries[i]);
            mSVRAppSet.add(entries[i].toString());

            ++i;
        }
    }

    /**
     * isMainAndLauncherApplication
     *
     * @param  strAppName
     */
    public static boolean isMainAndLauncherApplication(String strAppName)
    {
        if (mMainAndLauncherAppSet.contains(strAppName))
            return true;

        return false;
    }

    /**
     * isSVRApp
     *
     * @param  strAppName
     */
    public static boolean isSVRApp(String strAppName)
    {
        if (mSVRAppSet.contains(strAppName))
            return true;

        return false;
    }

    /**
     * simulateDeleteApp
     *
     * @param  currentActivity
     * @param  packageName
     */
    public static void simulateDeleteApp(Activity currentActivity, String packageName)
    {
        Log.d("LynxAndroidSystem", " simulateDeleteApp " + packageName);

        String toto = "package:"+packageName;
        UnityPlayer.UnitySendMessage(LynxAndroidSystemComMng.mUnityGameObjectForCallback, "AndroidPackageRemoved", toto);
    }

    /**
     * readApplicationMetaData
     *
     * @param  applicationInfo
     * @param  applicationName
     * @return boolean
     */
    public static boolean readApplicationMetaData(ApplicationInfo applicationInfo,String applicationName)
    {
        try {
            //ApplicationInfo app = context.getPackageManager().getApplicationInfo(context.getPackageName(),PackageManager.GET_META_DATA);
            Bundle bundle = applicationInfo.metaData;

            String permissions = applicationInfo.permission;

            Log.d("LynxAndroidSystem", "applicationName : " + applicationName);
            //Log.d("LynxAndroidSystem", "Bundle : " + bundle.size());

            Log.d("LynxAndroidSystem", "permissions : " + permissions);

            for (String key : bundle.keySet()) {
                Log.d("LynxAndroidSystem", key + " is a key in the bundle");
            }
            Log.d("LynxAndroidSystem", " ");

            //Log.d("TEST", "google: " + bundle.getString("com.google.android.gms.version"));
            //Log.d("TEST", "version: " + bundle.getString("dbVersion"));

        } catch (Exception e) {
            Log.e("LynxAndroidSystem", "--------- error in readApplicationMetaData :");
            e.printStackTrace();
        }

        return true;
    }

    /**
     * readAllApplicationPermissions
     *
     * @param  context          the android context of the calling application
     * @return boolean
     */
    public static boolean readAllApplicationPermissions(Context context)
    {
        Log.d("LynxAndroidSystem", "\n");

        StringBuffer appNameAndPermissions = new StringBuffer();
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo applicationInfoItem : packages)
        {
            Log.d("LynxAndroidSystem", "App: " + applicationInfoItem.name + " Package: " + applicationInfoItem.packageName);

            try {
                PackageInfo packageInfo = pm.getPackageInfo(applicationInfoItem.packageName, PackageManager.GET_PERMISSIONS);
                appNameAndPermissions.append(packageInfo.packageName + "*******:\n");

                //Get Permissions
                String[] requestedPermissions = packageInfo.requestedPermissions;

                if (requestedPermissions != null)
                {
                    for (int i = 0; i < requestedPermissions.length; i++) {
                        Log.d("LynxAndroidSystem", requestedPermissions[i]);
                        appNameAndPermissions.append(requestedPermissions[i] + "\n");
                    }

                    appNameAndPermissions.append("\n");
                }
                else
                {

                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }

        return true;
    }

    /**
     * isOpenXRApp
     *
     * @param  context     the android context of the calling application
     * @param  packageName
     * @return boolean
     */
    public static boolean isOpenXRApp(Context context, String packageName)
    {
        PackageManager pm = context.getPackageManager();

        try {

            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);

            //Get Permissions :
            String[] requestedPermissions = packageInfo.requestedPermissions;

            if (requestedPermissions != null)
            {
                for (int i = 0; i < requestedPermissions.length; i++)
                {
                    //Log.d("LynxAndroidSystem", requestedPermissions[i]);

                    if (requestedPermissions[i].equals("org.khronos.openxr.permission.OPENXR_SYSTEM") || requestedPermissions[i].equals("org.khronos.openxr.permission.OPENXR") )
                    {
                        return true;
                    }
                }
            }
            else
            {
                Log.d("LynxAndroidSystem", "requestedPermissions == null ");
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }

        return false;
    }

    /**
     * EnableADB
     *
     * @param  currentActivity     the android context of the calling application
     * @param  value
     */
    public static void EnableADB(Activity currentActivity,int value)
    {
        Log.i("LynxAndroidSystem", "--------- EnableADB called with value :" + value);

        try {
            Settings.Global.putInt(currentActivity.getContentResolver(),Settings.Global.ADB_ENABLED, value);
        } catch (Exception e) {
            Log.e("LynxAndroidSystem", "--------- EnableADB ERROR :" + e);
            e.printStackTrace();
        }
    }

    /**
     * EnableDeveloperMode
     *
     * @param  currentActivity the android context of the calling application
     * @param  value
     */
    public static void EnableDeveloperMode(Activity currentActivity,boolean value)
    {
        Log.i("LynxAndroidSystem", "--------- EnableDeveloperMode called with value :" + value);
        int i = value ? 1 : 0;
        try {
            Settings.Global.putInt(currentActivity.getContentResolver(),Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, i);

            Log.i("LynxAndroidSystem", "--------- force enable ADB call with value :" + value);

            // March 2023. it's really anoying to disable adb in non developer mode :
            // so don't disable adb even if we pass in non developer mode
            if (i==1)
                EnableADB(currentActivity,i);

        } catch (Exception e) {
            Log.e("LynxAndroidSystem", "--------- EnableDevelopperMode :" + e);
            e.printStackTrace();
        }
    }

    /**
     * GetDeveloperModeState
     *
     * @param  currentActivity the android activity of the calling application
     * @return int
     */
    public static int GetDeveloperModeState(Activity currentActivity)
    {
        Log.i("LynxAndroidSystem", "--------- GetDeveloperModeState called :");

        int ret = 0;

        try {

            ret = Settings.Global.getInt(currentActivity.getContentResolver(),Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
            Log.i("LynxAndroidSystem", "--------- GetDeveloperModeState return :" + ret);

        } catch (Exception e)
        {
            ret = 0;
            Log.e("LynxAndroidSystem", "--------- GetDeveloperModeState error : " + e);
            e.printStackTrace();
        }

        return ret;
    }


    /**
     * EnableUsbFileTransferOnce
     *
     * @param  value
     */
    public static void EnableUsbFileTransferOnce(boolean value) {
        try {
            Log.i("LynxAndroidSystem", "--------- EnableUsbFileTransferOnce");
            // cedrock touch
            if (value)
                Runtime.getRuntime().exec("svc usb setFunctions mtp"); // MTP : Media Transfer Protocol
            else {
                // cedric : svc usb setFunctions without argument ; it works :
                Runtime.getRuntime().exec("svc usb setFunctions"); // MTP : Media Transfer Protocol
            }

        } catch (Exception e) {
            Log.e("LynxAndroidSystem", "--------- EnableUsbFileTransferOnce error : " + e);
            e.printStackTrace();
        }
    }

    /**
     * GetUsbFileTransferState
     *
     * @param  view
     * @return int
     */
    public static int GetUsbFileTransferState(View view) {

        int ret = 0;

        try {
            Log.i("LynxSystemAppTestTag", "--------- GetUsbFileTransferState");

            // svc usb setFunctions sans argument ; ça marche :
            //String ret = Runtime.getRuntime().exec("svc usb getFunctions"); // MTP : Media Transfer Protocol

            String arg = "svc usb getFunctions";

            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("svc usb getFunctions");
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;

            Log.i("LynxSystemAppTestTag", "--------- GetUsbFileTransferState  2222222 ");

            while ((line = br.readLine()) != null) {
                System.out.println(line);
                Log.i("LynxSystemAppTestTag", "--------- GetUsbFileTransferState  : " + line);
            }

        } catch (Exception e) {
            Log.e("LynxSystemAppTestTag", "--------- GetUsbFileTransferState error : " + e);
            e.printStackTrace();
        }

        return 0;
    }

    // need to be system for this :
    /**
     * forceKillAppFromPackageName
     *
     * @param  currentActivity
     * @param  packageName
     */
    public static void forceKillAppFromPackageName(Activity currentActivity, String packageName)
    {
        Log.i("LynxAndroidSystem", "forceKillAppFromPackageName Called with package name : " + packageName);

        try {
            ActivityManager am = (ActivityManager) currentActivity.getSystemService(Context.ACTIVITY_SERVICE);
            Method forceStopPackage = am.getClass().getDeclaredMethod("forceStopPackage", String.class);
            forceStopPackage.setAccessible(true);
            forceStopPackage.invoke(am, packageName);
        }
        catch (Exception e) {
            Log.e("LynxAndroidSystem", "Error in forceKillAppFromPackageName with error : ", e);
        }
    }

    /**
     * isPackageInstalled
     *
     * @param  context          the android context of the calling application
     * @param  packageName
     */
    public static boolean isPackageInstalled(Context context, String packageName) {

        PackageManager pm = context.getPackageManager();

        try {
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * setImmersiveSticky
     *
     * @param  context          the android context of the calling application
     * @param  activity
     */
    public static void setImmersiveSticky(Context context, Activity activity) {

        Log.d("LynxAndroidSystem", "setImmersiveSticky called : ");

        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.d("LynxAndroidSystem", "setImmersiveSticky called end : ");
    }


    /**
     * getTimeZoneReadableList
     *
     * @return  List<String>
     */
    public static List<String> getTimeZoneReadableList()
    {
        Log.i("LynxAndroidSystem", "getTimeZoneReadableList() called : " );

        return mTimeZonesReadableList;
    }

    /**
     * setTimeZone (sytem)
     *
     * @param  context the android context of the calling application
     * @param  timeZone
     */
    public static void setTimeZone(Context context, String timeZone)
    {
        Log.i("LynxAndroidSystem", "SetTimeZone(Context context, String timeZone) called with timeZone : " + timeZone );

        try {

            String timeZoneToSet=mTimeZonesMap.get(timeZone);

            if (timeZoneToSet!=null && timeZoneToSet.length()>0)
            {
                Log.i("LynxAndroidSystem", " ");
                Log.i("LynxAndroidSystem", "TimeZone to set with correct android format : " + timeZoneToSet);
                Log.i("LynxAndroidSystem", " ");

                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                am.setTimeZone(timeZoneToSet);
            }
            else
            {
                Log.e("LynxAndroidSystem", "TimeZoneToSet Not found, impossible to adjust Timezone");
            }
        }
        catch (Exception e) {
            Log.e("LynxAndroidSystem", "Error in SetTimeZone with error : ", e);
        }
    }

    /**
     * getCurrentTimeZone
     *
     * @return  String
     */
    public static String getCurrentTimeZone()
    {
        Log.i("LynxAndroidSystem", "getCurrentTimeZone called ");

        TimeZone tz = TimeZone.getDefault();

        /*
        Log.i("LynxAndroidSystem","tz.toString() "+tz.toString());
        Log.i("LynxAndroidSystem","TimeZone tz.getDisplayName(false, TimeZone.SHORT) "+tz.getDisplayName(false, TimeZone.SHORT));

        Log.i("LynxAndroidSystem","TimeZone tz.getDisplayName(false, TimeZone.LONG) "+tz.getDisplayName(false, TimeZone.LONG));
        Log.i("LynxAndroidSystem","TimeZone tz.getDisplayName(true, TimeZone.SHORT) "+tz.getDisplayName(true, TimeZone.SHORT));
        Log.i("LynxAndroidSystem","TimeZone tz.getDisplayName(true, TimeZone.LONG) "+tz.getDisplayName(true, TimeZone.LONG));
         */

        Log.i("LynxAndroidSystem","Timezone id = " +tz.getID()); // exemple : Africa/Cairo

        return tz.getID();
    }

    /**
     * getReadableTimeZoneFromNormalizedTimeZone
     *
     * @param normalizedTimeZone
     */
    public static String getReadableTimeZoneFromNormalizedTimeZone(String normalizedTimeZone)
    {
        Log.i("LynxAndroidSystem", "getReadableTimeZoneFromNormalizedTimeZone called with normalizedTimeZone : " + normalizedTimeZone);

        // Important : set GMT+0 as default readable timeZone
        // If there is no correspondance , that is this one that will be displayed in the launcher.

        String ret="00- GMT+0 Europe/London";

        for (Map.Entry<String, String> entry : mTimeZonesMap.entrySet()) {

            String value = entry.getValue().toString();

            if (value.equals(normalizedTimeZone))
            {
                ret = entry.getKey().toString();
                Log.i("LynxAndroidSystem", "ReadableTimeZone found is : " + normalizedTimeZone);
                break;
            }
        }

        Log.i("LynxAndroidSystem", "ReadableTimeZone ret is  : " + ret);
        return ret;
    }

    /**
     * getUltraleapAnalyticsState
     *
     * @param  context the android context of the calling application
     * @return boolean
     */
    public static boolean getUltraleapAnalyticsState(Context context)
    {
        try (
                Cursor cursor = context.getContentResolver().query(mUltraleapTrackingServiceUri, null, "analytics_enabled", null,null)
        ) {
            if (cursor != null && cursor.getCount() == 1)
            {
                cursor.moveToFirst();
                boolean analytics_enabled = cursor.getInt(cursor.getColumnIndexOrThrow("value")) == 1;
                Log.i("LynxAndroidSystem", "analytics_enabled : " + analytics_enabled);

                return analytics_enabled;
            }
        } catch (Exception e) {
            // Handle Exception (e.g. tracking service isn't running, incorrect parameter, etc...)
            Log.e("LynxAndroidSystem", "Error in getUltraleapAnalyticsState with error = ", e);
            return false;
        }

        return false;
    }

    /**
     * setUltraleapAnalyticsState
     *
     * @param  context the android context of the calling application
     * @param  analytics_enabled
     */
    public static void setUltraleapAnalyticsState(Context context, boolean analytics_enabled)
    {
        String pre_shared_key = "#aj4$dF1Wqx8j8ck";
        ContentValues values = new ContentValues(2);
        values.put("_psk_", pre_shared_key);
        values.put("analytics_enabled", analytics_enabled);

        try {
            Log.i("LynxAndroidSystem", "in setUltraleapAnalyticsState with analytics_enabled to : " + analytics_enabled);
            context.getContentResolver().update(mUltraleapTrackingServiceUri, values, null, null);

        } catch (Exception e) {
            Log.e("LynxAndroidSystem", "Error in setUltraleapAnalyticsState with error = ", e);
        }
    }

    static SensorManager mSensorManager;
    static Sensor        mAccelerometer;

    public static void testProximitySensor(Context context)
    {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(TYPE_PROXIMITY /*Sensor.TYPE_ACCELEROMETER*/);

        mSensorManager.registerListener((SensorEventListener)context, mAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onSensorChanged(SensorEvent event) {
        /*
        event.

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];


        if (!mInitialized) {
            mLastX = x;
            mLastY = y;
            mLastZ = z;
            mInitialized = true;
        } else {
            float deltaX = Math.abs(mLastX - x);
            float deltaY = Math.abs(mLastY - y);
            float deltaZ = Math.abs(mLastZ - z);
            if (deltaX < NOISE) deltaX = (float)0.0;
            if (deltaY < NOISE) deltaY = (float)0.0;
            if (deltaZ < NOISE) deltaZ = (float)0.0;
            mLastX = x;
            mLastY = y;
            mLastZ = z;
            if (deltaX > deltaY) {


                // horizontal shaking    receive here your call

            } else if (deltaY > deltaX) {

                //Reject here your call

            } else {
                // no shaking
            }
        }

         */
    }

} // end class










