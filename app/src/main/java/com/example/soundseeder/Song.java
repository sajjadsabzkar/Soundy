package com.example.soundseeder;
import android.net.Uri;

public class Song {
    private String title;
    private Uri uri;

    public Song(String title, Uri uri) {
        this.title = title;
        this.uri = uri;
    }

    public String getTitle() {
        return title;
    }

    public Uri getUri() {
        return uri;
    }
}