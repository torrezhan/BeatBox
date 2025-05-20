package com.example.beatbox.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.beatbox.R
import com.example.beatbox.models.Track
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.Toast

class PlayerActivity : AppCompatActivity() {
    private lateinit var trackTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var albumArt: ImageView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        initializeViews()
        setupToolbar()

        val track = intent.getParcelableExtra<Track>("track")
        if (track != null) {
            updateTrackInfo(track)
        } else {
            Toast.makeText(this, "Error: Track information missing", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializeViews() {
        trackTitle = findViewById(R.id.trackTitle)
        artistName = findViewById(R.id.artistName)
        albumArt = findViewById(R.id.albumArt)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun updateTrackInfo(track: Track) {
        Log.d("PlayerActivity", "Updating track info: name=${track.name}, artist=${track.artist}")
        trackTitle.text = track.name
        artistName.text = track.artist ?: "Unknown Artist"
        loadAlbumArt(track.uri)
    }

    private fun loadAlbumArt(uri: Uri) {
        try {
            Log.d("PlayerActivity", "Loading album art for URI: $uri")
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val art = retriever.embeddedPicture
            if (art != null) {
                Log.d("PlayerActivity", "Found embedded album art")
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                albumArt.setImageBitmap(bitmap)
            } else {
                Log.d("PlayerActivity", "No embedded album art found, using default icon")
                albumArt.setImageResource(R.drawable.ic_music_note)
            }
            retriever.release()
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error loading album art", e)
            albumArt.setImageResource(R.drawable.ic_music_note)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 