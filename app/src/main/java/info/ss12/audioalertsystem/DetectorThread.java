/*
 * Copyright (C) 2012 Jacquet Wong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * musicg api in Google Code: http://code.google.com/p/musicg/
 * Android Application in Google Play: https://play.google.com/store/apps/details?id=com.whistleapp
 * 
 */

package info.ss12.audioalertsystem;

import android.content.Context;
import android.preference.PreferenceManager;
import com.musicg.api.ClapApi;
import info.ss12.audioalertsystem.alert.AudioAlert;

import java.util.LinkedList;

import android.media.AudioFormat;
import android.media.AudioRecord;

import com.musicg.api.WhistleApi;
import com.musicg.wave.WaveHeader;

/**
 * The DetectorThread class runs the Recorder processes
 */
public class DetectorThread extends Thread {

    /**
     * The recorder thread
     */
    private RecorderThread recorder;

    /**
     * The Wave Header. Used to set wave configurations
     */
    private WaveHeader waveHeader;
    /**
     * The Whistle API. Detect frequency
     */
    private WhistleApi whistleApi;
    private ClapApi clapApi;
    /**
     * The thread for detector
     */
    private volatile Thread _thread;

    /**
     * The list for whistle results. Used to detect sound level
     */
    private LinkedList<Boolean> whistleResultList = new LinkedList<Boolean>();
    /**
     * The number of whistles
     */
    private int numWhistles;
    /**
     * Total amount of whistles detected
     */
    private int totalWhistlesDetected = 0;
    /**
     * Length check for whistle length
     */
    private int whistleCheckLength = 3;
    /**
     * The pass score for whistle
     */
    private int whistlePassScore = 3;
    /**
     * Listener to send alerts
     */
    private AudioAlert listener;

    /**
     * The DetectorThread constructor. Used to set recorder and audio alert
     * notification
     *
     * @param recorder the RecorderThread
     * @param listener the AudioAlert
     */
    public DetectorThread(RecorderThread recorder, AudioAlert listener) {
        this.recorder = recorder;
        this.listener = listener;
        AudioRecord audioRecord = recorder.getAudioRecord();

        int bitsPerSample = 0;
        if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) {
            bitsPerSample = 16;
        } else if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_8BIT) {
            bitsPerSample = 8;
        }

        int channel = 1;
        // whistle detection only supports mono channel
        if (audioRecord.getChannelConfiguration() == AudioFormat.CHANNEL_IN_MONO) {
            channel = 1;
        }

        waveHeader = new WaveHeader();
        waveHeader.setChannels(channel);
        waveHeader.setBitsPerSample(bitsPerSample);
        waveHeader.setSampleRate(audioRecord.getSampleRate());
        whistleApi = new WhistleApi(waveHeader);
        clapApi = new ClapApi(waveHeader);
    }

    private boolean whitle = false;
    private boolean isRecording;
    private Context context;
    private OnSignalsDetectedListener onSignalsDetectedListener;

    public DetectorThread(RecorderThread recorder, int i, Context context, OnSignalsDetectedListener onSignalsDetectedListener) {
        this.recorder = recorder;
        this.context = context;
        this.onSignalsDetectedListener = onSignalsDetectedListener;
        if (i == 0) {
            whitle = true;
        } else {
            whitle = false;
        }

        if (whitle) {
            whistleCheckLength = 3;
            whistlePassScore = 3;
        } else {
            whistleCheckLength = 1;
            whistlePassScore = 1;
        }

        AudioRecord audioRecord = recorder.getAudioRecord();

        int bitsPerSample = 0;
        if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) {
            bitsPerSample = 16;
        } else if (audioRecord.getAudioFormat() == AudioFormat.ENCODING_PCM_8BIT) {
            bitsPerSample = 8;
        }

        int channel = 1;
        // whistle detection only supports mono channel
        if (audioRecord.getChannelConfiguration() == AudioFormat.CHANNEL_IN_MONO) {
            channel = 1;
        }

        waveHeader = new WaveHeader();
        waveHeader.setChannels(channel);
        waveHeader.setBitsPerSample(bitsPerSample);
        waveHeader.setSampleRate(audioRecord.getSampleRate());
        whistleApi = new WhistleApi(waveHeader);
        clapApi = new ClapApi(waveHeader);
    }

    /**
     * Initialize whistle api
     */
    private void initBuffer() {
        numWhistles = 0;
        whistleResultList.clear();

        // init the first frames
        for (int i = 0; i < whistleCheckLength; i++) {
            whistleResultList.add(false);
        }
        // end init the first frames
    }

    /**
     * Start the detector thread
     */
    public void start() {
        this.isRecording = true;
        _thread = new Thread(this);
        _thread.start();
    }

    /**
     * Stop the detector thread
     */
    public void stopDetection() {
        this.isRecording = false;
        _thread = null;
    }

    /**
     * The detector thread process
     */
    public void run() {
        while (this.isRecording) {
            try {
                byte[] buffer;
                if (whitle) {
                    whistleCheckLength = 3;
                    whistlePassScore = 3;
                } else {
                    whistleCheckLength = 1;
                    whistlePassScore = 1;
                }
                initBuffer();

                Thread thisThread = Thread.currentThread();
                while (_thread == thisThread) {
                    // detect sound
                    buffer = recorder.getFrameBytes();
                    // audio analyst
                    if (buffer != null) {
                        if (PreferenceManager.getDefaultSharedPreferences(context).contains("TYPDEC")) {
                            setWhitle(PreferenceManager.getDefaultSharedPreferences(context).getInt("TYPDEC", 0) == 0);
                        }
                        // sound detected
                        // MainActivity.whistleValue = numWhistles;
                        if (whitle) {
                            // whistle detection
                            boolean isWhistle = whistleApi.isWhistle(buffer);

                            if (whistleResultList.getFirst()) {
                                numWhistles--;
                            }
                            whistleResultList.removeFirst();
                            this.whistleResultList.add(isWhistle);
                            if (isWhistle) {
                                numWhistles++;
                            }

                        } else {
                            boolean isClap = clapApi.isClap(buffer);
                            if (whistleResultList.getFirst()) {
                                numWhistles--;
                            }
                            whistleResultList.removeFirst();
                            this.whistleResultList.add(isClap);
                            if (isClap) {
                                numWhistles++;
                            }
                        }
                        // System.out.println("num:" + numWhistles);
                        if (numWhistles >= whistlePassScore) {
                            // clear buffer
                            initBuffer();
                            onWhistleDetected();
//                            totalWhistlesDetected++;
//                            listener.sendAlert();
                        }
                        // end whistle detection
                    } else {
                        // no sound detected
                        if (whistleResultList.getFirst()) {
                            numWhistles--;
                        }
                        whistleResultList.removeFirst();
                        whistleResultList.add(false);
//                        listener.dismissAlert();

                        // MainActivity.whistleValue = numWhistles;
                    }
                    // end audio analyst
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return the total whistles detected
     *
     * @return the number of whistles detected
     */
    public int getTotalWhistlesDetected() {
        return totalWhistlesDetected;
    }

    private void onWhistleDetected() {
        if (this.onSignalsDetectedListener != null) {
            this.onSignalsDetectedListener.onWhistleDetected();
        }
    }

    public void setOnSignalsDetectedListener(OnSignalsDetectedListener listener) {
        this.onSignalsDetectedListener = listener;
    }

    public void setWhitle(boolean whitle) {
        this.whitle = whitle;
    }
}