package com.example.soundseeder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlin.jvm.java

class MainActivity : ComponentActivity(){
    private val songs = mutableStateListOf<Song>()
    private var currentSongIndex by mutableStateOf(-1)
    private var playbackPosition by mutableStateOf(0f)
    private var duration by mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            return
        }

        loadSongs()
        startService(Intent(this, PlayerService::class.java))

        setContent {
            MaterialTheme {
                PlayerUI(
                    songs = songs,
                    currentSongIndex = currentSongIndex,
                    playbackPosition = playbackPosition,
                    duration = duration,
                    onSongSelected = { index ->
                        currentSongIndex = index
                        playSong(index)
                    },
                    onPauseToggle = { togglePause() },
                    onNext = { playNext() },
                    onPrevious = { playPrevious() },
                    onSeek = { position -> seekTo(position) },
                    onPositionUpdate = { pos, dur ->
                        playbackPosition = pos
                        duration = dur
                    }
                )
            }
        }
    }

    private fun loadSongs() {
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA)
        val cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(0)
                val path = it.getString(1)
                songs.add(Song(title, Uri.parse(path)))
            }
        }
    }

    private fun playSong(index: Int) {
        val intent = Intent(this, PlayerService::class.java).apply {
            action = "PLAY"
            putExtra("SONG_URI", songs[index].getUri().toString())
            putExtra("SONG_INDEX", index)
        }
        startService(intent)
    }

    private fun togglePause() {
        startService(Intent(this, PlayerService::class.java).apply { action = "TOGGLE_PAUSE" })
    }

    private fun playNext() {
        if (currentSongIndex < songs.size - 1) {
            playSong(currentSongIndex + 1)
        }
    }

    private fun playPrevious() {
        if (currentSongIndex > 0) {
            playSong(currentSongIndex - 1)
        }
    }

    private fun seekTo(position: Float) {
        val intent = Intent(this, PlayerService::class.java).apply {
            action = "SEEK"
            putExtra("POSITION", position)
        }
        startService(intent)
    }
}

@Composable
fun PlayerUI(
    songs: List<Song>,
    currentSongIndex: Int,
    playbackPosition: Float,
    duration: Float,
    onSongSelected: (Int) -> Unit,
    onPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onPositionUpdate: (Float, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(songs) { index, song ->
                Text(
                    text = song.getTitle(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongSelected(index) }
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (currentSongIndex >= 0) {
            Text(
                text = "Playing: ${songs[currentSongIndex].getTitle()}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Slider(
                value = playbackPosition,
                onValueChange = { onSeek(it) },
                valueRange = 0f..duration,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onPrevious) { Text("Previous") }
                Button(onClick = onPauseToggle) { Text("Pause/Resume") }
                Button(onClick = onNext) { Text("Next") }
            }
        }
    }
}