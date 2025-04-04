package com.example.soundseeder;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MusicPlayerService extends Service {
    private ServerSocket serverSocket;
    private HashMap<String, User> activeClients = new HashMap<>();
    private Messenger client;
    private Messenger sender = new Messenger(new ClientHandler(this));
    public static final String START_KEY = "start";
    private ServerThread serverThread;

    private Song currentSong;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sender.getBinder();
    }


    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

      /*  Notification notification = new NotificationCompat.Builder(this, "my_channel_id")
                .setContentTitle("My Service Running")
                .setContentText("This service is in foreground.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);*/


        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PLAY":
                    String uriString = intent.getStringExtra("SONG_URI");
                    currentSong = new Song(Uri.parse(uriString));
                    startServer();
                    break;
                case "PAUSE":
                    break;
            }
        }
        return START_STICKY;
    }

    private void startServer() {
        if (this.serverThread == null) {
            this.serverThread = new ServerThread();
            this.serverThread.start();
          /*  this.udpLocator = new UDPLocator();
            this.udpLocator.start();*/
/*            startForeground(47,
                    new NotificationCompat.Builder(this).setAutoCancel(false)
                            .setContentIntent(PendingIntent.getActivity(this, 47,
                                    new Intent(this, (Class<?>) StartActivity.class)
                                            .setFlags(536870912), 268435456))
                            .setContentTitle("Chorus ID: " + NetworkUtils.getChorusHostId(this))
                            .setSmallIcon(R.drawable.ic_chorus_notification).build());*/
        }
    }

    private class ServerThread extends Thread {
        Socket client;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(Constants.PORT);
                serverSocket.setReceiveBufferSize(16384);
            } catch (IOException e) {
                Logger.error(e);
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    client = serverSocket.accept();
                    if (client != null) {
                        client.setKeepAlive(true);
                        client.setSendBufferSize(16384);
                        client.setReceiveBufferSize(16384);
                        client.setTcpNoDelay(true);
                        new HandleClient(new User(client)).start();
                    }
                } catch (Exception e2) {
                    Logger.error(e2);
                    removeClient(client);
                }
            }
        }
    }

    private static class ClientHandler extends Handler {
        private WeakReference<MusicPlayerService> serviceWeakReference;

        public ClientHandler(MusicPlayerService service) {
            this.serviceWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            this.serviceWeakReference.get().handleMessage(msg);
        }
    }


    protected void handleMessage(Message msg) {
        switch (msg.what) {
            case 0:
                this.client = msg.replyTo;
                break;
        }
    }

    private class HandleClient extends Thread {
        private int PORT;
        private InetAddress address;
        private BufferedReader inputStream;
        private OutputStream outputStream;
        private Socket socket;
        private PrintWriter writer;
        private long rangeStart = -1;
        private long rangeEnd = -1;

        public HandleClient(User user) {
            inputStream = new BufferedReader(new InputStreamReader(user.getInputStream()));
            outputStream = user.getOutputStream();
            writer = user.getPrintWriter();
            socket = user.getSocket();
            address = socket.getInetAddress();
            PORT = socket.getPort();
        }

        @Override
        public void run() {
            String request;
            String read;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                   /* if (socket == null || socket.isClosed() || !socket.isConnected() || (request = inputStream.readLine()) == null) {
                        break;
                    }*/
                   /* if (request.startsWith(Constants.CLIENT_GET_SONG)) {
                        do {
                            read = inputStream.readLine();
                            if (read.startsWith("Content-Range:") || read.startsWith("Range:")) {
                                read = read.substring(read.indexOf("bytes") + 6);
                                String[] data = read.split("-");
                                rangeStart = Long.parseLong(data[0]);
                                rangeEnd = data.length == 1 ? -1L : Long.parseLong(data[1]);
                            }
                        } while (read.length() > 1);
                    }*/
                    sendResponse("request");
                } catch (IOException e) {
                    Logger.error(e);
                    removeClient(socket);
                }
            }
            if (activeClients.isEmpty())
                stopSelf();
            rangeStart = -1L;
            rangeEnd = -1L;
            removeClient(socket);
            safeClose(inputStream);
            safeClose(outputStream);
        }

        private void sendResponse(String request) throws IOException {

            ContentResolver resolver = getApplicationContext().getContentResolver();
            File f = new File(currentSong.getUri().getPath());
            rangeEnd = rangeEnd == -1 ? ((int) f.length()) - 1 : rangeEnd;
            FileInputStream fis = new FileInputStream(currentSong.getUri().getPath()) {
                @Override
                public int available() throws IOException {
                    return (int) ((rangeEnd - (rangeStart == -1 ? 0L : rangeStart)) + 1);
                }
            };
            writer.print("HTTP/1.1 " + (rangeStart == -1 ? "200 OK" : "206 Partial Content") + " \r\n");
            writer.print("Date: " + new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US).format(new Date()) + "\r\n");
            writer.print("Accept-Ranges: bytes \r\n");
            writer.print("Content-Type: " + currentSong.getMimeType() + " \r\n");
            writer.print("Content-Length: " + fis.available() + "\r\n");
            writer.print("\r\n");
            writer.flush();
            if (rangeStart > 1) {
                fis.skip(rangeStart - 1);
            }
            int pending2 = fis.available();
            byte[] buff2 = new byte[16384];
            while (pending2 > 0) {
                int read2 = fis.read(buff2, 0, Math.min(pending2, 16384));
                if (read2 <= 0) {
                    break;
                }
                try {
                    outputStream.write(buff2, 0, read2);
                } catch (Exception e) {
                    Logger.error(e);
                }
                pending2 -= read2;
            }
            outputStream.flush();
            safeClose(fis);



           /* if (request.contains(Constants.CLIENT_GET_ALBUM_ART) && request.contains(Constants.CLIENT_GET_SONG)) {
                Bitmap bmp = Song.getAlbumArt(getApplicationContext(), currentSong.getAlbumArtPath(), currentSong.getPath());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, buffer);
                ByteArrayInputStream fis = new ByteArrayInputStream(buffer.toByteArray());
                writer.print("HTTP/1.1 200 OK \r\n");
                writer.print("Date: " + new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US).format(new Date()) + "\r\n");
                writer.print("Accept-Ranges: bytes\r\n");
                writer.print("Content-Length " + buffer.toByteArray().length + "\r\n");
                writer.print("Content-Type: image/png\r\n");
                writer.print("\r\n");
                writer.flush();
                byte[] buff = new byte[32768];
                int pending = fis.available();
                while (pending > 0) {
                    int read = fis.read(buff, 0, Math.min(pending, 32768));
                    if (read <= 0 || socket.isClosed() || !socket.isConnected()) {
                        break;
                    }
                    outputStream.write(buff, 0, read);
                    outputStream.flush();
                    pending -= read;
                }
                outputStream.flush();
                safeClose(fis);
                safeClose(buffer);
                safeClose(outputStream);
                socket.close();
                bmp.recycle();
                return;
            }*/
