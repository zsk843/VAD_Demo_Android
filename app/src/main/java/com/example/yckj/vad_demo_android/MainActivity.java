package com.example.yckj.vad_demo_android;


import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;


import java.util.concurrent.locks.ReentrantLock;


public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("native-lib");
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 100;
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
    private float sum;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);

        GammaTone gt = new GammaTone();
        gt.initialize();

        float[] res = {1};


        int repeat_time = 1000;
        int time_len = 400;
        short[] test_data = new short[time_len];
        for (int i = 0; i < time_len;i++)
            test_data[i] = (short)i;
//        long start = System.currentTimeMillis();
//        float iii = 0;
//        for(int i = 0; i < repeat_time;i++) {
        gt.SetData(test_data, null);
        res = gt.GetFeature();

//        }
//        long end = System.currentTimeMillis();



        Log.d("MainAcivity", Float.toString((end-start)/(float)repeat_time));
        Log.d("MainAcivity", Float.toString(res[0]));

        //Log.d(TAG, "Time is "+Float.toString((float)(endtime-startime)/repeat_time));


//        int size = 0;
//
//        BigDecimal[] inputs = null;
//        try {
//
//            InputStream is = getAssets().open("feature.data");
//            int length = is.available();
//            byte[]  buffer = new byte[length];
//            is.read(buffer);
//            String result = new String(buffer);
//
//            String[] lines = result.split("\n");
//            String line1 = lines[0];
//            String line2 = lines[1];
//            size = Integer.parseInt(line1.split(" ")[0]);
//
//            String[] line_lst = line2.split(" ");
//            inputs = new BigDecimal[line_lst.length];
//            for(int i = 0; i < line_lst.length; i++){
//                inputs[i] = new BigDecimal(line_lst[i]);
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        assert inputs != null;
//
//        float[] outputScores = new float[size*2];
//
//
//        BigDecimal[] means = new BigDecimal[64];
//        BigDecimal[] std = new BigDecimal[64];
//        BigDecimal size_Deicimal = new BigDecimal(size);
//        for(int i = 0; i < 64; i++){
//            BigDecimal sum = new BigDecimal(0);
//            for(int j = 0; j < size; j++){
//                sum = sum.add(inputs[j*64+i]);
//            }
//            means[i] = sum.divide(size_Deicimal,BigDecimal.ROUND_HALF_UP);
//            BigDecimal square_sum = new BigDecimal(0);
//            for(int j = 0; j < size; j++){
//                BigDecimal reduction = inputs[j*64+i].subtract( means[i]);
//                square_sum = square_sum.add(reduction.multiply(reduction));
//            }
//            std[i] = new BigDecimal(Math.sqrt(square_sum.divide(size_Deicimal,BigDecimal.ROUND_HALF_UP).doubleValue()));
//        }
//
//        float[] tmp_array = new float[size*64];
//        for(int i = 0; i < 64; i++)
//        {
//            for(int j = 0; j < size; j++){
//                tmp_array[j*64 + i] = (inputs[j*64+i].subtract(means[i])).divide(std[i], BigDecimal.ROUND_HALF_UP).floatValue();
//            }
//        }
//
//        float[] pro = {1.0f};
//        TensorFlowInferenceInterface inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);
//        inferenceInterface.feed(INPUT_DATA_NAME,tmp_array, size, 64);
//        inferenceInterface.feed("keep_prob", pro, 1);
//        inferenceInterface.run(outputScoresNames);
//        inferenceInterface.fetch(outputScoresNames[0], outputScores);
//
//        int[] labels = new int[size];
//        for(int i = 0; i < size; i++){
//            if(outputScores[i*2]> outputScores[i*2+1]){
//                labels[i] = 0;
//            }
//            else{
//                labels[i] = 1;
//            }
//        }
//        System.out.print("Done");
        textView = findViewById(R.id.res_text_view);

        requestMicrophonePermission();
        startRecording();
        startRecognition();

    }

    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
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
            startRecording();
            startRecognition();
        }
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    private void recognize() {

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[] floatInputBuffer = new float[RECORDING_LENGTH];

        while (shouldContinueRecognition) {
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                int firstCopyLength = maxLength - recordingOffset;
                int secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.

            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                floatInputBuffer[i] = inputBuffer[i];

            }

            long v = 0;
            for (int i = 0; i < floatInputBuffer.length; i++) {
                v += floatInputBuffer[i] * floatInputBuffer[i];
            }

            float mean = v / (float) floatInputBuffer.length;
            float volume = (float)(10 * Math.log10(mean));
            if(volume> 60){
                sum = 1.0f;
            }
            else {
                sum =0.0f;
            }
            runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            // If we do have a new command, highlight the right list entry.
                            textView.setText(Float.toString(sum));
                        }
                    });
            try {
                // We don't need to run too frequently, so snooze for a bit.
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

    }

    public native float[] stringFromJNI();

}
