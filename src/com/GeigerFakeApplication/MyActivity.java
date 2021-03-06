package com.GeigerFakeApplication;

import java.util.*;

import android.hardware.*;
import android.app.Activity;
import android.media.*;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

public class MyActivity extends Activity implements View.OnClickListener {

    //snap settings
    private final int trackDuration = 500;             //msec
    private final int snapDuration = 20;          //msec
    private final int sampleRate = 8000;          //frame per second
    private final int numSamples = trackDuration * sampleRate/1000;
    private final double freqOfTone = 3600;       //frequency of snap in hz
    private final Random rand = new Random();
    private volatile boolean doNoise = true;//do snap noise or not

    //visualization
    TextView digitView;
    ImageView needleView;           //needle of analog display
    ImageView soundStateView;

    //timer of update
    Timer updateTimer;              //updates all data
    private final int updatePeriod = 50;

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
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(listener);
        updateTimer.cancel();
        disableNoise();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        needleView = (ImageView) findViewById(R.id.needleView);
        digitView = (TextView) findViewById(R.id.digitView);
        soundStateView = (ImageView) findViewById(R.id.soundStateView);

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

        enableNoise();
    }

    private byte[] generateSnapAudioTrack(){

        byte generatedSnd[] = new byte[numSamples*2];
        double sample[] = new double[numSamples];


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
        return generatedSnd;
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

    @Override
    public void onClick(View v){
        if(!doNoise){
            soundStateView.setBackgroundResource(R.drawable.sound_on_icon);
            enableNoise();
        }
        else{
            soundStateView.setBackgroundResource(R.drawable.sound_off_icon);
            disableNoise();
        }
    }

    void enableNoise(){
        Thread noisePlayer = new Thread(new Runnable() {
            @Override
            public void run() {
                while(doNoise) {

                    final byte[] generatedSnd = generateSnapAudioTrack();

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

    void disableNoise(){
        doNoise = false;
    }

}
