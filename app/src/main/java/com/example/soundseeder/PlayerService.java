package com.example.soundseeder;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.MediaExtractor;

public class PlayerService extends Service {
    private static final int PORT = 12345;
    private static final String TAG = "PlayerService";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHUNK_SIZE = 4096;
    private static final int QUEUE_CAPACITY = 50;
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
    private MediaCodec codec;

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


        audioTrack =new AudioTrack(3, this.extractor.getTrackFormat(0).getInteger("sample-rate"), this.extractor.getTrackFormat(0).getInteger("channel-count") == 1 ? 4 : 12, 2, AudioTrack.getMinBufferSize(this.extractor.getTrackFormat(0).getInteger("sample-rate"), this.extractor.getTrackFormat(0).getInteger("channel-count") == 1 ? 4 : 12, 2), 1);


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

                this.codec = MediaCodec.createDecoderByType(this.extractor.getTrackFormat(0).getString("mime"));
                this.codec.configure(this.extractor.getTrackFormat(0),  null,  null, 0);
                this.codec.start();
                int trackCount = extractor.getTrackCount();
                if (trackCount == 0) {
                    Log.e(TAG, "No tracks found in file: " + uri);
                    isPlaying.set(false);
                    return;
                }

                Log.d(TAG, "Track count: " + trackCount);
                for (int i = 0; i < trackCount; i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        duration = format.getLong(MediaFormat.KEY_DURATION) / 1000;
                        Log.d(TAG, "Selected audio track, duration: " + duration + "ms");
                        break;
                    }
                }

                ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
                while (isPlaying.get()) {
                    int size = extractor.readSampleData(buffer, 0);
                    Log.e("Play", "size is: " + size);
                    if (size < 0) {
//                        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC); // برگشت به ابتدای فایل
                        Log.d(TAG, "End of stream reached");
//                        continue; // for repeat
                        break;
                    }
                    byte[] data = new byte[size];
                    buffer.rewind();
                    buffer.get(data);
                    while (!audioQueue.offer(data, 1, TimeUnit.SECONDS)) {
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

       /* streamingThread = new Thread(() -> {

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

        });*/
        streamingThread = new Thread(() -> {

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


                    Thread.sleep(22);
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
                serverSocket.setSoTimeout(50000 * 10);
                Log.d(TAG, "Server started on port " + PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    client.setKeepAlive(true);
                    client.setSendBufferSize(16384);
                    client.setReceiveBufferSize(16384);
                    client.setTcpNoDelay(true);
                    client.setSoTimeout(50000 * 10);
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
