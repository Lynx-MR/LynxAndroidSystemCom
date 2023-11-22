/**
 * SettingsContentObserver gives information about audio volume on the device.
 *
 * @author      Cédric Morel Francoz
 * @since       1.0
 */

package com.lynx.lynxandroidsystemcom;


import android.database.ContentObserver;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

public class SettingsContentObserver extends ContentObserver {

    /** Android audio manager object */
    private AudioManager mAudioManager;

    public SettingsContentObserver(Context context, Handler handler) {
        super(handler);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public boolean deliverSelfNotifications() {
        return false;
    }

    @Override
    public void onChange(boolean selfChange)
    {
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.d("LynxAndroidSystem", "Volume now " + currentVolume);
        UnityPlayer.UnitySendMessage(LynxAndroidSystemComMng.mUnityGameObjectForCallback, "AudioVolumeChange", Integer.toString(currentVolume));
    }
}


