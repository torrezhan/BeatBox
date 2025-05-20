package com.example.beatbox.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.beatbox.models.Track

object FileUtils {
    fun getAllAudioFiles(context: Context): List<Track> {
        val trackList = mutableListOf<Track>()
        // Define the collection URI based on Android version
        val collection =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        // Define the columns we want to retrieve
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,          // Unique ID for the audio file
            MediaStore.Audio.Media.DISPLAY_NAME, // File name
            MediaStore.Audio.Media.TITLE,        // Track title metadata
            MediaStore.Audio.Media.ARTIST        // Track artist metadata
            // Add other fields if needed, e.g., MediaStore.Audio.Media.DURATION
        )

        // Define the selection criteria: only retrieve music files
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        // val selectionArgs = null // No specific args needed for IS_MUSIC

        // Define the sort order
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC" // Sort by title

        try {
            // Query the ContentResolver
            context.contentResolver.query(
                collection,     // The collection URI to query
                projection,     // The columns to return
                selection,      // The selection criteria
                null,           // selectionArgs - No arguments needed for this selection
                sortOrder       // The sort order
            )?.use { cursor -> // 'use' ensures the cursor is closed automatically
                // Get the column indices
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                // Iterate over the results
                while (cursor.moveToNext()) {
                    // Get the values from the current row
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn) ?: "Unknown File"
                    val title = cursor.getString(titleColumn) ?: displayName // Use file name as fallback title
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist" // Fallback artist

                    // Construct the content URI for this audio file using its ID
                    val contentUri: Uri = ContentUris.withAppendedId(
                        collection, // Use the same base collection URI
                        id
                    )

                    // Create a Track object and add it to the list
                    trackList.add(Track(title, artist, contentUri))
                }
            }
        } catch (e: Exception) {
            // Handle potential exceptions, like SecurityException if permission is missing
            // Or IllegalArgumentException if a column doesn't exist (shouldn't happen with getColumnIndexOrThrow)
            android.util.Log.e("FileUtils", "Error querying MediaStore", e)
            // Optionally, inform the user or return an empty list gracefully
        }

        // Return the list of Track objects
        return trackList
    }
}