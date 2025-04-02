package com.example.soundseeder;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.MediaExtractor;

public class PlayerService extends Service {
    private static final int PORT = 12345;
    private static final String TAG = "PlayerService";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHUNK_SIZE = 4096 / 2;
    private static final int QUEUE_CAPACITY = 10;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2 * 2;
    private static final long SEND_INTERVAL_MS = 1;

    private AudioTrack audioTrack;
    int minBufferdSize = 0;
    private ArrayBlockingQueue<byte[]> audioQueue;
    private Thread playbackThread;
    private Thread streamingThread;
    private Thread serverThread;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private final List<Socket> clients = new ArrayList<>();
    private long playbackPosition = 0;
    private long duration = 0;
    private MediaExtractor extractor;

    @Override
    public void onCreate() {
        super.onCreate();
        audioQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        initializeAudioTrack();
        startServer();
        Log.d(TAG, "Service created with queue capacity: " + QUEUE_CAPACITY);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PLAY":
                    String uriString = intent.getStringExtra("SONG_URI");
                    if (uriString != null) {
                        playSong(Uri.parse(uriString));
                    } else {
                        Log.e(TAG, "No URI provided for PLAY action");
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
        int sampleRate = SAMPLE_RATE;
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;

        minBufferdSize = AudioTrack.getMinBufferSize(sampleRate, 4, 2);

        System.out.println("minBuffer: " + minBufferdSize);


        audioTrack = new AudioTrack(3, sampleRate,
                4, 2, minBufferdSize
                , 1);


        audioTrack.setStereoVolume(0.0f, 0.0f);

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
        audioTrack.setPositionNotificationPeriod(sampleRate / 10);
        Log.d(TAG, "AudioTrack initialized with buffer size: " + bufferSize);
    }

    private void playSong(Uri uri) {
        stopPlayback();
        if (uri == null) {
            Log.e(TAG, "URI is null, cannot play song");
            return;
        }
        Log.d(TAG, "Playing song with URI: " + uri.toString());
        isPlaying.set(true);

        playbackThread = new Thread(() -> {
            try {
                extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(this, uri, null);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to set data source for URI: " + uri, e);
                    isPlaying.set(false);
                    return;
                }

                int trackCount = extractor.getTrackCount();
                if (trackCount == 0) {
                    Log.e(TAG, "No tracks found in file: " + uri);
                    isPlaying.set(false);
                    return;
                }

                Log.d(TAG, "Track count: " + trackCount);
                for (int i = 0; i < trackCount; i++) {
                    android.media.MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        duration = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000;
                        Log.d(TAG, "Selected audio track, duration: " + duration + "ms");
                        break;
                    }
                }

                ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
                while (isPlaying.get()) {
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        Log.d(TAG, "End of stream reached");
                        break;
                    }
                    byte[] data = new byte[size];
                    buffer.rewind();
                    buffer.get(data);
                    while (!audioQueue.offer(data, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "Queue full, waiting...");
                    }
                    Log.d(TAG, "Added " + size + " bytes to queue");
                    extractor.advance();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in playback thread", e);
            } finally {
                if (extractor != null) {
                    extractor.release();
                    extractor = null;
                }
                isPlaying.set(false);
            }
        });
        playbackThread.start();

        streamingThread = new Thread(() -> {
            long lastSendTime = System.currentTimeMillis();
            byte[] accumulatedData = new byte[BYTES_PER_SECOND];
            int accumulatedSize = 0;

            while (isPlaying.get() || !audioQueue.isEmpty()) {
                try {
                    byte[] data = audioQueue.take();
                    byte[] byteArray = minBufferdSize <= 0 ? ByteArrayPool.getByteArray(32768) : ByteArrayPool.getByteArray(minBufferdSize * 2);
                    int written = audioTrack.write(byteArray, 0, data.length);
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack write failed: " + written);
                    } else {
                        Log.d(TAG, "Wrote " + written + " bytes to AudioTrack");
                    }

                    synchronized (clients) {
                        for (Socket client : new ArrayList<>(clients)) {
                            if (!client.isClosed()) {
                                try {
                                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream());
                                    bufferedOutputStream.write(data);
                                    bufferedOutputStream.flush();
                                    Log.d(TAG, "Sent " + byteArray.length + " bytes to client");
                                } catch (IOException e) {
                                    Log.e(TAG, "Client disconnected", e);
                                    try {
                                        client.close();
                                    } catch (IOException ignored) {
                                    }
                                    clients.remove(client);
                                }
                            }
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.d(TAG, "Streaming thread interrupted");
                    break;
                }
            }

        });
        streamingThread.start();


