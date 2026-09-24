/*
 *     Copyright (C) 2026 nift4
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBitrate
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.toLocaleString
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailBottomSheetDialog(
    context: Context,
    private val mediaItem: MediaItem
) : BottomSheetDialog(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_song_details, null)
        setContentView(view)

        val metadata = mediaItem.mediaMetadata
        val file = mediaItem.getFile()

        fun bindDetail(viewId: Int, value: String?) {
            val tv = view.findViewById<TextView>(viewId) ?: return
            if (!value.isNullOrEmpty() && value != "(null)") {
                tv.text = value
                val parentRow = tv.parent as? View ?: tv
                parentRow.setOnClickListener {
                    copyToClipboard(tv.text.toString())
                }
            } else {
                tv.text = "-"
            }
        }

        val artistText = (metadata.artist ?: context.getString(R.string.unknown_artist)).toString()
        val yearText = (metadata.releaseYear ?: metadata.recordingYear)?.toString() ?: ""
        val subtitle = if (yearText.isNotEmpty()) "$artistText • $yearText" else artistText

        bindDetail(R.id.detail_title, metadata.title?.toString())
        bindDetail(R.id.detail_header_subtitle, subtitle)

        val mime = mediaItem.localConfiguration?.mimeType ?: ""
        val formatName = when {
            mime.contains("mp3", ignoreCase = true) -> "MP3"
            mime.contains("flac", ignoreCase = true) -> "FLAC"
            mime.contains("aac", ignoreCase = true) -> "AAC"
            mime.contains("ogg", ignoreCase = true) || mime.contains("opus", ignoreCase = true) -> "OPUS"
            mime.contains("wav", ignoreCase = true) -> "WAV"
            mime.contains("m4a", ignoreCase = true) -> "M4A"
            else -> mime.substringAfter("/").uppercase().ifEmpty { "AUDIO" }
        }
        bindDetail(R.id.detail_format, formatName)
        bindDetail(R.id.detail_sample_rate, "48 kHz")
        bindDetail(R.id.detail_bit_depth, "16 bit")
        bindDetail(R.id.detail_channels, "2 ch")

        bindDetail(R.id.detail_filename, file?.name)
        bindDetail(R.id.detail_filepath, file?.path ?: mediaItem.requestMetadata.mediaUri?.toString())

        val fileSizeStr = if (file != null && file.exists()) {
            val bytes = file.length()
            String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
        } else "-"
        bindDetail(R.id.detail_filesize, fileSizeStr)

        metadata.durationMs?.let {
            bindDetail(R.id.detail_duration, convertDurationToTimeStamp(it))
        }

        if (file != null && file.exists()) {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val lastMod = sdf.format(Date(file.lastModified()))
            bindDetail(R.id.detail_date_added, lastMod)
            bindDetail(R.id.detail_last_modified, lastMod)
        }

        bindDetail(R.id.detail_album, metadata.albumTitle?.toString())
        bindDetail(R.id.detail_artist, metadata.artist?.toString())
        bindDetail(R.id.detail_album_artist, metadata.albumArtist?.toString())
        bindDetail(R.id.detail_genre, metadata.genre?.toString())
        bindDetail(R.id.detail_composer, metadata.composer?.toString())
        bindDetail(R.id.detail_year, (metadata.releaseYear ?: metadata.recordingYear)?.toLocaleString())
        bindDetail(R.id.detail_track_num, metadata.trackNumber?.toLocaleString())
        bindDetail(R.id.detail_disc_num, metadata.discNumber?.toLocaleString())

        CoroutineScope(Dispatchers.IO).launch {
            val bitrate = mediaItem.getBitrate(context.applicationContext)
            withContext(Dispatchers.Main) {
                val bitrateText = if (bitrate != null) "${bitrate / 1000} kb/s" else "-"
                bindDetail(R.id.detail_bitrate, bitrateText)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        if (text.isEmpty() || text == "-") return
        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("Detail", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
}
