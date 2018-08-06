package com.example.yckj.vad_demo_android;


import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;


import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;


public class MainActivity extends AppCompatActivity {

//    static {
//        System.loadLibrary("native-lib");
//    }

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 315;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);

    // UI elements.
    private static final int REQUEST_RECORD_AUDIO = 13;

    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    private TensorFlowInferenceInterface inferenceInterface;
    private String MODEL_FILENAME = "file:///android_asset/Model.pb";
    private String INPUT_DATA_NAME = "input";
    private String[] outputScoresNames = {"output/Softmax"};
    private TextView textView;
    private GammaTone gt;
    private int sum;
    private float[] mean;
    private float[] std;
    private int NUM_FILTERS = 64;
    private Button btn;
    private FloatingActionButton fab;
    private int[] red_color = {0xffff0000};


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.res_text_view);
        btn = findViewById(R.id.act_btn);
        fab = findViewById(R.id.led);

        gt = new GammaTone();
        gt.initialize();
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);

       requestMicrophonePermission();
        // startRecording();
        // startRecognition();

    }

    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    public void on_act(View view){
        mean = new float[64];
        std = new float[64];
        for(int i = 0; i < 64;i++)
        {
            mean[i] = 0;
            std[i] = 1;
        }

        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        // If we do have a new command, highlight the right list entry.
                        textView.setText("正在激活");
                    }
                });
        startRecording();
        view.setClickable(false);
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];


        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            return;
        }

        short[] act_buffer = new short[48240];
        short[] act_buffer_zero = new short[400];
        float[] act_tem = new float[NUM_FILTERS*300];
        record.startRecording();
        record.read(act_buffer_zero,0,act_buffer_zero.length);
        record.read(act_buffer,0,act_buffer.length);
        record.stop();

        for(int buff_i = 0; buff_i < 10; buff_i++){
            short[] act_tmp_buffer = new short[5040];
            for(int i = 0;i < 5040;i++)
                act_tmp_buffer[i] = act_buffer[i+(160*30*buff_i)];

            float[] res;
            gt.SetData(act_tmp_buffer,null);
            res = gt.GetFeature();

            for(int i = 0; i < res.length;i++)
                act_tem[64*30*buff_i+i] = res[i];
        }


        for (int j = 0; j < 64; j++) {
            for (int k = 0; k < 300; k++) {
                mean[j] += act_tem[k * 64 + j];
            }
            mean[j] = mean[j] / 300;

            for (int k = 0; k < 300; k++) {
                std[j] += Math.pow(act_tem[k * 64 + j] - mean[j], 2);
            }
            std[j] = (float) Math.sqrt(std[j] / 300);
        }
        gt.mean = Arrays.copyOf(mean, 64);
        gt.std = Arrays.copyOf(std, 64);
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        // If we do have a new command, highlight the right list entry.
                        textView.setText("已激活");
                    }
                });
        record.startRecording();

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;

            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;
            } finally {
                recordingBufferLock.unlock();
            }


            short[] inputBuffer = new short[RECORDING_LENGTH];
            short[] shortInputBuffer = new short[RECORDING_LENGTH];

            recordingBufferLock.lock();
            try {
                maxLength = recordingBuffer.length;
                firstCopyLength = maxLength - recordingOffset;
                secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }

            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                shortInputBuffer[i] = inputBuffer[i];
            }

            float[] res = {1};

            gt.SetData(shortInputBuffer, null);
            try {
                res = gt.GetFeature();
            }catch (Exception e)
            {
                e.printStackTrace();
            }


                inferenceInterface.feed(INPUT_DATA_NAME, res, gt.GetDim());
                inferenceInterface.feed("keep_prob", new float[]{1.0f}, 1);
                inferenceInterface.run(outputScoresNames);
                float[] label = new float[(int) (2 * (gt.GetDim()[0] - 10))];
                inferenceInterface.fetch(outputScoresNames[0], label);

                float sum_1 = 0;
                String output = "";
                for (int i = 0; i < label.length / 2; i++) {
                    if (label[i * 2] < label[i * 2 + 1]) {
                        sum_1 += 1;
                        output += "| 1 |";
                    } else
                        output += "| 0 |";
                }
                Log.d("MainActivity", output);

                if (sum_1 > label.length / 4)
                    sum = 1;
                else
                    sum = 0;
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
//                                textView.setText(Float.toString(sum));
                                if(sum == 1)
                                    fab.setBackgroundTintList(ColorStateList.valueOf(0xffff0000));
                                else
                                    fab.setBackgroundTintList(ColorStateList.valueOf(0x303f9f));
                            }
                        });
            }
        record.stop();
        record.release();
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        }
    }

}
