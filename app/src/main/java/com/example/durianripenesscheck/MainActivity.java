package com.example.durianripenesscheck;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {


    private static final int REQUEST_RECORD_AUDIO = 13;

    private static final String LOG_TAG = "Record_log";
    private TextView mLabel;
    private TextView plabel;
    private TextView mTime;
    ProgressBar pbTimer;
    CountDownTimer cdt;

    private String outputFile = null;
    private final WavRecorder rec = new WavRecorder();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pbTimer = findViewById(R.id.pbTimer);
        pbTimer.setVisibility(View.INVISIBLE);

        mTime = findViewById(R.id.time);


        final ToggleButton mRecordBtn = findViewById(R.id.record_bt);
        mLabel = findViewById(R.id.label);
        plabel = findViewById(R.id.pLabel);

        outputFile = Objects.requireNonNull(getExternalCacheDir()).getAbsolutePath();
        outputFile += "/wav_recorded.wav";

        requestMicrophonePermission();

        mRecordBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton view
                    , boolean isChecked) {
                if (isChecked) {
                    pbTimer.setVisibility(View.VISIBLE);
                    System.out.println(outputFile);
                    rec.prepare(outputFile, 8000);

                    startRecording();

                    cdt = new CountDownTimer(5000, 50) {

                        public void onTick(long millisUntilFinished) {
                            @SuppressLint("DefaultLocale") String strTime = String.format("%.1f"
                                    , (double) millisUntilFinished / 1000);
                            plabel.setText("กำลังบันทึกเสียง");
                            mLabel.setText("");
                            mTime.setText(strTime);
                        }

                        public void onFinish() {
                            mTime.setText("--.--");
                            mRecordBtn.setChecked(false);
                            pbTimer.setVisibility(View.INVISIBLE);
                            stopRecording();
                            plabel.setText("บันทึกเสียงเสร็จสิ้น");
                        }
                    }.start();
                } else {
                    cdt.cancel();
                    plabel.setText("กดปุ่มเพื่อบันทึกเสียง");
                    mTime.setText("--.--");
                    pbTimer.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private void startRecording() {
        rec.startRecording();
    }

    private void stopRecording() {
        try {
            rec.stopRecording();
            uploadFile(outputFile, getTime());
            Log.v(LOG_TAG, "Stop!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void uploadFile(final String filename, final String time_name) {

//        Uri uri = Uri.fromFile(new File(filename));
        File file = new File(filename);
        file = file.getAbsoluteFile();


        Map<String, RequestBody> map = new HashMap<>();

        RequestBody requestBody =
                RequestBody.create(MediaType.parse("multipart/form-data"), file);
        map.put("file\"; filename=\"" + time_name + ".wav" + "\"", requestBody);

        final ApiConfig getResponse = AppConfig.getRetrofit().create(ApiConfig.class);
        Call<ServerResponse> call = getResponse.upload("file", map);
        call.enqueue(new Callback<ServerResponse>() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onResponse(@NonNull Call<ServerResponse> call, @NonNull Response<ServerResponse> response) {

                if (!response.isSuccessful()) {
                    plabel.setText("Code: " + response.code());
                    return;
                }
                String content = "";

                assert response.body() != null;
                content += "ความแม่นยำ: " + response.body().getACC() + "\n";
                content += "ความสุกทุเรียน: " + response.body().getLABEL() + "\n\n";

                Log.v(LOG_TAG, content);
                mLabel.append(content);
            }

            @Override
            public void onFailure(@NonNull Call<ServerResponse> call, @NonNull Throwable t) {
                Log.v("Response gotten is", Objects.requireNonNull(t.getMessage()));
                System.out.println(filename);
            }
        });
    }


    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO
            );
            Log.v(LOG_TAG, "This the request microphone permission");
        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        }
    }

    private String getTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }
}