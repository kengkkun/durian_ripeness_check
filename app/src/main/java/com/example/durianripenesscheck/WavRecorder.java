package com.example.durianripenesscheck;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WavRecorder {
    public static final String LOG_TAG = "Recorder_log";
    private AudioRecord recorder = null;

    private static final int RECORDER_BPP = 16; //bits per sample

    private File recordFile = null;
    private int bufferSize = 0;

    private Thread recordingThread;

    private boolean isRecording = false;


    public void prepare(String outputFile, int sampleRate) {
        recordFile = new File(outputFile);
        if (recordFile.exists() && recordFile.isFile()) {
            int channel = AudioFormat.CHANNEL_IN_MONO;
            try {
                bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                        channel,
                        AudioFormat.ENCODING_PCM_16BIT);
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                            channel,
                            AudioFormat.ENCODING_PCM_16BIT);
                }
                recorder = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channel,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );
            } catch (IllegalArgumentException e) {
                Log.e("sampleRate = " + sampleRate + " channel = " + channel + " bufferSize = " + bufferSize, String.valueOf(e));
                if (recorder != null) {
                    recorder.release();
                }
            }
            if (recorder != null && recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording();
            }
        } else {
            Log.e(LOG_TAG, "prepare() failed");

        }
    }


    public void startRecording() {
        if (recorder != null && recorder.getState() == AudioRecord.STATE_INITIALIZED) {
            try {
                recorder.startRecording();
                isRecording = true;
                recordingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        writeAudioDataToFile();
                    }
                }, "AudioRecorder Thread");

                recordingThread.start();


            } catch (IllegalStateException e) {
                Log.e("startRecording() failed", String.valueOf(e));
            }
        }
    }


    public void stopRecording() {
        if (recorder != null) {
            isRecording = false;
            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                try {
                    recorder.stop();
                } catch (IllegalStateException e) {
                    Log.e(LOG_TAG, "Stop recording error");
                }
            }
            recorder.release();
            recordingThread.interrupt();
        }
    }


    private void writeAudioDataToFile() {
        byte[] data = new byte[bufferSize];
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(recordFile);
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, String.valueOf(e));
            fos = null;
        }
        if (null != fos) {
            int chunksCount = 0;
            ByteBuffer shortBuffer = ByteBuffer.allocate(2);
            shortBuffer.order(ByteOrder.LITTLE_ENDIAN);
            //TODO: Disable loop while pause.
            while (isRecording) {
                chunksCount += recorder.read(data, 0, bufferSize);
                if (AudioRecord.ERROR_INVALID_OPERATION != chunksCount) {
                    long sum = 0;
                    for (int i = 0; i < bufferSize; i += 2) {
                        //TODO: find a better way to covert bytes into shorts.
                        shortBuffer.put(data[i]);
                        shortBuffer.put(data[i + 1]);
                        sum += Math.abs(shortBuffer.getShort(0));
                        shortBuffer.clear();
                    }
//                    int lastVal = (int) (sum / (bufferSize / 16));
                    try {
                        fos.write(data);
                    } catch (IOException e) {
                        Log.e(LOG_TAG, String.valueOf(e));
                        recordingThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                stopRecording();
                            }
                        });
                    }
                }
            }


            try {
                fos.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, String.valueOf(e));
            }
            int channelCount = 1;
            setWaveFileHeader(recordFile, channelCount);
        }
    }

    private void setWaveFileHeader(File file, int channels) {
        long fileSize = file.length() - 8;
        long totalSize = fileSize + 36;
        int sampleRate = 8000;
        long byteRate = sampleRate * channels * (RECORDER_BPP / 8); //2 byte per 1 sample for 1 channel.

        try {
            final RandomAccessFile wavFile = randomAccessFile(file);
            wavFile.seek(0); // to the beginning
            wavFile.write(generateHeader(fileSize, totalSize, sampleRate, channels, byteRate));
            wavFile.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, String.valueOf(e));
        }
    }

    private RandomAccessFile randomAccessFile(File file) {
        RandomAccessFile randomAccessFile;
        try {
            randomAccessFile = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return randomAccessFile;
    }

    private byte[] generateHeader(
            long totalAudioLen, long totalDataLen, long longSampleRate, int channels,
            long byteRate) {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; //16 for PCM. 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * (RECORDER_BPP / 8)); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        return header;
    }
}