/*
            if (request.contains(Constants.CLIENT_GET_SONG)) {
                File f = new File(currentSong.getUri().getPath());
                rangeEnd = rangeEnd == -1 ? ((int) f.length()) - 1 : rangeEnd;
                FileInputStream fis = new FileInputStream(currentSong.getUri().getPath()) {
                    @Override
                    public int available() throws IOException {
                        return (int) ((rangeEnd - (rangeStart == -1 ? 0L : rangeStart)) + 1);
                    }
                };
                writer.print("HTTP/1.1 " + (rangeStart == -1 ? "200 OK" : "206 Partial Content") + " \r\n");
                writer.print("Date: " + new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US).format(new Date()) + "\r\n");
                writer.print("Accept-Ranges: bytes \r\n");
                writer.print("Content-Type: " + currentSong.getMimeType() + " \r\n");
                writer.print("Content-Length: " + fis.available() + "\r\n");
                writer.print("\r\n");
                writer.flush();
                if (rangeStart > 1) {
                    fis.skip(rangeStart - 1);
                }
                int pending2 = fis.available();
                byte[] buff2 = new byte[16384];
                while (pending2 > 0) {
                    int read2 = fis.read(buff2, 0, Math.min(pending2, 16384));
                    if (read2 <= 0) {
                        break;
                    }
                    try {
                        outputStream.write(buff2, 0, read2);
                    } catch (Exception e) {
                        Logger.error(e);
                    }
                    pending2 -= read2;
                }
                outputStream.flush();
                safeClose(fis);
                return;
            }
*/
           /* if (request.contains(Constants.CLIENT_GET_DETAILS)) {
                String userName = request.split("\t")[1];
                addClient(socket);
                ((User) activeClients.get(address.getHostAddress() + ":" + PORT)).setUserName(userName);
                sendUserData();
                writer.println("Welcome");
            } else if (request.equals(Constants.CLIENT_GET_SONG_DETAILS)) {
                writer.println(currentSong.getTitle() + "\t" + currentSong.getArtist() + "\t" + currentSong.getTrackLengthInMillis() + "\t" + Build.VERSION.SDK_INT + "\t" + currentSong.getAlbum());
            } else if (request.equals(Constants.CLIENT_GET_ALBUM_ART)) {
                try {
                    MessageDigest algo = MessageDigest.getInstance("MD5");
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    Bitmap bitmap = Song.getAlbumArt(getApplicationContext(), currentSong.getAlbumArtPath(), currentSong.getPath());
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    writer.println(MD5ToString(algo.digest(bos.toByteArray())));
                    bitmap.recycle();
                } catch (NoSuchAlgorithmException ex) {
                    Logger.error(ex);
                }
            } else if (request.equals(Constants.CLIENT_REQUEST_SYNC) || request.equals(Constants.CLIENT_REQUEST_IOS)) {
                writer.println("com.avrapps.chorus.requestsync\t" + currentTimeMillis);
            } else if (request.equals("com.avrapps.chorus.nextsong")) {
                writer.println("com.avrapps.chorus.nextsong\t" + nextSong);
            } else if (request.equals(Constants.TIMESTAMP)) {
                writer.println(System.currentTimeMillis());
            }*/
            writer.flush();
            outputStream.flush();
        }
    }

    public void safeClose(Closeable closable) {

        if (closable == null) return;

        try {
            closable.close();
        } catch (IOException e) {
            Logger.error(e);
        }


    }

    public synchronized void addClient(Socket socket) {
        activeClients.put(socket.getInetAddress().getHostAddress() + ":" + socket.getPort(), new User(socket));
    }

    public synchronized void removeClient(Socket socket) {
        if (socket != null) {
            if (socket.getInetAddress() != null) {
                activeClients.remove(socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
            }
        }
        safeClose(socket);
    }
}
