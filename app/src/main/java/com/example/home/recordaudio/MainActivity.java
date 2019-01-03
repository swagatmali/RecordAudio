package com.example.home.recordaudio;

import android.content.pm.PackageManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity implements SoundRecorder.OnVoicePlaybackStateChangedListener {
    private static final int REQUEST_RECORD_AUDIO = 13;
    private StringBuilder str;
    private SoundRecorder mSoundRecorder;
    private static final String VOICE_FILE_NAME = "audiorecord.pcm";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSoundRecorder = new SoundRecorder(this, VOICE_FILE_NAME, this);
    }

    public void onClickStartRecord(View V) {
        startAudioRecordingSafe();
    }

    public void onClickStopRecoding(View V) {
        findViewById(R.id.button3).setVisibility(View.VISIBLE);
        mSoundRecorder.stopRecording();
    }

    public void play(View V) {
        mSoundRecorder.startPlay();
    }


    private void startAudioRecordingSafe() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            mSoundRecorder.startRecording();
        } else {
            requestMicrophonePermission();
        }
    }

    private void requestMicrophonePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)) {
            // Show dialog explaining why we need record audio
            Snackbar.make(findViewById(R.id.mainLayout), "Microphone access is required in order to record audio",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                            android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                }
            }).show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onPlaybackStopped() {

    }
}