        audioTrack.play();
        audioTrack.flush();
        Log.d(TAG, "AudioTrack started");
    }

    private void sendAccumulatedData(byte[] sendData) {
        Log.d(TAG, "Sending " + sendData.length + " bytes to clients");
        synchronized (clients) {
            for (Socket client : new ArrayList<>(clients)) {
                if (!client.isClosed()) {
                    try {
                        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream());
                        byte[] f37192z = {82, 73, 70, 70};
                        bufferedOutputStream.write(f37192z);
                        bufferedOutputStream.flush();
                        Log.d(TAG, "Sent " + sendData.length + " bytes to client");
                    } catch (IOException e) {
                        Log.e(TAG, "Client disconnected", e);
                        try {
                            client.close();
                        } catch (IOException ignored) {
                        }
                        clients.remove(client);
                    }
                }
            }
        }
    }

    private void sendAccumulatedData(byte[] accumulatedData, int size) {
//        System.out.println("hey data must be send: " + accumulatedData );
        System.out.println("size is : " + size);
        byte[] sendData = new byte[size];
        System.arraycopy(accumulatedData, 0, sendData, 0, size);
        synchronized (clients) {
            for (Socket client : new ArrayList<>(clients)) {
                if (!client.isClosed()) {
                    try {
                        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(client.getOutputStream());
                        bufferedOutputStream.write(sendData);
                        bufferedOutputStream.flush();
                        Log.d(TAG, "Sent " + sendData.length + " bytes to client");
                    } catch (IOException e) {
                        Log.e(TAG, "Client disconnected", e);
                        try {
                            client.close();
                        } catch (IOException ignored) {
                        }
                        clients.remove(client);
                    }
                }
            }
        }
    }

    private void togglePause() {
        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
            audioQueue.clear();
            Log.d(TAG, "Paused");
            //notifyClients("PAUSE");
        } else {
            audioTrack.play();
            Log.d(TAG, "Resumed");
            //notifyClients("RESUME");
        }
    }

    private void seekTo(long positionMs) {
        synchronized (audioTrack) {
            audioTrack.pause();
            audioTrack.flush();
            audioQueue.clear();
            if (extractor != null) {
                extractor.seekTo(positionMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                playbackPosition = positionMs;
                Log.d(TAG, "Seeked to " + positionMs + "ms");
            }
            if (isPlaying.get()) {
                audioTrack.play();
            }
            //notifyClients("SEEK:" + positionMs);
        }
    }

    private void notifyClients(String command) {
        synchronized (clients) {
            for (Socket client : new ArrayList<>(clients)) {
                if (!client.isClosed()) {
                    try {
                        OutputStream out = client.getOutputStream();
                        out.write(command.getBytes());
                        out.flush();
                        Log.d(TAG, "Sent command to client: " + command);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending command to client", e);
                        try {
                            client.close();
                        } catch (IOException ignored) {
                        }
                        clients.remove(client);
                    }
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
        audioQueue.clear();
        if (playbackThread != null) playbackThread.interrupt();
        if (streamingThread != null) streamingThread.interrupt();
        Log.d(TAG, "Playback stopped");
        //notifyClients("STOP");
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Server started on port " + PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    client.setSendBufferSize(65536);
                    client.setTcpNoDelay(true);
                    client.setSoTimeout(1000);

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
                } catch (IOException e) {
                }
            }
            clients.clear();
        }
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
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