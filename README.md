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
These are statics and can be called from a Unity or an Unreal project. 
exemples : 

*public static void enableWifi(Context context, boolean enable)*

*#public static void deletePackage(Activity currentActivity, String packageName)*

Note that **Android Context** and **Android activity** are often used as argument of these functions 
and are given by the calling application.
 
