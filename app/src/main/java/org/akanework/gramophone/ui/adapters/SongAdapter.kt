/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.ui.adapters

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.allowDiskAccessInStrictMode
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.logic.setMediaItemsSeamlessly
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.SongPickerActivity
import org.akanework.gramophone.ui.components.NowPlayingDrawable
import org.akanework.gramophone.ui.fragments.ArtistSubFragment
import org.akanework.gramophone.ui.fragments.DetailDialogFragment
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import org.akanework.gramophone.ui.fragments.SearchFragment
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.modifiedDate
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File
import java.util.GregorianCalendar

/**
 * [SongAdapter] is an adapter for displaying songs.
 */
class SongAdapter(
    val fragment: Fragment?,
    val queueTitle: Flow<String>?,
    songList: Flow<List<MediaItem>?> = (fragment?.requireContext() ?: fallbackContext!!)
        .gramophoneApplication.reader.songListFlow,
    helper: Sorter.NaturalOrderHelper<MediaItem>? = null,
    isSubFragment: Int? = null,
    allowDiffUtils: Boolean = false,
    rawOrderExposed: Sorter.Type? = if (isSubFragment == null) Sorter.Type.ByTitleAscending else null,
    val folder: Boolean = false,
    fallbackContext: AppCompatActivity? = null,
    val playlistId: Long? = null
) : BaseAdapter<MediaItem>
    (
    fragment,
    liveData = songList,
    sortHelper = MediaItemHelper,
    naturalOrderHelper = helper,
    initialSortType =
        (if (helper != null) Sorter.Type.NaturalOrder else (if (folder) Sorter.Type
            .ByFilePathAscending else rawOrderExposed ?: Sorter.Type.ByTitleAscending)),
    canSort = true,
    pluralStr = R.plurals.songs,
    defaultLayoutType = LayoutType.COMPACT_LIST,
    isSubFragment = isSubFragment,
    rawOrderExposed = rawOrderExposed,
    allowDiffUtils = allowDiffUtils,
    hasMenu = isSubFragment != R.id.songs,
    fallbackContext = fallbackContext
), SharedPreferences.OnSharedPreferenceChangeListener {

    init {
        lateInit()
    }

    fun getSongList() = list?.second ?: emptyList()

    fun getActivity() = mainActivity

    private var showFileNames = false
    private var idToPosMap: HashMap<String, List<Int?>>? = null
    private var currentMediaItem: String? = null
        set(value) {
            if (field != value) {
                val oldValue = field
                field = value
                if (idToPosMap != null) {
                    val oldPos = idToPosMap!![oldValue]
                    val newPos = idToPosMap!![value]
                    oldPos?.forEach {
                        it?.let {
                            notifyItemChanged(it, true)
                        }
                    }
                    newPos?.forEach {
                        it?.let {
                            notifyItemChanged(it, true)
                        }
                    }
                }
            }
        }
    private var currentIsPlaying: Boolean? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && currentMediaItem != null) {
                    idToPosMap?.get(currentMediaItem)?.forEach {
                        it?.let {
                            notifyItemChanged(it, false)
                        }
                    }
                }
            }
        }

    init {
        if (fragment != null) {
            val mediaControllerViewModel: MediaControllerViewModel by fragment.activityViewModels()
            mediaControllerViewModel.addRecreationalPlayerListener(
                fragment.viewLifecycleOwner.lifecycle,
                object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentMediaItem = mediaItem?.mediaId
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        currentIsPlaying =
                            playWhenReady &&
                                    mediaControllerViewModel.get()!!.playbackState != Player.STATE_ENDED
                                    && mediaControllerViewModel.get()!!.playbackState != Player.STATE_IDLE
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        currentIsPlaying =
                            mediaControllerViewModel.get()!!.playWhenReady
                                    && playbackState != Player.STATE_ENDED &&
                                    mediaControllerViewModel.get()!!.playbackState != Player.STATE_IDLE
                    }
                }
            ) {
                currentMediaItem = it.currentMediaItem?.mediaId
                currentIsPlaying =
                    it.playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (folder) {
            prefs.registerOnSharedPreferenceChangeListener(this)
            showFileNames = allowDiskAccessInStrictMode { prefs.getBooleanStrict("show_file_names", true) }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (folder)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if ((key == null || key == "show_file_names") && folder) {
            showFileNames = allowDiskAccessInStrictMode { this.prefs.getBooleanStrict("show_file_names", true) }
            notifyDataSetChanged()
        }
    }

    fun getPlayingSong(): Int? {
        return if (currentMediaItem != null) {
            idToPosMap?.get(currentMediaItem)?.firstOrNull()
        } else null
    }

    override fun onListUpdated() {
        // TODO run this method on a different thread / in advance
        idToPosMap = hashMapOf()
        list!!.second.forEachIndexed { i, item ->
            idToPosMap!![item.mediaId] = (idToPosMap!![item.mediaId] ?: emptyList()).plus(listOf(i))
        }
    }

    override fun virtualTitleOf(item: MediaItem): String {
        return "null"
    }

    override fun titleOf(item: MediaItem): String? {
        return if (showFileNames) item.getFile()?.name else super.titleOf(item)
    }

    val selectedItems = mutableSetOf<MediaItem>()
    var selectionMode = false
    private var actionMode: androidx.appcompat.view.ActionMode? = null

    fun toggleSelection(item: MediaItem, position: Int) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        notifyItemChanged(position, "selection_change")

        if (selectedItems.isEmpty()) {
            finishSelectionMode()
        } else {
            actionMode?.title = context.getString(R.string.selected_count, selectedItems.size)
        }
    }

    fun startSelectionMode(initialItem: MediaItem? = null) {
        if (selectionMode) return
        selectionMode = true
        selectedItems.clear()
        if (initialItem != null) {
            selectedItems.add(initialItem)
        }
        notifyItemRangeChanged(0, itemCount, "selection_change")

        actionMode = (getActivity() as? AppCompatActivity)?.startSupportActionMode(object : androidx.appcompat.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: androidx.appcompat.view.ActionMode, menu: android.view.Menu): Boolean {
                mode.menuInflater.inflate(R.menu.selection_menu, menu)
                mode.title = context.getString(R.string.selected_count, selectedItems.size)
                return true
            }

            override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: android.view.Menu): Boolean {
                val hasPlaylistId = (isSubFragment == R.id.playlist && playlistId != null)
                menu.findItem(R.id.action_remove_from_playlist)?.isVisible = hasPlaylistId
                return true
            }

            override fun onActionItemClicked(mode: androidx.appcompat.view.ActionMode, menuItem: android.view.MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_add_to_playlist -> {
                        if (selectedItems.isNotEmpty()) {
                            val selected = selectedItems.toList()
                            mainActivity.addToPlaylistDialog(selected) {
                                finishSelectionMode()
                            }
                        }
                        true
                    }
                    R.id.action_remove_from_playlist -> {
                        if (selectedItems.isNotEmpty() && playlistId != null) {
                            val selected = selectedItems.toList()
                            CoroutineScope(Dispatchers.Default).launch {
                                ItemManipulator.removeFromPlaylist(mainActivity, playlistId, selected)
                                withContext(Dispatchers.Main) {
                                    finishSelectionMode()
                                }
                            }
                        }
                        true
                    }
                    R.id.action_add_to_queue -> {
                        if (selectedItems.isNotEmpty()) {
                            val mediaController = mainActivity.getPlayer()
                            selectedItems.forEach {
                                mediaController?.addMediaItem(it)
                            }
                            Toast.makeText(context, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                            finishSelectionMode()
                        }
                        true
                    }
                    R.id.action_play_next -> {
                        if (selectedItems.isNotEmpty()) {
                            val mediaController = mainActivity.getPlayer()
                            val nextIdx = (mediaController?.currentMediaItemIndex ?: 0) + 1
                            selectedItems.forEachIndexed { i, item ->
                                mediaController?.addMediaItem(nextIdx + i, item)
                            }
                            Toast.makeText(context, R.string.play_next, Toast.LENGTH_SHORT).show()
                            finishSelectionMode()
                        }
                        true
                    }
                    R.id.action_delete -> {
                        if (selectedItems.isNotEmpty()) {
                            val selected = selectedItems.toList()
                            val pairs = selected.mapNotNull {
                                val file = it.getFile()
                                val id = it.requireMediaStoreId()
                                if (file != null) file to id else null
                            }
                            if (pairs.isNotEmpty()) {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.delete)
                                    .setMessage(context.getString(R.string.delete_selected_really, selected.size))
                                    .setPositiveButton(R.string.delete) { _, _ ->
                                        CoroutineScope(Dispatchers.Default).launch {
                                            val res = ItemManipulator.deleteSongs(mainActivity, pairs)
                                            withContext(Dispatchers.Main) {
                                                res?.invoke()
                                                finishSelectionMode()
                                            }
                                        }
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            }
                        }
                        true
                    }
                    R.id.action_select_all -> {
                        getSongList().let {
                            selectedItems.clear()
                            selectedItems.addAll(it)
                            notifyItemRangeChanged(0, itemCount, "selection_change")
                            mode.title = context.getString(R.string.selected_count, selectedItems.size)
                        }
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
                selectionMode = false
                selectedItems.clear()
                actionMode = null
                notifyItemRangeChanged(0, itemCount, "selection_change")
            }
        })
    }

    fun finishSelectionMode() {
        actionMode?.finish()
        actionMode = null
        selectionMode = false
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount, "selection_change")
    }

    override fun onLongClick(item: MediaItem, position: Int): Boolean {
        if (isSubFragment == R.id.songs) return false
        if (!selectionMode) {
            startSelectionMode(item)
            return true
        } else {
            toggleSelection(item, position)
            return true
        }
    }

    override fun onClick(item: MediaItem, position: Int) {
        if (selectionMode) {
            toggleSelection(item, position)
            return
        }
        if (isSubFragment == R.id.songs) {
            (context as SongPickerActivity).onSelected(item)
            return
        }
        val mediaController = mainActivity.getPlayer()
        val title = runBlocking { queueTitle!!.first() }
        mediaController?.apply {
            val songList = getSongList()
            // If the currently playing song is also the clicked song, then we continue playing the
            // song and open full player, but we still replace the list. This is intended to copy
            // UX of Chinese players that open full player when clicking song, and we don't want
            // this UX to break if list is different for some reason.
            val currentItem = currentMediaItem
            setMediaItemsSeamlessly(songList, position, title)
            prepare()
            play()
            if (currentItem?.mediaId == songList[position].mediaId && fragment !is SearchFragment) {
                mainActivity.playerBottomSheet.open()
            }
        }
    }

    override fun onMenu(item: MediaItem, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)
        if (isSubFragment == R.id.playlist && playlistId != null) {
            popupMenu.menu.findItem(R.id.remove_from_playlist)?.isVisible = true
        }

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItem(
                        mediaController.currentMediaItemIndex + 1,
                        item,
                    )
                    true
                }

                R.id.add_to_queue -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItem(
                        item,
                    )
                    true
                }

                R.id.album -> {
                    mainActivity.startFragment(GeneralSubFragment()) {
                        putString("Id", item.mediaMetadata.albumId?.toString())
                        putInt("Item", R.id.album)
                    }
                    true
                }

                R.id.artist -> {
                    mainActivity.startFragment(ArtistSubFragment()) {
                        putString("Id", item.mediaMetadata.artistId?.toString())
                        putInt("Item", R.id.artist)
                    }
                    true
                }

                R.id.details -> {
                    mainActivity.startFragment(DetailDialogFragment()) {
                        putString("Id", item.mediaId)
                    }
                    true
                }

                R.id.delete -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val res = ItemManipulator.deleteSongs(
                            mainActivity,
                            listOf(item.getFile()!! to item.requireMediaStoreId())
                        )
                        if (res != null) {
                            withContext(Dispatchers.Main) {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.delete)
                                    .setMessage(
                                        context.getString(
                                            R.string.delete_really,
                                            item.mediaMetadata.title
                                        )
                                    )
                                    .setPositiveButton(R.string.delete) { _, _ ->
                                        res.invoke()
                                    }
                                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                                    .show()
                            }
                        }
                    }
                    true
                }

                R.id.share -> {
                    val uri = item.requestMetadata.mediaUri
                        ?: item.localConfiguration?.uri
                        ?: return@setOnMenuItemClickListener true

                    val mimeType = item.localConfiguration?.mimeType ?: "audio/*"

                    try {
                        ShareCompat.IntentBuilder(mainActivity)
                            .setType(mimeType)
                            .setStream(uri)
                            .setChooserTitle("Share audio file")
                            .startChooser()
                    } catch (e: Exception) {
                        Toast.makeText(
                            mainActivity,
                            "Unable to share: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }

                R.id.add_to_playlist -> {
                    mainActivity.addToPlaylistDialog(item)
                    true
                }

                R.id.remove_from_playlist -> {
                    if (playlistId != null) {
                        CoroutineScope(Dispatchers.Default).launch {
                            ItemManipulator.removeFromPlaylist(mainActivity, playlistId, listOf(item))
                        }
                    }
                    true
                }

                R.id.select -> {
                    startSelectionMode(item)
                    true
                }

                else -> false
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val isSelected = selectionMode && position < getSongList().size && selectedItems.contains(getSongList()[position])
        if (isSelected) {
            holder.itemView.setBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    holder.itemView,
                    com.google.android.material.R.attr.colorSecondaryContainer
                )
            )
        } else {
            holder.itemView.background = null
        }

        if (payloads.isNotEmpty()) {
            if (payloads.contains("selection_change")) {
                return
            }
            if (payloads.none { it is Boolean && it }) {
                holder.nowPlaying.drawable?.level = if (currentIsPlaying == true) 1 else 0
                return
            }
            if (currentMediaItem == null || getSongList()[position].mediaId != currentMediaItem) {
                (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                    holder.nowPlaying.visibility = View.GONE
                    holder.nowPlaying.setImageDrawable(null)
                }
                holder.nowPlaying.drawable?.level = 2
                return
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
            if (currentMediaItem == null || getSongList()[position].mediaId != currentMediaItem)
                return
        }
        holder.nowPlaying.setImageDrawable(
            NowPlayingDrawable(context)
                .also { it.level = if (currentIsPlaying == true) 1 else 0 })
        holder.nowPlaying.visibility = View.VISIBLE
    }

    object MediaItemHelper : Sorter.Helper<MediaItem>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByAlbumTitleDescending, Sorter.Type.ByAlbumTitleAscending,
            Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAlbumArtistYearDescending, Sorter.Type.ByAlbumArtistYearAscending,
            Sorter.Type.ByAlbumYearDescending, Sorter.Type.ByAlbumYearAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending,
            Sorter.Type.ByDurationDescending, Sorter.Type.ByDurationAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByReleaseDateDescending, Sorter.Type.ByReleaseDateAscending,
            Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending,
            Sorter.Type.ByFilePathDescending, Sorter.Type.ByFilePathAscending,
            Sorter.Type.ByDiscAndTrack
        )
    ) {
        override fun getId(item: MediaItem): String {
            return item.mediaId
        }

        override fun getFile(item: MediaItem): File {
            return item.getFile()!!
        }

        override fun getTitle(item: MediaItem): String {
            return item.mediaMetadata.title.toString()
        }

        override fun getArtist(item: MediaItem): String? {
            return item.mediaMetadata.artist?.toString()
        }

        override fun getAlbumTitle(item: MediaItem): String {
            return item.mediaMetadata.albumTitle?.toString() ?: ""
        }

        override fun getAlbumArtist(item: MediaItem): String {
            return item.mediaMetadata.albumArtist?.toString() ?: ""
        }

        override fun getAlbumYear(item: MediaItem): Int? {
            return item.mediaMetadata.releaseYear
        }

        override fun getCover(item: MediaItem): Uri? {
            return item.mediaMetadata.artworkUri
        }

        override fun getDiscAndTrack(item: MediaItem): Int {
            return (item.mediaMetadata.discNumber ?: 0) * 1000 + (item.mediaMetadata.trackNumber
                ?: 0)
        }

        override fun getAddDate(item: MediaItem): Long {
            return item.mediaMetadata.addDate ?: -1
        }

        override fun getReleaseDate(item: MediaItem): Long {
            if (item.mediaMetadata.releaseYear == null && item.mediaMetadata.releaseMonth == null
                && item.mediaMetadata.releaseDay == null
            ) {
                return GregorianCalendar(
                    item.mediaMetadata.recordingYear ?: 0,
                    (item.mediaMetadata.recordingMonth ?: 1) - 1,
                    item.mediaMetadata.recordingDay ?: 0, 0, 0, 0
                )
                    .timeInMillis
            }
            return GregorianCalendar(
                item.mediaMetadata.releaseYear ?: 0,
                (item.mediaMetadata.releaseMonth ?: 1) - 1,
                item.mediaMetadata.releaseDay ?: 0, 0, 0, 0
            )
                .timeInMillis
        }

        override fun getSize(item: MediaItem): Int {
            return getFile(item).length().toInt()
        }

        override fun getDuration(item: MediaItem): Long {
            return item.mediaMetadata.durationMs ?: 0
        }

        override fun getModifiedDate(item: MediaItem): Long {
            return item.mediaMetadata.modifiedDate ?: -1
        }
    }
}
