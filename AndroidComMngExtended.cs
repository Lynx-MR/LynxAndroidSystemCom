/**
 * @file AndroidComMngExtended.cs
 * 
 * @author Cédric Morel Francoz
 * 
 * @brief Manage all calls to java plugin which allows to communicate with the android system. 
 */


using System;
using System.Collections;
using System.Collections.Generic;
using TMPro;
using UnityEngine;
using UnityEngine.Assertions;
using UnityEngine.Events;
using UnityEngine.XR.Management;

using lynxlauncher;
using lynx;

// Version LynxLauncher 0.3 oxr : 1 /12/ 2022
// Version JAR 0.3. 

public class AndroidComMngExtended : MonoBehaviour
{
    //PUBLIC
    [HideInInspector] public CustomUnityStringEvent mNewBTDevicePairedEvent = null;
    [HideInInspector] public CustomUnityStringEvent mBTDeviceUnpairedEvent = null;
    [HideInInspector] public CustomUnityStringEvent mPackageRemovedEvent = null;
    [HideInInspector] public CustomUnityStringEvent mNewPackageInstalledEvent = null;
    [HideInInspector] public CustomUnityBoolEvent mWifiStateChangedEvent = null;
    [HideInInspector] public CustomUnityIntEvent mAudioVolumeChangeBySoftwareEvent = null;
    [HideInInspector] public CustomUnityIntEvent mAudioVolumeChangeByHardwareEvent = null;
    [HideInInspector] public CustomUnityIntEvent mBatteryLevelChangeEvent = null;
    [HideInInspector] public UnityEvent          mTimeZoneChangeEvent = null;

    [HideInInspector] public UnityEvent mAndroidSystemComPlugInInitSucceedEvent;
    [HideInInspector] public UnityEvent mAndroidSystemComPlugInInitFailedEvent;
    [HideInInspector] public UnityEvent mLoadApplicationDataFinishedEvent;

    // to store data about all installed application on device
    public struct OSApplicationData
    {
        public string    applicationName;
        public string    packageName;
        public Texture2D iconTexture;
        public long      lastUpdateDate;
        public bool      lynxApp;
        public bool      isOpenXRApp;
        public bool      isSVRApp;
    }
    // to store data about all wifi available
    public struct WifiData
    {
        public string ssid;
        public int level;
        public int security;
    }
    // to store data about bluetooth
    public struct BluetoothData
    {
        public string name;
        public int deviceType;
    }

    public List<OSApplicationData> mOSApplicationDataList { get; private set; }
    public List<WifiData>          mWifiDataList { get; private set; }
    public List<BluetoothData>     mBluetoothDataList { get; private set; }
    public List<BluetoothData>     mBluetoothDataList2 { get; private set; }

    [SerializeField]
    private bool m_killLauncherWhenLaunchApp = true;

    //PRIVATE
    private const string LynxAndroidSystemComPlugInName = "com.lynx.lynxandroidsystemcom.LynxAndroidSystemComMng";

    private AndroidJavaClass  mAndroidSystemComPlugIn = null;
    private AndroidJavaObject mCurrentActivity = null;
    private AndroidJavaObject mApplicationContext = null;

    private string mCurrentApplicationToDelete;
    private string mCurrentPackageToDelete;

    private bool mLoadApplicationDataComplete = true;
    private bool mAudioVolumeChangedbySoftware = false;

    private string iGaliaWolvicPackageName = "com.igalia.wolvic";

    // Touch
    //Singleton
    private static AndroidComMngExtended AndroidComMngExtendedInst = null; //Reference to the AndroidComMngExtendedInst, to make sure it's been included
    public static AndroidComMngExtended Instance()
    {
        if (!AndroidComMngExtendedInst)
        {
            AndroidComMngExtendedInst = FindObjectOfType(typeof(AndroidComMngExtended)) as AndroidComMngExtended;
            if (!AndroidComMngExtendedInst)
            {
                Debug.LogError("There needs to be one active AndroidComMngExtended script on a GameObject in your scene.");
            }
        }
        return AndroidComMngExtendedInst;
    }

    private void Awake()
    {
        if (mAndroidSystemComPlugInInitSucceedEvent == null) mAndroidSystemComPlugInInitSucceedEvent = new UnityEvent();
        if (mAndroidSystemComPlugInInitFailedEvent == null) mAndroidSystemComPlugInInitFailedEvent = new UnityEvent();

        Application.SetStackTraceLogType(LogType.Log , StackTraceLogType.None);
        Application.SetStackTraceLogType(LogType.Warning, StackTraceLogType.None);
    }

    private void Start()
    {
#if UNITY_ANDROID && !UNITY_EDITOR   
        AndroidInit();
#endif   
    }

