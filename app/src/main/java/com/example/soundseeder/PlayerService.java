package com.example.soundseeder;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.MediaExtractor;

public class PlayerService extends Service {
    private static final int PORT = 12345;
    private static final String TAG = "PlayerService";
    private AudioTrack audioTrack;
    //    private ArrayBlockingQueue<byte[]> audioQueue;
    private ArrayBlockingQueue<AudioChunk> audioChunkQueue;

    private Thread playbackThread;
    private Thread streamingThread;
    private Thread serverThread;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private final List<Socket> clients = new ArrayList<>();
    private long playbackPosition = 0;
    private long duration = 0;
    private MediaExtractor extractor;

    private final Object bufferLock = new Object();
    private long bufferedDurationMs = 0;
    private static final long MAX_BUFFER_DURATION_MS = 1000;

    private static class AudioChunk {
        byte[] data;
        long durationMs;

        AudioChunk(byte[] data, long durationMs) {
            this.data = data;
            this.durationMs = durationMs;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
//        audioQueue = new ArrayBlockingQueue<>(43);
        audioChunkQueue = new ArrayBlockingQueue<>(128);
        initializeAudioTrack();
        startServer();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PLAY":
                    String uriString = intent.getStringExtra("SONG_URI");
                    if (uriString != null) {
                        playSong(Uri.parse(uriString));
                    }
                    break;
                case "TOGGLE_PAUSE":
                    togglePause();
                    break;
                case "SEEK":
                    float position = intent.getFloatExtra("POSITION", 0f);
                    seekTo((long) (position * 1000));
                    break;
            }
        }
        return START_STICKY;
    }

    private void initializeAudioTrack() {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2; // بافر بزرگ‌تر
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
        audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
                playbackPosition = track.getPlaybackHeadPosition() * 1000L / sampleRate;
                Log.d(TAG, "Playback position: " + playbackPosition + "ms");
            }
        });
        audioTrack.setPositionNotificationPeriod(sampleRate / 10); // آپدیت هر 100ms
        Log.d(TAG, "AudioTrack initialized with buffer size: " + bufferSize);
    }

    private void playSong(Uri uri) {
        stopPlayback();
        isPlaying.set(true);

        playbackThread = new Thread(() -> {
            try {
                extractor = new MediaExtractor();
                extractor.setDataSource(this, uri, null);
                int trackCount = extractor.getTrackCount();
                Log.d(TAG, "Track count: " + trackCount);
                int sampleRate = 44100;
                int bytesPerFrame = 4;
                for (int i = 0; i < trackCount; i++) {
                    android.media.MediaFormat format = extractor.getTrackFormat(i);
                    if (format.getString(android.media.MediaFormat.KEY_MIME).startsWith("audio/")) {
                        extractor.selectTrack(i);
                        duration = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000;
                        Log.d(TAG, "Selected audio track, duration: " + duration + "ms");
                        if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
                        }
                        break;
                    }
                }

                ByteBuffer buffer = ByteBuffer.allocate(4096);
                while (isPlaying.get()) {
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        Log.d(TAG, "End of stream reached");
                        break;
                    }
                    byte[] data = new byte[size];
                    buffer.rewind();
                    buffer.get(data);
                    long chunkDurationMs = (long) (((double) size / (sampleRate * bytesPerFrame)) * 1000);

//                    audioQueue.put(data);

                    synchronized (bufferLock) {
                        while (bufferedDurationMs + chunkDurationMs > MAX_BUFFER_DURATION_MS) {
                            bufferLock.wait();
                        }
                        audioChunkQueue.put(new AudioChunk(data, chunkDurationMs));
                        bufferedDurationMs += chunkDurationMs;
                    }

                   /* if (!audioQueue.offer(data)) {
                        Log.w(TAG, "Queue full, dropping data");
                        audioQueue.poll();
                        audioQueue.offer(data);
                    }*/
                    Log.d(TAG, "Added " + size + " bytes to queue");
                    extractor.advance();
                }
                extractor.release();
                extractor = null;
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error extracting audio", e);
            }
        });
        playbackThread.start();

        streamingThread = new Thread(() -> {
            while (isPlaying.get() || !audioChunkQueue.isEmpty()) {
                try {
                    AudioChunk chunk = audioChunkQueue.take();
                    Log.d(TAG, "Writing " + chunk.data.length + " bytes to AudioTrack");
                    int written = audioTrack.write(chunk.data, 0, chunk.data.length);
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack write failed: " + written);
                    }
                    synchronized (bufferLock) {
                        bufferedDurationMs -= chunk.durationMs;
                        if (bufferedDurationMs < 0) {
                            bufferedDurationMs = 0;
                        }
                        bufferLock.notifyAll();
                    }
                    synchronized (clients) {
                        for (Socket client : new ArrayList<>(clients)) {
                            if (!client.isClosed()) {
                                try {
                                    OutputStream out = client.getOutputStream();
                                    out.write(chunk.data);
                                    out.flush();
                                } catch (IOException e) {
                                    Log.e(TAG, "Client disconnected", e);
                                    client.close();
                                    clients.remove(client);
                                }
                            }
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    Thread.currentThread().interrupt();
                    Log.d(TAG, "Streaming thread interrupted");
                    break;
                }
            }
        });
        streamingThread.start();

        audioTrack.play();
        Log.d(TAG, "AudioTrack started");
    }

    private void togglePause() {
        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
            Log.d(TAG, "Paused");
        } else {
            audioTrack.play();
            Log.d(TAG, "Resumed");
        }
    }

    private void seekTo(long positionMs) {
        synchronized (audioTrack) {
            audioTrack.pause();
            audioTrack.flush();
            audioChunkQueue.clear();
            if (extractor != null) {
                extractor.seekTo(positionMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                playbackPosition = positionMs;
                Log.d(TAG, "Seeked to " + positionMs + "ms");
            }
            if (isPlaying.get()) {
                audioTrack.play();
            }
        }
        synchronized (clients) {
            for (Socket client : clients) {
                try {
                    OutputStream out = client.getOutputStream();
                    out.write(("SEEK:" + positionMs).getBytes());
                    out.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Error sending seek to client", e);
                }
            }
        }
    }

    private void stopPlayback() {
        isPlaying.set(false);
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.flush();
        }
        audioChunkQueue.clear();
        if (playbackThread != null) playbackThread.interrupt();
        if (streamingThread != null) streamingThread.interrupt();
        Log.d(TAG, "Playback stopped");
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Server started on port " + PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    synchronized (clients) {
                        clients.add(client);
                        Log.i(TAG, "Client connected: " + client.getInetAddress());
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Server error", e);
            }
        });
        serverThread.start();
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        if (audioTrack != null) audioTrack.release();
        synchronized (clients) {
            for (Socket client : clients) {
                try {
                    client.close();
                } catch (IOException ignored) {

                }
            }
            clients.clear();
        }
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (serverThread != null) serverThread.interrupt();
        Log.d(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}