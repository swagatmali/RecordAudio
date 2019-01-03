package com.example.home.recordaudio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * A helper class to provide methods to record audio input from the MIC to the internal storage
 * and to playback the same recorded audio file.
 */
public class SoundRecorder {

    private static final String TAG = "SoundRecorder";
    private static final int RECORDING_RATE = 8000; // can go up to 44K, if needed
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static int BUFFER_SIZE = AudioRecord
            .getMinBufferSize(RECORDING_RATE, CHANNEL_IN, FORMAT);

    private final String mOutputFileName;
    private final AudioManager mAudioManager;
    private final Handler mHandler;
    private final Context mContext;
    private State mState = State.IDLE;

    private OnVoicePlaybackStateChangedListener mListener;
    private AsyncTask<Void, Void, Void> mRecordingAsyncTask;
    private AsyncTask<Void, Void, Void> mPlayingAsyncTask;
    static ArrayList<byte[]> b;
    private static int read;

    enum State {
        IDLE, RECORDING, PLAYING
    }

    public SoundRecorder(Context context, String outputFileName,
                         OnVoicePlaybackStateChangedListener listener) {
        mOutputFileName = outputFileName;
        mListener = listener;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        b = new ArrayList<>();
    }

    /**
     * Starts recording from the MIC.
     */
    public void startRecording() {
        if (mState != State.IDLE) {
            Log.w(TAG, "Requesting to start recording while state was not IDLE");
            return;
        }

        mRecordingAsyncTask = new RecordAudioAsyncTask(this);

        mRecordingAsyncTask.execute();
    }

    public void stopRecording() {
        if (mRecordingAsyncTask != null) {
            mRecordingAsyncTask.cancel(true);
        }
    }

    public void stopPlaying() {
        if (mPlayingAsyncTask != null) {
            mPlayingAsyncTask.cancel(true);
        }
    }

    /**
     * Starts playback of the recorded audio file.
     */
    public void startPlay() {
        if (mState != State.IDLE) {
            Log.w(TAG, "Requesting to play while state was not IDLE");
            return;
        }

        if (!new File(mContext.getFilesDir(), mOutputFileName).exists()) {
            // there is no recording to play
            if (mListener != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onPlaybackStopped();
                    }
                });
            }
            return;
        }
        int intSize = AudioTrack.getMinBufferSize(RECORDING_RATE, CHANNELS_OUT, FORMAT);

        mPlayingAsyncTask = new PlayAudioAsyncTask(this, intSize);

        mPlayingAsyncTask.execute();
    }

    public interface OnVoicePlaybackStateChangedListener {

        /**
         * Called when the playback of the audio file ends. This should be called on the UI thread.
         */
        void onPlaybackStopped();
    }

    /**
     * Cleans up some resources related to {@link AudioTrack} and {@link AudioRecord}
     */
    public void cleanup() {
        Log.d(TAG, "cleanup() is called");
        stopPlaying();
        stopRecording();
    }


    private static class PlayAudioAsyncTask extends AsyncTask<Void, Void, Void> {

        private WeakReference<SoundRecorder> mSoundRecorderWeakReference;

        private AudioTrack mAudioTrack;
        private int mIntSize;

        PlayAudioAsyncTask(SoundRecorder context, int intSize) {
            mSoundRecorderWeakReference = new WeakReference<>(context);
            mIntSize = intSize;
        }

        @Override
        protected void onPreExecute() {

            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            if (soundRecorder != null) {
                soundRecorder.mAudioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        soundRecorder.mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        0 /* flags */);
                soundRecorder.mState = State.PLAYING;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            try {
                mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDING_RATE,
                        CHANNELS_OUT, FORMAT, mIntSize, AudioTrack.MODE_STREAM);
                byte[] buffer = new byte[mIntSize * 2];
                Log.e("ssss", "------- " + buffer);
                FileInputStream in = null;
                BufferedInputStream bis = null;
                mAudioTrack.setVolume(AudioTrack.getMaxVolume());
                mAudioTrack.play();
                try {
                    in = soundRecorder.mContext.openFileInput(soundRecorder.mOutputFileName);
                    bis = new BufferedInputStream(in);
//                    while ((read = bis.read(buffer, 0, buffer.length)) > 0) {
//                        mAudioTrack.write(buffer, 0, read);
//                        Log.e("ssss", "Failed to record data: " + buffer + "----" + read);
//                    }
                    if (!isCancelled()) {
                        for (byte[] bytes : b) {
                            mAudioTrack.write(bytes, 0, read);
                        }
                    }
                    Log.e("ssss", "------ " + b.size());
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read the sound file into a byte array", e);
                } finally {
                    try {
                        if (in != null) {
                            in.close();
                        }
                        if (bis != null) {
                            bis.close();
                        }
                    } catch (IOException e) { /* ignore */}

                    mAudioTrack.release();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to start playback", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            cleanup();
        }

        @Override
        protected void onCancelled() {
            cleanup();
        }

        private void cleanup() {
            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            if (soundRecorder != null) {
                if (soundRecorder.mListener != null) {
                    soundRecorder.mListener.onPlaybackStopped();
                }
                soundRecorder.mState = State.IDLE;
                soundRecorder.mPlayingAsyncTask = null;
            }
        }
    }

    private static class RecordAudioAsyncTask extends AsyncTask<Void, Void, Void> {

        private WeakReference<SoundRecorder> mSoundRecorderWeakReference;

        private AudioRecord mAudioRecord;

        RecordAudioAsyncTask(SoundRecorder context) {
            mSoundRecorderWeakReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            if (soundRecorder != null) {
                soundRecorder.mState = State.RECORDING;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDING_RATE, CHANNEL_IN, FORMAT, BUFFER_SIZE * 3);


            BufferedOutputStream bufferedOutputStream = null;

            try {
                bufferedOutputStream = new BufferedOutputStream(
                        soundRecorder.mContext.openFileOutput(
                                soundRecorder.mOutputFileName,
                                Context.MODE_PRIVATE));
                byte[] buffer = new byte[BUFFER_SIZE];
                mAudioRecord.startRecording();
                Log.e("ssss", "------ " + buffer);

                while (!isCancelled()) {
                    read = mAudioRecord.read(buffer, 0, buffer.length);
                    b.add(buffer);
                    Log.e("ssss", "------ " + buffer + " --- " + read);
                    bufferedOutputStream.write(buffer, 0, read);
                }
            } catch (IOException | NullPointerException | IndexOutOfBoundsException e) {
                Log.e(TAG, "Failed to record data: " + e);
            } finally {
                if (bufferedOutputStream != null) {
                    try {
                        bufferedOutputStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
                Log.e("ssss", "------ " + b.size());
                mAudioRecord.release();
                mAudioRecord = null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            if (soundRecorder != null) {
                soundRecorder.mState = State.IDLE;
                soundRecorder.mRecordingAsyncTask = null;
            }
        }

        @Override
        protected void onCancelled() {
            SoundRecorder soundRecorder = mSoundRecorderWeakReference.get();

            if (soundRecorder != null) {
                if (soundRecorder.mState == State.RECORDING) {
                    Log.d(TAG, "Stopping the recording ...");
                    soundRecorder.mState = State.IDLE;
                } else {
                    Log.w(TAG, "Requesting to stop recording while state was not RECORDING");
                }
                soundRecorder.mRecordingAsyncTask = null;
            }
        }
    }
}