    private void OnApplicationFocus(bool value)
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        if (value == false)
        {
            Debug.Log("App loose focus");
            UnregisterVolumeChangeReceiver();
            
        }
        else
        {
            Debug.Log("App get focus");
            RegisterVolumeChangeReceiver();
        }
#endif
    }

    private void OnApplicationPause(bool value)
    {
        if (value) // when the application really pause
        {
            Debug.Log("******** Save application data before quit");
            SaveLoadAppMng.Instance.SaveLauncherSettings();
        }
    }



    private void AndroidInit()
    {
        mOSApplicationDataList = new List<OSApplicationData>();
        mWifiDataList = new List<WifiData>();
        mBluetoothDataList = new List<BluetoothData>();
        mBluetoothDataList2 = new List<BluetoothData>();

        // Important : Initialisation of the java plugin :
        InitAndroidJavaPlugin();

        // Pass the Unity object name to the plug in to send Message from java to Unity
        mAndroidSystemComPlugIn.CallStatic("setUnityGameObjectName", gameObject.name);

        if (mNewBTDevicePairedEvent == null) mNewBTDevicePairedEvent = new CustomUnityStringEvent();
        if (mBTDeviceUnpairedEvent == null) mBTDeviceUnpairedEvent = new CustomUnityStringEvent();
        if (mPackageRemovedEvent == null) mPackageRemovedEvent = new CustomUnityStringEvent();
        if (mNewPackageInstalledEvent == null) mNewPackageInstalledEvent = new CustomUnityStringEvent();
        if (mWifiStateChangedEvent == null) mWifiStateChangedEvent = new CustomUnityBoolEvent();
        if (mAudioVolumeChangeBySoftwareEvent == null) mAudioVolumeChangeBySoftwareEvent = new CustomUnityIntEvent();
        if (mAudioVolumeChangeByHardwareEvent == null) mAudioVolumeChangeByHardwareEvent = new CustomUnityIntEvent();
        if (mBatteryLevelChangeEvent == null) mBatteryLevelChangeEvent = new CustomUnityIntEvent();
        if (mTimeZoneChangeEvent == null) mTimeZoneChangeEvent = new UnityEvent();

        mAndroidSystemComPlugIn.CallStatic("registerChangesReceivers", mCurrentActivity, mApplicationContext);

        if (mLoadApplicationDataFinishedEvent == null) mLoadApplicationDataFinishedEvent = new UnityEvent();
    }
    private void InitAndroidJavaPlugin()
    {
        try
        {
            var plugin = new AndroidJavaClass(LynxAndroidSystemComPlugInName);

            if (plugin != null)
            {
                mAndroidSystemComPlugIn = plugin;
            }
            else
            {
                Debug.LogError("mAndroidSystemComPlugIn is NULL");
                return;
            }

            AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");

            if (unityPlayer == null)
            {
                Debug.LogError("unityPlayer is NULL");
                return;
            }


            mCurrentActivity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");

            if (mCurrentActivity == null)
            {
                Debug.LogError("CurrentActivity is NULL");
                return;
            }

            mApplicationContext = mCurrentActivity.Call<AndroidJavaObject>("getApplicationContext");

            if (mApplicationContext == null)
            {
                Debug.LogError("ApplicationContext is NULL");
                return;
            }

            string libJarVersion = mAndroidSystemComPlugIn.CallStatic<string>("getVersion");
            Debug.Log("-------------- LynxAndroidSystemComMng was correctly initialized and its version is " + libJarVersion +" -----------------");
            Debug.Log(" ");

            // Force to set Ultraleap analytics to false. 
            SetUltraleapAnalyticsState(false);

            if (mAndroidSystemComPlugInInitSucceedEvent != null)
            {
                mAndroidSystemComPlugInInitSucceedEvent.Invoke();
            }
        }
        catch (System.Exception e)
        {
            Debug.LogError(e, this);

            Debug.Log("-----------");
            Debug.Log("----------- Init AndroidSystemComPlugIn FAILED, application will not run normally-------------");
            Debug.Log("-----------");

            // tell the user that the plug in init fails : 
            if (mAndroidSystemComPlugInInitFailedEvent != null)
            {
                mAndroidSystemComPlugInInitFailedEvent.Invoke();
            }
        }
    }


    #region CALLBACKS sent from Java Plugin
    public void AndroidPackageRemoved(string packageName)
    {
        Debug.Log("------------------- AndroidPackageRemoved = " + packageName);

        string strTemp = "package:" + mCurrentPackageToDelete;

        // code OK :
        
        if (packageName == strTemp) // verify that is the user that delete the application. 
        {
            if (mPackageRemovedEvent != null)
            {
                Debug.Log("------------------- before mPackageRemovedEvent.Invoke(mCurrentApplicationToDelete); = " + strTemp);
                mPackageRemovedEvent.Invoke(mCurrentApplicationToDelete);
            }
        }
        

        //mPackageRemovedEvent.Invoke(mCurrentApplicationToDelete);

    }
    public void AndroidNewPackageInstalled(string packageName)
    {
        Debug.Log("------------------- AndroidNewPackageInstalled = " + packageName);

        if (mNewPackageInstalledEvent != null)
        {
            mNewPackageInstalledEvent.Invoke(packageName);
        }
    }

    public void NewBluetoophDeviceBonded(string BTDeviceName)
    {
        Debug.Log("------------------- NewBluetoothDeviceBonded = " + BTDeviceName);

        if (mNewBTDevicePairedEvent != null)
        {
            mNewBTDevicePairedEvent.Invoke(BTDeviceName);
        }
    }
    public void BluetoophDeviceUnbonded(string BTDeviceName)
    {
        Debug.Log("------------------- BluetoophDeviceUnbonded = " + BTDeviceName);

        if (mBTDeviceUnpairedEvent != null)
        {
            mBTDeviceUnpairedEvent.Invoke(BTDeviceName);
        }
    }

    public void BatteryLevelChange(string batteryLevel)
    {
        // Battery level varies from 0 to 100. it's a percentage 
        //Debug.Log("------------------- BatteryLevelChange(string batteryLevel) received in AndroidComMngExtended = " + batteryLevel);
        int iBatteryLevel = 0;
        int.TryParse(batteryLevel, out iBatteryLevel);
        mBatteryLevelChangeEvent.Invoke(iBatteryLevel);
    }
    public void AudioVolumeChange(string volume)
    {
        // Volume sent varies from 0 to 15. 
        int iVolume = 0;      
        int.TryParse(volume, out iVolume);

        if (mAudioVolumeChangedbySoftware)
            mAudioVolumeChangeBySoftwareEvent.Invoke(iVolume);
        else
            mAudioVolumeChangeByHardwareEvent.Invoke(iVolume);

        mAudioVolumeChangedbySoftware = false;
    }
    #endregion CALLBACKS sent from Java Plugin


    #region System
    public void EnableDeveloperMode(bool value)
    {
        //Debug.Log($"AndroidComMngExtended.EnableDeveloperMode({value})");
        mAndroidSystemComPlugIn.CallStatic("EnableDeveloperMode", mCurrentActivity, value);
    }
    public void EnableUsbFileTransferOnce(bool value)
    {
        mAndroidSystemComPlugIn.CallStatic("EnableUsbFileTransferOnce", value);
    }
    public void AskForLocationPermission()
    {
        mAndroidSystemComPlugIn.CallStatic("RequestLocationPermission", mApplicationContext, mCurrentActivity);
    }

    public bool GetDeveloperModeState()
    {
        //Debug.Log($"AndroidComMngExtended.GetDeveloperModeState()");
        int i = mAndroidSystemComPlugIn.CallStatic<int>("GetDeveloperModeState", mCurrentActivity);
        //Debug.Log($"AndroidComMngExtended.GetDeveloperModeState() devState i = {i}");
        return (i == 1);
    }

    public string GetDeviceAndSystemInfo()
    {
        string ret = "";
        ret = mAndroidSystemComPlugIn.CallStatic<string>("getDeviceAndSystemInfo");
        return ret;
    }

    public string GetLynxAndroidSystemVersion()
    {
        string ret = "1.0.0";

        if (mAndroidSystemComPlugIn!=null)
            ret = mAndroidSystemComPlugIn.CallStatic<string>("getLynxAndroidSystemVersion");

        return ret;
    }
    #endregion

    #region App Mng
    public void LoadApplicationData()
    {
        if (mLoadApplicationDataComplete) StartCoroutine("LoadApplicationDataCoroutine");
        else Debug.LogWarning("--------------- LoadApplicationData() already running");
    }
    public IEnumerator LoadApplicationDataCoroutine()
    {     
        Debug.Log("------------- BEGIN of LoadApplicationDataCoroutine.");

        Debug.Log(" ");
        mLoadApplicationDataComplete = false;
        mOSApplicationDataList.Clear();

        int flag = new AndroidJavaClass("android.content.pm.PackageManager").GetStatic<int>("GET_META_DATA");
        AndroidJavaObject pm = mCurrentActivity.Call<AndroidJavaObject>("getPackageManager");

        AndroidJavaObject packageInfoList = pm.Call<AndroidJavaObject>("getInstalledPackages", flag); // retrun une list de packages infos.
        int count = packageInfoList.Call<int>("size");

        //Debug.Log("------------- Total application package count : " + count);

        // Locate the application that has to be displayed, the one that has in their manifest :Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER
        mAndroidSystemComPlugIn.CallStatic("computeLauncherAndMainApplicationList", mApplicationContext);

 
        mAndroidSystemComPlugIn.CallStatic("computeSVRAppList", mApplicationContext);

        int ii = 0;

        for (int i = 0; ii < count;)
        {
            AndroidJavaObject packageInfo = packageInfoList.Call<AndroidJavaObject>("get", ii);

            try
            {
                string packageName = packageInfo.Get<string>("packageName");

                // Get the appplication info from Package Info :
                AndroidJavaObject applicationInfo = packageInfo.Get<AndroidJavaObject>("applicationInfo");
                string applicationName = pm.Call<string>("getApplicationLabel", applicationInfo);

                if (true) // parse all apps :
                //if (packageName.Contains("com.") && !packageName.Contains("scenehome") && !packageName.Contains("service") && !packageName.Contains("Service"))
                // Display only application with com.lynx in their packageName :
                // If ( (packageName.Contains("com.lynx") || packageName.Contains("com.Lynx")) && !packageName.Contains("scenehome")) 
                {
                    // Don't display system app except OnBoardingApp and Wolvic iGalia : 
                    if (!packageName.Contains(LynxOnBoardingLaunch.GetOnBoardingPackageName()) && !packageName.Contains(iGaliaWolvicPackageName))
                    {
                        if (mAndroidSystemComPlugIn.CallStatic<bool>("isSystem", applicationInfo))
                        {
                            ii++;
                            continue;
                        }
                    }
                    else
                    {
                        Debug.Log("****************** On Boarding App or Igalia wolvic will be displayed");
                    }
         
                    // don't display android non launcher and non main app (info in Manifest)
                    if (!mAndroidSystemComPlugIn.CallStatic<bool>("isMainAndLauncherApplication", applicationName))
                    {
                        ii++;
                        continue;
                    }

                    // don't display openxrApplication : 
                    if (packageName.Contains("com.qualcomm.qti.openxrruntime"))
                    {                       
                        ii++;
                        continue;
                    }

                    //Debug.Log("------------- applicationName : " + applicationName);
                    //Debug.Log("------------- Package Name    : " + packageName);

                    //byte[] decodedBytes = mAndroidSystemComPlugIn.CallStatic<byte[]>("getIcon", pm, applicationInfo);
                    // replace byte[] by SByte cause byte[] is obsolete  
                    SByte[] decodedBytes = mAndroidSystemComPlugIn.CallStatic<SByte[]>("getIcon", pm, applicationInfo);

                    bool bHasIcon = true;
                    if (decodedBytes == null) // cedric : it could happens
                    {
                        Debug.LogWarning("------------- No App icon for package name : " + packageName);
                        bHasIcon = false;

                    }

                    Texture2D text = null;
                    byte[] dest;

                    if (bHasIcon)
                    {
                        text = new Texture2D(1, 1, TextureFormat.ARGB32, false);
                        dest = Array.ConvertAll(decodedBytes, (a) => (byte)a);
                        text.LoadImage(dest);
                    }


                    long lastUpdateDate = mAndroidSystemComPlugIn.CallStatic<long>("getLastUpdateDate", packageInfo);

                    // look if app are openXR or SVR applications
                    bool isSVR = false;
                    bool isOpenXR = mAndroidSystemComPlugIn.CallStatic<bool>("isOpenXRApp", mApplicationContext, packageName);

                    if (isOpenXR)
                    {
                        // do nothing
                        //Debug.Log("App with package name " + packageName + "is an OpenXR application");
                    }
                    else
                    {
                        isSVR = mAndroidSystemComPlugIn.CallStatic<bool>("isSVRApp", applicationName);

                        if (isSVR)
                            Debug.Log("App with package name " + packageName + " is an SVR application");
                    }
                                
                    OSApplicationData oSApplicationData = new OSApplicationData();

                    oSApplicationData.applicationName = applicationName;
                    oSApplicationData.packageName     = packageName;               
                    oSApplicationData.lastUpdateDate  = lastUpdateDate;
                    oSApplicationData.isOpenXRApp     = isOpenXR;
                    oSApplicationData.isSVRApp        = isSVR;

                    if (packageName.Contains("lynx") || packageName.Contains("Lynx"))
                    {
                        oSApplicationData.lynxApp = true;                                  
                    }        
                    else
                        oSApplicationData.lynxApp = false;

                    if (bHasIcon == true)
                        oSApplicationData.iconTexture = text;
                    else
                        oSApplicationData.iconTexture = null;



                    mOSApplicationDataList.Add(oSApplicationData);                                     
                }

                Debug.Log("---------------- This application will be displayed in App panel : " + applicationName);
                Debug.Log(" ");

                i++;
                ii++;
            }

            catch (System.Exception e)
            {
                Debug.LogError(e, this);
                ii++;
            }

            yield return null;
        }
     
        mLoadApplicationDataComplete = true;
        mLoadApplicationDataFinishedEvent.Invoke();

        Debug.Log("------------- End of LoadApplicationDataCoroutine.");    
    }
    public SByte[] GetAppIcon()
    {
        string appPackageName = Application.identifier;
      
        int flag = new AndroidJavaClass("android.content.pm.PackageManager").GetStatic<int>("GET_META_DATA");
        AndroidJavaObject pm = mCurrentActivity.Call<AndroidJavaObject>("getPackageManager");

        SByte[] decodedBytes = mAndroidSystemComPlugIn.CallStatic<SByte[]>("GetIcon", pm, appPackageName);
       
        if (decodedBytes == null) // Cedric : it could happens
        {
            Debug.LogWarning("------------- No App icon for package name : " + appPackageName);         
        }

        return decodedBytes;
    }
    public bool isPackageInstalled(string packageName)
    {
        Debug.Log("------------- Call isPackageInstalled ");
        return mAndroidSystemComPlugIn.CallStatic<bool>("isPackageInstalled", mApplicationContext, packageName);
    }
    
    public void LaunchApp(string packageName)
    {
#if UNITY_ANDROID && !UNITY_EDITOR

        if (m_killLauncherWhenLaunchApp)
        {
            if (!LynxAPI.IsVR())
            {
                LynxAPI.SetVR();  
            }

            Debug.Log("------------- LynxAPI.SetVR() is called cause the launcher was is AR");
        }

#endif

        // First Kill old app :
        Debug.Log("------------- Kill App To Launch if it is already In background : " + packageName);
        KillAppInBackground(packageName);


        //KillAppInBackground("com.qualcomm.qti.openxrruntime"); 

        // new 17/11/2022 :
        //forceStopAppInBackground(packageName);

        // call from CSharp :
        bool fail = false;
        
        AndroidJavaObject packageManager = mCurrentActivity.Call<AndroidJavaObject>("getPackageManager");
        AndroidJavaObject launchIntent = null;

        try
        {
            launchIntent = packageManager.Call<AndroidJavaObject>("getLaunchIntentForPackage", packageName);
        }
        catch (System.Exception e)
        {
            fail = true;
            Debug.LogError("Exception in AndroidComMngExtended::LaunchApp" + e.ToString());
        }
 
        if (fail)
        {  //open app in store
            Application.OpenURL("https://lynx-r.com/");
        }
        else //open the app
            mCurrentActivity.Call("startActivity",launchIntent);

        if (m_killLauncherWhenLaunchApp)
        {
            // this is the magic line. 
            Debug.Log(" ");
            Debug.Log("---------- finishAndRemoveTask : the launcher kill itself");
            Debug.Log(" ");
            mCurrentActivity.Call("finishAndRemoveTask");
        }
        else
        {
            Debug.Log(" ");
            Debug.Log("---------- the launcher is still alive when launching an app");
            Debug.Log(" ");
        }
            
        packageManager.Dispose();
        launchIntent.Dispose();
    }

    private void StopOpenXR()
    {
        XRManagerSettings manager = XRGeneralSettings.Instance.Manager;

        if (manager.activeLoader == null)
            return;

        manager.DeinitializeLoader();
    }

    private void StartOpenXR()
    {
        var manager = XRGeneralSettings.Instance.Manager;

        if (manager.activeLoader != null)
            return;

        manager.InitializeLoaderSync();
        manager.StartSubsystems();
    }


    public void LaunchSVRApp(string packageName)
    {
        Debug.Log("------------- Launch SVR app Called with package name : " + packageName);

        Debug.Log("---------- 1/ Stop Open XR Runtime ");
        StopOpenXR();

        Debug.Log("---------- 2/ Launch effectively the SVR app");
        // call from CSharp :
        bool fail = false;

        AndroidJavaObject packageManager = mCurrentActivity.Call<AndroidJavaObject>("getPackageManager");
        AndroidJavaObject launchIntent = null;

        try
        {
            launchIntent = packageManager.Call<AndroidJavaObject>("getLaunchIntentForPackage", packageName);
        }
        catch (System.Exception e)
        {
            fail = true;
            Debug.LogError("Exception in AndroidComMngExtended::LaunchSVRApp" + e.ToString());
        }

        if (fail)
        {  //open app in store
            Application.OpenURL("https://lynx-r.com/");
        }
        else //open the app
            mCurrentActivity.Call("startActivity", launchIntent);


        Debug.Log("----------3/ call finishAndRemoveTask : Launcher will be killed");
        mCurrentActivity.Call("finishAndRemoveTask");

        packageManager.Dispose();
        launchIntent.Dispose();
    }

    public void LaunchAppInVirtualDisplay(string packageName)
    {

        Debug.Log("------------- LaunchAppInVirtualDisplay called ");

        Debug.Log("---------- 1/ Stop Open XR Runtime ");
        StopOpenXR();

        Debug.Log("---------- 2/ Launch Virtual display on this app : " + packageName);
        mAndroidSystemComPlugIn.CallStatic("launchAppInVirtualDisplay", mApplicationContext, packageName);
        Debug.Log("----------3/ call finishAndRemoveTask : Launcher will be killed");
        mCurrentActivity.Call("finishAndRemoveTask");
    }

    public void KillAppInBackground(string packageName)
    {
        Debug.Log("------------- Call KillAppInBackground ");
        mAndroidSystemComPlugIn.CallStatic("KillApplication", packageName, mApplicationContext);
    }

    public void forceStopAppInBackground(string packageName)
    {
        Debug.Log("------------- Call forceStopAppInBackground ");
        //forceKillAppFromPackageName(Activity currentActivity, String packageName)
        mAndroidSystemComPlugIn.CallStatic("forceKillAppFromPackageName", mCurrentActivity, packageName);
    }

    public void DeleteApp(string packageName, string appName)
    {
        // store information
        mCurrentApplicationToDelete = appName;
        mCurrentPackageToDelete     = packageName;

        mAndroidSystemComPlugIn.CallStatic("deletePackage", mCurrentActivity, packageName);
    }
    
    #endregion
    
    #region LaunchHome
    public void LaunchHome()
    {
        Debug.Log("------------ In Launch Home ");

        bool fail = false;

        AndroidJavaClass  up = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        AndroidJavaObject ca = up.GetStatic<AndroidJavaObject>("currentActivity");
       
        AndroidJavaObject launchIntent  = null;
        AndroidJavaObject launchIntent2 = null;

        try
        {
            launchIntent = new AndroidJavaObject("android.content.Intent", "android.intent.action.MAIN");
 
            // flags not necessary.
            //launchIntent.Call<AndroidJavaObject>("addFlags", 268435456);
            //launchIntent.Call<AndroidJavaObject>("addFlags", 2097152);
            launchIntent2 = launchIntent.Call<AndroidJavaObject>("addCategory", "android.intent.category.HOME");

            // equivalent java code :
            //Intent i = new Intent(Intent.ACTION_MAIN);
            //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            //i.addCategory(Intent.CATEGORY_HOME);
        }
        catch (System.Exception e)
        {
            Debug.Log("------------ System.Exception e =  " + e);
            fail = true;
        }

        if (fail)
        {  //open app in store
            Application.OpenURL("https://lynx-r.com/");
        }
        else //open the app
            ca.Call("startActivity", launchIntent2);

        up.Dispose();
        ca.Dispose();
        launchIntent.Dispose();
    }
    public void RestartApplication()
    {
        mAndroidSystemComPlugIn.CallStatic("RestartApp", mApplicationContext);
    }
    #endregion


    #region Wifi_Mng
    public List<string> GetAllWifiAvailable()
    {
        AndroidJavaObject resutList = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("GetAllWifiAvailable", mApplicationContext, mCurrentActivity);
        int count = resutList.Call<int>("size");

        //Debug.Log("-------------Wifi Device count: " + count);

        List<string> wifiList = new List<string>();

        for (int i = 0; i < count; i++)
        {
            string wifiname = resutList.Call<string>("get", i);

            if (wifiname != null && wifiname.Length > 0)
            {
                wifiList.Add(wifiname);
            }
            else
            {
                Debug.Log("------------- wifiname == null in GetAllWifiAvailable");
            }
        }

        return wifiList;
    }
    public List<WifiData> GetAllAvailableWifiInfo()
    {
        mWifiDataList.Clear();

        AndroidJavaObject resutList = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("GetAllAvailableWifiInfo", mApplicationContext, mCurrentActivity);
        int count = resutList.Call<int>("size");

        //Debug.Log("-------------Wifi Device count: " + count);

        for (int i = 0; i < count; i++)
        {
            AndroidJavaObject wifiInfo = resutList.Call<AndroidJavaObject>("get", i);

            string ssid = wifiInfo.Get<string>("ssid");

            if (ssid != null && ssid.Length > 0)
            {
                WifiData wifiData = new WifiData();

                wifiData.ssid = ssid;
                wifiData.level = wifiInfo.Get<int>("level");
                wifiData.security = wifiInfo.Get<int>("security");            

                mWifiDataList.Add(wifiData);
            }
            else
            {
                //Debug.LogWarning("------------- wifiname == null in GetAllAvailableWifiInfo()");
            } 
        }

        return mWifiDataList;
    }
    public WifiData GetCurrentWifiInfo()
    {
        AndroidJavaObject wifiInfo = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("GetCurrentWifiInfo", mApplicationContext, mCurrentActivity);

        string ssid = wifiInfo.Get<string>("ssid");
        ssid = ssid.Trim('"'); // cedric : curiously the ssid is given with " by java plugin !

        WifiData wifiData = new WifiData();

        wifiData.ssid  = ssid;
        wifiData.level = wifiInfo.Get<int>("level");
        wifiData.security = wifiInfo.Get<int>("security");
        
        return wifiData;
    }
    public string GetCurrentWifiSSID()
    {
        string ssid = null;
        string strTemp = mAndroidSystemComPlugIn.CallStatic<string>("GetCurrentSSID", mApplicationContext, mCurrentActivity);

        if (strTemp != null)
        {
            ssid = strTemp.Trim('"'); // cedric : curiously the ssid is given with " by java plugin !
        }

        return ssid;
    }

    public int GetNetworkInfo()
    {
        if (mAndroidSystemComPlugIn == null)
        {
            return -1;
        }

        return mAndroidSystemComPlugIn.CallStatic<int>("getNetworkInfo", mApplicationContext);
    }

    public bool isConnectedToNetwork()
    {
        int value = GetNetworkInfo();
        bool bRet = false;

        switch (value)
        {
            case 0:
            case 2:
                //Debug.Log("Not Connected to network case A");
                bRet = false;
                break;
            case 1:
            case 3:
                //Debug.Log("CONNECTED to wifi");
                bRet = true;
                break;
            default:
                Debug.LogWarning("Not Connected to network default case");
                bRet = false;
                break;
        }

        return bRet;
    }

    public void EnableWifi(bool value)
    {
        mAndroidSystemComPlugIn.CallStatic("enableWifi", mApplicationContext, value);
    }
    public void RequestWifiConnection(string ssid, string password)
    {
        mAndroidSystemComPlugIn.CallStatic("requestWIFIConnection", mApplicationContext, mCurrentActivity, ssid, password); 
    }
    #endregion Wifi_Mng

    #region Bluetooth_Mng
    public void EnableBluetooth(bool value)
    {
        mAndroidSystemComPlugIn.CallStatic("enableBluetooth", value);
    }
    public void LaunchBluetoothSurroundingDevicesSearch()
    {
        mAndroidSystemComPlugIn.CallStatic("launchBluetoothSurroundingDevicesSearch", mApplicationContext);
    }
    public void PairDevice(string deviceName)
    {
        Debug.Log("AndroidComMngExtended PairDevice called");
        mAndroidSystemComPlugIn.CallStatic("pairDevice", deviceName, mApplicationContext);
    }
    public void UnpairDevice(string deviceName)
    {
        Debug.Log("AndroidComMngExtended UnpairDevice called");
        mAndroidSystemComPlugIn.CallStatic("unpairDevice", deviceName, mApplicationContext);
    }

    public int GetBluetoothInfo()
    {
        if (mAndroidSystemComPlugIn == null)
        {
            return -1;
        }

        return mAndroidSystemComPlugIn.CallStatic<int>("getBluetoothInfo");
    }
    public string GetBluetoothDeviceName()
    {
        return mAndroidSystemComPlugIn.CallStatic<string>("getDeviceName");
    }
    public List<BluetoothData> GetBluetoothPairedDevices()
    {
            mBluetoothDataList.Clear();

            AndroidJavaObject resutList = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("getBluetoothPairedDevices");
            int count = resutList.Call<int>("size");

            Debug.Log("-------------BluetoothData Paired Device count: " + count);

            for (int i = 0; i<count; i++)
            {
                AndroidJavaObject bluetoothInfo = resutList.Call<AndroidJavaObject>("get", i);

                string name = bluetoothInfo.Get<string>("name");

                if (name != null && name.Length > 0)
                {
                    BluetoothData bluetoothData = new BluetoothData();

                    bluetoothData.name = name;
                    bluetoothData.deviceType = bluetoothInfo.Get<int>("type");

                    mBluetoothDataList.Add(bluetoothData);
                }
                else
                {
                    Debug.LogWarning("------------- bluetooth name == null");
                } 
            }

            return mBluetoothDataList;
    }
    public List<BluetoothData> getBluetoothSurroundingDevices()
    {
        mBluetoothDataList2.Clear();

        AndroidJavaObject resutList = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("getBluetoothSurroundingDevices", mApplicationContext);
        int count = resutList.Call<int>("size");

        Debug.Log("-------------BluetoothData Surrounding Device count: " + count);

        for (int i = 0; i < count; i++)
        {
            AndroidJavaObject bluetoothInfo = resutList.Call<AndroidJavaObject>("get", i);

            string name = bluetoothInfo.Get<string>("name");

            if (name != null && name.Length > 0)
            {
                BluetoothData bluetoothData = new BluetoothData();

                bluetoothData.name = name;
                bluetoothData.deviceType = bluetoothInfo.Get<int>("type");

                mBluetoothDataList2.Add(bluetoothData);
            }
            else
            {
                Debug.LogWarning("------------- bluetooth name == null");
            }
        }

        return mBluetoothDataList2;
    }
    public List<string> getBluetoothSurroundingDevicesNames()
    {
        AndroidJavaObject resutList = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("getBluetoothSurroundingDevices", mApplicationContext);

        int count = resutList.Call<int>("size");

        Debug.Log("-------------Bluetooth Surrounding Devices count: " + count);

        List<string> deviceNames = new List<string>();

        for (int i = 0; i < count; i++)
        {
            string deviceName = resutList.Call<string>("get", i);
            deviceNames.Add(deviceName);
        }

        return deviceNames;
    }//Not used

    public void SetDeviceName(string deviceName)
    {
        mAndroidSystemComPlugIn.CallStatic("setDeviceName", deviceName);
    }
    #endregion Bluetooth_Mng

    #region Battery_Mng
    public int GetBatteryLevel()
    {
        if (mAndroidSystemComPlugIn == null)
        {
            return 0;
        }

        return mAndroidSystemComPlugIn.CallStatic<int>("getBatteryPercentage", mApplicationContext);
    }

    public bool IsBatteryCharging()
    {
        if (mAndroidSystemComPlugIn == null)
        {
            return false;
        }

        int value = mAndroidSystemComPlugIn.CallStatic<int>("isBatteryCharging", mApplicationContext);

        return (value > 0);
    }
    #endregion Battery_Mng

    #region Device_Audio_Mng
    public bool isMicrophoneMute()
    {
        return mAndroidSystemComPlugIn.CallStatic<bool>("isMicrophoneMute", mApplicationContext);
    }

    public int GetAudioVolume()
    {
        if (mAndroidSystemComPlugIn == null) return 0;

        return mAndroidSystemComPlugIn.CallStatic<int>("getAudioVolume", mApplicationContext);
    }
    public int GetMaxAudioVolume()
    {
        return mAndroidSystemComPlugIn.CallStatic<int>("getMaxAudioVolume", mApplicationContext);
    }

    public void SetAudioVolume(int volume) // volume is 0 to 15. (integer)
    {
        if (volume < 0 || volume > 15)
        {
            Debug.LogWarning("Try to set an audio volume not between 0 to 15, setAudioVolume will be not called");
            return;
        }

        mAndroidSystemComPlugIn.CallStatic("setAudioVolume", mApplicationContext, volume);
        mAudioVolumeChangedbySoftware = true;
    }

    public void SetMicrophoneMute(bool mute)
    {
        mAndroidSystemComPlugIn.CallStatic("setMicrophoneMute", mApplicationContext, mute);
    }


    public void RegisterVolumeChangeReceiver()
    {
        mAndroidSystemComPlugIn.CallStatic("RegisterVolumeChangeReceiver", mApplicationContext);
    }


    public void UnregisterVolumeChangeReceiver()
    {
        mAndroidSystemComPlugIn.CallStatic("UnregisterVolumeChangeReceiver", mApplicationContext);
    }

   


	// touch.





    #endregion Device_Audio_Mng

    #region Screen Brightness Mng
    public int GetBrightness()
    {
        return mAndroidSystemComPlugIn.CallStatic<int>("getBrightness", mApplicationContext);
    }

    public void SetBrightness(int brightness)
    {
        mAndroidSystemComPlugIn.CallStatic("setBrightness", mApplicationContext, brightness);
    }
    #endregion
    
    #region Device_Storage_Info
    public float GetTotalRAMInGB()
    {
        float  ramInGB = mAndroidSystemComPlugIn.CallStatic<float>("GetTotalRAMInGB", mApplicationContext);
        return ramInGB;
    }

    public float GetTotalInternalStorageInGB()
    {
        float totalInternalStorageInGB = mAndroidSystemComPlugIn.CallStatic<float>("GetTotalInternalStorageInGB");
        return totalInternalStorageInGB;
    }

    public float GetFreeInternalStorageinGB()
    {
        float freeInternalStorageinGB = mAndroidSystemComPlugIn.CallStatic<float>("GetFreeInternalStorageinGB");
        return freeInternalStorageinGB;
    }
    #endregion Device_Storage_Info


    #region Device_Time_TimeZone
 
    public List<string> GetTimeZoneReadableList()
    {
        AndroidJavaObject resutList = mAndroidSystemComPlugIn.CallStatic<AndroidJavaObject>("getTimeZoneReadableList");
        int count = resutList.Call<int>("size");

        List<string> timeZoneList = new List<string>(); // pas terrible. 

        for (int i = 0; i < count; i++)
        {
            string str = resutList.Call<string>("get", i);

            if (str != null && str.Length > 0)
            {
                timeZoneList.Add(str);
            }           
        }

        return timeZoneList;
    }

    public void SetTimeZone(string timeZone)
    {
        mAndroidSystemComPlugIn.CallStatic("setTimeZone", mApplicationContext, timeZone);
    }

    public string GetCurrentTimeZone()
    {
        string timeZone = mAndroidSystemComPlugIn.CallStatic<string>("getCurrentTimeZone");
        Debug.Log("Current timezone : " + timeZone);

        return timeZone;
    }

    public string GetReadableTimeZoneFromNormalizedTimeZone(string readableTimeZone)
    {
        string value = mAndroidSystemComPlugIn.CallStatic<string>("getReadableTimeZoneFromNormalizedTimeZone", readableTimeZone);
        return value;
    }

    #endregion Device_Time_TimeZone


    #region Ultraleap_Analytics

    public void GetUltraLeapAnalyticsStateTest()
    {
        GetUltraLeapAnalyticsState();
    }

    public bool GetUltraLeapAnalyticsState()
    {
        bool value=mAndroidSystemComPlugIn.CallStatic<bool>("getUltraleapAnalyticsState", mApplicationContext);
        Debug.Log("GetUltraLeapAnalyticsState() returns : " + value);
        return value; 
    }

    public void SetUltraleapAnalyticsState(bool value)
    {
        Debug.Log("SetUltraleapAnalyticsState() with value : " + value);
        mAndroidSystemComPlugIn.CallStatic("setUltraleapAnalyticsState", mApplicationContext, value);   
    }

    #endregion Ultraleap_Analytics

    // Just for fast tests
    public void ButtonTestBisClicked()
    {
        Debug.Log("ButtonTestBisClicked()");

        AndroidJavaClass up = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        AndroidJavaObject ca = up.GetStatic<AndroidJavaObject>("currentActivity");
        AndroidJavaObject context = ca.Call<AndroidJavaObject>("getApplicationContext");

        var plugin = new AndroidJavaClass(LynxAndroidSystemComPlugInName);

        if (plugin != null)
        {
            //plugin.CallStatic("enableLocationPermission", context, ca);
            plugin.CallStatic("getBluetoothSurroundingDevices", context);
            //string name = plugin.CallStatic<string>("getDeviceName", context);

            //Debug.Log("name : " + name);
        }
        else
        {
            Debug.LogWarning("Plugin " + LynxAndroidSystemComPlugInName + " not present");
        }
    }
    public void SetTimeAndDateTest()
    {
        AndroidJavaClass up = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        AndroidJavaObject ca = up.GetStatic<AndroidJavaObject>("currentActivity");
        AndroidJavaObject context = ca.Call<AndroidJavaObject>("getApplicationContext");

        try
        {
            var plugin = new AndroidJavaClass(LynxAndroidSystemComPlugInName);

            if (plugin != null)
            {
                plugin.CallStatic("SetTimeAndDateTest", context);
            }
            else
            {
                Debug.LogWarning("Plugin " + LynxAndroidSystemComPlugInName + " not present");
            }
        }
        catch (System.Exception e)
        {
            Debug.LogError(e, this);
        }
    }

    // Not called but can be used later
    public void justForTest()
    {
        mAndroidSystemComPlugIn.CallStatic("askLauncherApp", mApplicationContext);
    }
    public void tryLaunchingLynxOnBoarding()
    {
        Assert.AreNotEqual(null, mAndroidSystemComPlugIn);
        Assert.AreNotEqual(null, mApplicationContext);

        string OnBoardingPackageName = "com.Lynx.TestOtherAppStart";

        mAndroidSystemComPlugIn.CallStatic("launchOnceOnBoarding", mApplicationContext, OnBoardingPackageName);
    }
    public bool readAllApplicationsPermissions()
    {
        if (mAndroidSystemComPlugIn.CallStatic<bool>("readAllApplicationPermissions", mApplicationContext))
            return true;

        return false;
    }

}
