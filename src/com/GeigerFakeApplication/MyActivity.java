package com.GeigerFakeApplication;

import java.util.*;

import android.hardware.*;
import android.app.Activity;
import android.media.*;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.*;

public class MyActivity extends Activity {

    //snap settings

    Handler handler = new Handler();
    Random rand = new Random();
    volatile boolean doNoise = true;

    volatile boolean isFinished = true;

    //visualization
    TextView digitView;
    ImageView needleView;           //needle of analog display

    //update timer
    Timer updateTimer;              //updates all data
    int updatePeriod = 50;

    //radiation data
    SensorManager sensorManager;
    Sensor sensorAcceleration;
    private volatile float radiation = (float)-0.01;//current radiation level
    private float valueAccelerationZ = 0; //Acceleration by Z dimension used for Geiger PseudoCounter

    SensorEventListener listener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                valueAccelerationZ = event.values[2]; //get Z acceleration
                radiation = updateRadiationValue();
            }

        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        needleView = (ImageView) findViewById(R.id.needleView);

        digitView = (TextView) findViewById(R.id.digitView);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(listener, sensorAcceleration, SensorManager.SENSOR_DELAY_NORMAL);

        //this timer updates all info
        updateTimer = new Timer();
        TimerTask updateInfoTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        digitView.setText(String.format(" %.2f ", radiation));
                        needleView.setRotation(Math.round(180 / 6 * radiation));
                    }
                });
            }
        };
        updateTimer.schedule(updateInfoTask, 1000, updatePeriod);

        Thread noisePlayer = new Thread(new Runnable() {
            @Override
            public void run() {
                while(doNoise) {
                    int duration = 500; // msec
                    int snapDuration = 20;
                    int sampleRate = 8000;
                    int numSamples = duration * sampleRate/1000;
                    byte generatedSnd[] = new byte[numSamples*2];
                    double sample[] = new double[numSamples];
                    double freqOfTone = 3600; // hz

                    // fill out the array
                    int snapAmount = (int) ((radiation + 2) * (radiation + 1)) / 6 + rand.nextInt(2) ;
                    for (int i = 0; i < snapAmount; ++i) {
                        int pos = rand.nextInt(numSamples-snapDuration);
                        for(int j = 0; j < snapDuration; j++) {
                            sample[pos+j] = 0.85 * Math.sin(2 * Math.PI * (pos+j) / (sampleRate / freqOfTone));
                        }
                    }

                    // convert to 16 bit pcm sound array
                    // assumes the sample buffer is normalised.
                    int idx = 0;
                    for (final double dVal : sample) {
                        // scale to maximum amplitude
                        final short val = (short) ((dVal * 32767));
                        // in 16 bit wav PCM, first byte is the low order byte
                        generatedSnd[idx++] = (byte) (val & 0x00ff);
                        generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

                    }

                    AudioTrack snapsTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                            sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                            AudioTrack.MODE_STATIC);
                    snapsTrack.write(generatedSnd, 0, generatedSnd.length);

                    snapsTrack.play();

                    /* Wait while track is playing.
                     * Strange way coz onMarkerReached is useless
                     * (not work in android 4.4.2 and some else versions, strange bag).
                     */
                    while((snapsTrack.getPlaybackHeadPosition() < numSamples) && doNoise){

                        Log.d("snapPlayer", "waiting..." +snapsTrack.getPlaybackHeadPosition() );
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    snapsTrack.release();
                }
            }
        });

        doNoise = true;
        noisePlayer.start();

    }






    private float updateRadiationValue(){
        //perform radiation value in pseudo mR/h using acceleration
        float newRadiation = (valueAccelerationZ > 0)? valueAccelerationZ/2 : 0;

        for(int i = 1; i <= 4; i++){
            newRadiation /= (newRadiation/i < 1) ? 1.5 : 0.99; //for small values prevalence
        }

        //if radiation < 0 then its first value of radiation or radiation = 0
        if (radiation > 0) {
            newRadiation = (newRadiation + 3*radiation) / 4; //smoothing
        }

        return newRadiation > 6 ? 6 : newRadiation;  //6 is max value of this Geiger pseudoCounter
    }

}
