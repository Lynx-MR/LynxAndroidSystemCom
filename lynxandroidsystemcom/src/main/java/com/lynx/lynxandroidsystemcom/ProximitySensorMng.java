/**
 * ProximitySensorMng gives information about the proximity sensor of the device.
 *
 * @author      Cédric Morel Francoz
 * @since       1.0
 */

package com.lynx.lynxandroidsystemcom;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

public class ProximitySensorMng implements SensorEventListener {

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (sensorEvent.values[0] == 0) {
                // here we are setting our status to our textview..
                // if sensor event return 0 then object is closed
                // to sensor else object is away from sensor.

                Log.d("LynxAndroidSystem", "Proximity sensor Near");

            } else {

                Log.d("LynxAndroidSystem", "Proximity sensor Away");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}
}


