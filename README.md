# LynxAndroidSystemCom

It's an android java library (.jar) that allows to communicate with the android system installed on lynx headset.
It is a bridge between the system settings and a high level application, typically a Unity or a Unreal based application, to have
access and change settings of the Lynx headset. (access to wifi, bluetooth, to the list of application installed on the device,
level of battery, level of audio volume ...). 

### To generate the library : LynxAndroidSystemCom.jar
Open the project with a recent Android Studio version.
Choose the project configuration lynxAndroidSystemCom and the release build version. 
Build the project (Make project). 
To get the generated .jar, go to in the root folder and execute GetLynxLib.bat. 
The library will be recopied in the GeneratedLynxLib folder, with the name LynxAndroidSystemCom.jar. 

### Structure of the library
All public functions are in the LynxAndroidSystemCom.java file. 
There are static and can be called from a Unity or an Unreal project. 
exemple : 
/**
* Enable or disable Wifi Network
* it's important to notice that the calling application needs to be a system app
* for that this function works.
* @param  context the android context of the calling application
* @param  enable  enable or not wifi
*/
public static void enableWifi(Context context, boolean enable)


/**
 * Delete a package on a android device.
 *
 * @param  currentActivity the android activity of the calling application
 * @param  packageName     the package name of the application (for exemple : com.lynx.MyAplication)
*/
public static void deletePackage(Activity currentActivity, String packageName)

Note that Android Context and Android activity are often used as argument of these functions and are given by the calling application.
 
