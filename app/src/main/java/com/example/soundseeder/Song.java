package com.example.soundseeder;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;

public class Song {
    private String album;
    private String albumArtPath;
    private long albumId;
    private String artist;
    private String genre;
    private long id;
    private boolean isCheckShown;
    private boolean isSelected;
    private String mimeType;
    private boolean nowPlaying;
    private Uri uri;
    private String title;
    private String trackLength;
    private long trackLengthInMillis;

    public Song() {
    }

    public Song(Uri path) {
        this.uri = path;
        // generateFromMetadata();
    }

    public Song(Song other) {
        this.title = other.title;
        this.artist = other.artist;
        this.genre = other.genre;
        this.mimeType = other.mimeType;
        this.trackLength = other.trackLength;
        this.uri = other.uri;
        this.albumArtPath = other.albumArtPath;
        this.id = other.id;
        this.albumId = other.albumId;
        this.trackLengthInMillis = other.trackLengthInMillis;
        this.isSelected = other.isSelected;
        this.nowPlaying = other.nowPlaying;
        this.isCheckShown = other.isCheckShown;
    }

    public Song(Cursor cursor) {
        this.id = cursor.getLong(0);
        this.title = cursor.getString(1);
        this.album = cursor.getString(2);
        this.artist = cursor.getString(3);
        this.trackLengthInMillis = cursor.getLong(4);
        this.trackLength = milliSecondsToTimer(this.trackLengthInMillis);
        this.uri = Uri.parse(cursor.getString(5));
        this.albumId = cursor.getLong(6);
        this.mimeType = cursor.getString(7);
        Uri songCover = Uri.parse("content://media/external/audio/albumart");
        this.albumArtPath = ContentUris.withAppendedId(songCover, this.albumId).toString();
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public void setTrackLength(String trackLength) {
        this.trackLength = trackLength;
        this.trackLengthInMillis = timerToMillis(this.trackLength);
    }

    public void setTrackLengthInMillis(long trackLengthInMillis) {
        this.trackLengthInMillis = trackLengthInMillis;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    public void setNowPlaying(boolean nowPlaying) {
        this.nowPlaying = nowPlaying;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setAlbumArtPath(String albumArtPath) {
        this.albumArtPath = albumArtPath;
    }

    public void setCheckShown(boolean isCheckShown) {
        this.isCheckShown = isCheckShown;
    }

    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isCheckShown() {
        return this.isCheckShown;
    }

    public boolean isSelected() {
        return this.isSelected;
    }

    public boolean isNowPlaying() {
        return this.nowPlaying;
    }

    public String getTrackLength() {
        return this.trackLength;
    }

    public long getTrackLengthInMillis() {
        return this.trackLengthInMillis;
    }

    public Uri getUri() {
        return this.uri;
    }

    public String getArtist() {
        return this.artist;
    }

    public String getAlbum() {
        return this.album;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public long getAlbumId() {
        return this.albumId;
    }

    public String getGenre() {
        return this.genre;
    }

    public String getTitle() {
        return this.title;
    }

    public long getId() {
        return this.id;
    }

    public String getAlbumArtPath() {
        return this.albumArtPath;
    }

    public int hashCode() {
        if (this.uri != null) {
            return this.uri.hashCode();
        }
        return 0;
    }

    public String toString() {
        return "Title: " + this.title + "\nAritst: " + this.artist + "\nAlbum: " + this.album + "\nTrack Length: " + this.trackLength + "\nPath: " + this.uri;
    }

    public int compareTo(Song another) {
        if (another == null || another.title == null || another.title.isEmpty()) {
            return 0;
        }
        if (this.title == null || this.title.isEmpty()) {
            return 1;
        }
        return this.title.compareToIgnoreCase(another.title);
    }

    /*private String getCustomData(int data) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this.path);
        String ans = retriever.extractMetadata(data);
        retriever.release();
        return ans;
    }*/

  /*  public boolean equals(Object o) {
        return (o instanceof Song) && this.uri.equalsIgnoreCase(((Song) o).uri);
    }
*/
   /* private void generateFromMetadata() {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this.path);
        this.title = retriever.extractMetadata(7);
        if (this.title == null || this.title.equals("")) {
            this.title = this.path.substring(this.path.lastIndexOf("/") + 1, this.path.lastIndexOf("."));
        }
        this.album = retriever.extractMetadata(1);
        if (this.album == null || this.album.equals("")) {
            this.album = "Unknown Album";
        }
        this.artist = retriever.extractMetadata(2);
        if (this.artist == null || this.artist.equals("")) {
            this.artist = "Unknown Artist";
        }
        this.genre = retriever.extractMetadata(6);
        if (this.genre == null || this.genre.equals("")) {
            this.genre = "Unknown Genre";
        }
        this.trackLengthInMillis = Long.parseLong(retriever.extractMetadata(9));
        this.trackLength = milliSecondsToTimer(this.trackLengthInMillis);
        retriever.release();
    }*/

    public static String milliSecondsToTimer(long milliseconds) {
        String secondsString;
        String finalTimerString = "";
        int hours = (int) (milliseconds / 3600000);
        int minutes = ((int) (milliseconds % 3600000)) / 60000;
        int seconds = (int) (((milliseconds % 3600000) % 60000) / 1000);
        if (hours > 0) {
            finalTimerString = hours + ":";
        }
        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;
        }
        return finalTimerString + minutes + ":" + secondsString;
    }

    public static int getProgressPercentage(long currentDuration, long totalDuration) {
        long currentSeconds = (int) (currentDuration / 1000);
        long totalSeconds = (int) (totalDuration / 1000);
        double percentage = ((double) currentSeconds / totalSeconds) * 100.0d;
        return (int) percentage;
    }

    public static int progressToMillis(int progress, int totalDuration) {
        int currentDuration = (int) ((progress / 100.0d) * ((double) totalDuration / 1000));
        return currentDuration * 1000;
    }

    public static long timerToMillis(String timer) {
        return ((Long.parseLong(timer.split(":")[0]) * 60) + Long.parseLong(timer.split(":")[1])) * 1000;
    }

   /* public static Bitmap getAlbumArt(Context context, String albumArtPath, String songPath) {
        return getAlbumArt(context, albumArtPath, songPath, 128, 128);
    }

    public static Bitmap getAlbumArt(Context context, String albumArtPath, String songPath, int reqHeight, int reqWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            InputStream is = context.getContentResolver().openInputStream(Uri.parse(albumArtPath));
            BitmapFactory.decodeStream(is, null, options);
            options.inSampleSize = calculateSampleInSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(is, null, options);
        } catch (Throwable th) {
            Bitmap bitmap2 = getAlbumArtFromFile(context, songPath, reqHeight, reqWidth);
            return bitmap2;
        }
    }

    private static Bitmap getAlbumArtFromFile(Context context, String songPath, int reqHeight, int reqWidth) {
        Bitmap bitmap = getAlbumArtFromFileOrNull(context, songPath, reqHeight, reqWidth);
        if (bitmap == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_empty, options);
            options.inSampleSize = calculateSampleInSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_empty, options);
        }
        return bitmap;
    }

    public static Bitmap getAlbumArtFromFileOrNull(Context context, String songPath, int reqHeight, int reqWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(songPath);
            byte[] data = retriever.getEmbeddedPicture();
            retriever.release();
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            options.inSampleSize = calculateSampleInSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Throwable th) {
            return null;
        }
    }

    private static int calculateSampleInSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while (halfHeight / inSampleSize > reqHeight && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static void loadAlbumArt(final ImageView imageView, String albumArtPath, final String songPath, final Palette.PaletteAsyncListener paletteAsyncListener) {
        new Thread(() -> {
            Bitmap bitmap = Song.getAlbumArtFromFileOrNull(imageView.getContext(), songPath, 128, 128);
            boolean shouldBlur = bitmap != null;
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(imageView.getContext().getResources(), C0233R.drawable.bg_empty, null);
            }
            Palette palette = new Palette.Builder(bitmap).generate();
            paletteAsyncListener.onGenerated(palette);
            final Bitmap albumArt = shouldBlur ? FastBlur.doBlur(bitmap, 1, false) : bitmap.copy(Bitmap.Config.ARGB_8888, true);
            bitmap.recycle();
            imageView.post(new Runnable() { // from class: com.avrapps.chorus.model.Song.1.1
                @Override // java.lang.Runnable
                public void run() {
                    imageView.setImageBitmap(albumArt);
                }
            });
        }).start();
    }*/
}