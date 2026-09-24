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

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.allowDiskAccessInStrictMode
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.QuickLinearSmoothScroller
import org.akanework.gramophone.logic.utils.FilterRangeDialog
import org.akanework.gramophone.logic.queueWithTitle
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType

open class BaseDecorAdapter<T : AdapterFragment.BaseInterface<*>>(
    protected val adapter: T,
    private val pluralStr: Int
) : MyRecyclerView.Adapter<BaseDecorAdapter.ViewHolder>(), ItemHeightHelper {

    protected val context: Context = adapter.context
    private val dpHeight = context.resources.getDimensionPixelSize(R.dimen.decor_height)
    private var recyclerView: MyRecyclerView? = null
    private val prefs by lazy { allowDiskAccessInStrictMode { PreferenceManager.getDefaultSharedPreferences(context.applicationContext) } }
    var jumpUpPos: (() -> Int)? = null
    var jumpDownPos: (() -> Int)? = null
    var offsetPos: (() -> Int)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = adapter.layoutInflater.inflate(R.layout.general_decor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val count = adapter.itemCountForDecor
        holder.playAll.visibility =
            if (adapter is SongAdapter && adapter.isSubFragment != R.id.songs ||
                adapter is AlbumAdapter) View.VISIBLE else View.GONE
        holder.shuffleAll.visibility =
            if (adapter is SongAdapter && adapter.isSubFragment != R.id.songs ||
                adapter is AlbumAdapter) View.VISIBLE else View.GONE
        holder.counter.text = context.resources.getQuantityString(pluralStr, count, count)
        if (adapter is SongAdapter) {
            holder.counter.setOnClickListener {
                goToPlayingSong()
            }
        }
        holder.sortButton.visibility =
            if (adapter.sortType.value != Sorter.Type.None) View.VISIBLE else View.GONE
        holder.displayLayoutButton.visibility =
            if (adapter.canChangeLayout) View.VISIBLE else View.GONE
        updateSortButtons(holder)

        holder.displayLayoutButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(context, view)
            popupMenu.inflate(R.menu.layout_menu)
            val layoutMap = mapOf(
                Pair(R.id.list, BaseAdapter.LayoutType.LIST),
                Pair(R.id.compact_list, BaseAdapter.LayoutType.COMPACT_LIST),
                Pair(R.id.grid, BaseAdapter.LayoutType.GRID),
                Pair(R.id.compact_grid, BaseAdapter.LayoutType.COMPACT_GRID)
            )
            layoutMap.entries.find { it.value == adapter.layoutType }?.let {
                popupMenu.menu.findItem(it.key)?.isChecked = true
            }
            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId in layoutMap.keys) {
                    adapter.layoutType = layoutMap[menuItem.itemId]!!
                    menuItem.isChecked = true
                    allowDiskAccessInStrictMode {
                        prefs.edit {
                            putString(
                                "L" + getAdapterType(adapter).toString(),
                                layoutMap[menuItem.itemId].toString()
                            )
                        }
                    }
                    true
                } else false
            }
            popupMenu.show()
        }

        holder.sortButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(context, view)
            popupMenu.inflate(R.menu.sort_menu)
            val buttonMap = mapOf(
                Pair(R.id.natural, Sorter.Type.NaturalOrder),
                Pair(R.id.name, Sorter.Type.ByTitleAscending),
                Pair(R.id.artist, Sorter.Type.ByArtistAscending),
                Pair(R.id.artist_year, Sorter.Type.ByArtistYearAscending),
                Pair(R.id.album, Sorter.Type.ByAlbumTitleAscending),
                Pair(R.id.album_artist, Sorter.Type.ByAlbumArtistAscending),
                Pair(R.id.album_artist_year, Sorter.Type.ByAlbumArtistYearAscending),
                Pair(R.id.album_year, Sorter.Type.ByAlbumYearDescending),
                Pair(R.id.size, Sorter.Type.BySizeDescending),
                Pair(R.id.add_date, Sorter.Type.ByAddDateDescending),
                Pair(R.id.release_date, Sorter.Type.ByReleaseDateDescending),
                Pair(R.id.mod_date, Sorter.Type.ByModifiedDateDescending),
                Pair(R.id.file_path, Sorter.Type.ByFilePathAscending),
                Pair(R.id.duration, Sorter.Type.ByDurationDescending)
            )
            buttonMap.forEach {
                popupMenu.menu.findItem(it.key)?.isVisible = adapter.sortTypes.contains(it.value)
            }
            val currentSort = adapter.sortType.value
            val activeEntry = buttonMap.entries.find { it.value == currentSort || Sorter.Type.inverse(it.value) == currentSort }
            if (activeEntry != null) {
                popupMenu.menu.findItem(activeEntry.key)?.isChecked = true
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    in buttonMap.keys -> {
                        if (!menuItem.isChecked) {
                            val targetType = buttonMap[menuItem.itemId]!!
                            adapter.sort(targetType)
                            menuItem.isChecked = true
                            allowDiskAccessInStrictMode {
                                prefs.edit {
                                    putString(
                                        "S" + getAdapterType(adapter).toString(),
                                        targetType.toString()
                                    )
                                }
                            }
                            updateSortButtons(holder)
                        }
                        true
                    }

                    else -> onExtraMenuButtonPressed(menuItem)
                }
            }
            onSortButtonPressed(popupMenu)
            popupMenu.show()
        }
        holder.sortOrderButton.setOnClickListener {
            val currentType = adapter.sortType.value
            val inverseType = Sorter.Type.inverse(currentType) ?: return@setOnClickListener
            adapter.sort(inverseType)
            allowDiskAccessInStrictMode {
                prefs.edit {
                    putString(
                        "S" + getAdapterType(adapter).toString(),
                        inverseType.toString()
                    )
                }
            }
            updateSortButtons(holder)
        }
        holder.filterButton.setOnClickListener {
            showFilterDialog(holder)
        }
        holder.playAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val controller = adapter.getActivity().getPlayer()
                val songList = adapter.getSongList()
                controller?.apply {
                    setMediaItemsWithTitle(
                        songList,
                        title = runBlocking { adapter.queueTitle!!.first() },
                        shuffleEnabled = false,
                        repeatMode = REPEAT_MODE_OFF,
                    )
                    if (songList.isNotEmpty()) {
                        prepare()
                        play()
                    }
                }
            } else if (adapter is AlbumAdapter) {
                val list = adapter.getAlbumList()
                val controller = adapter.getActivity().getPlayer()
                controller?.apply {
                    list.takeIf { it.isNotEmpty() }?.also { albums ->
                        setMediaItemsWithTitle(
                            albums.flatMap { it.songList },
                            title = runBlocking { adapter.queueTitle.first() },
                            shuffleEnabled = false,
                            repeatMode = REPEAT_MODE_OFF,
                        )
                        prepare()
                        play()
                    } ?: setMediaItems(listOf())
                }
            }
        }
        holder.shuffleAll.setOnClickListener {
            ShortcutManagerCompat.reportShortcutUsed(context, "shuffle_all")
            val mainActivity = (context as MainActivity)
            val controller = mainActivity.getPlayer()
            val isShuffled = controller?.shuffleModeEnabled == true
            if (isShuffled) {
                controller?.shuffleModeEnabled = false
                holder.shuffleAll.isChecked = false
            } else {
                if (adapter is SongAdapter) {
                    val songList = adapter.getSongList()
                    controller?.apply {
                        setMediaItemsWithTitle(
                            songList,
                            title = runBlocking { adapter.queueTitle!!.first() },
                            shuffleEnabled = true,
                        )
                        if (songList.isNotEmpty()) {
                            prepare()
                            play()
                        }
                    }
                } else if (adapter is AlbumAdapter) {
                    val list = adapter.getAlbumList()
                    controller?.apply {
                        list.takeIf { it.isNotEmpty() }?.also { albums ->
                            setMediaItemsWithTitle(
                                albums.shuffled().flatMap { it.songList },
                                title = context.getString(R.string.shuffled,
                                        runBlocking { adapter.queueTitle.first() }),
                                shuffleEnabled = true,
                                repeatMode = REPEAT_MODE_OFF,
                            )
                            prepare()
                            play()
                        } ?: setMediaItems(listOf())
                    }
                } else {
                    controller?.shuffleModeEnabled = true
                }
                holder.shuffleAll.isChecked = true
            }
        }
        holder.jumpUp.visibility = if (jumpUpPos != null) View.VISIBLE else View.GONE
        holder.jumpUp.setOnClickListener {
            scrollToViewPosition(jumpUpPos!!())
        }
        holder.jumpDown.visibility = if (jumpDownPos != null) View.VISIBLE else View.GONE
        holder.jumpDown.setOnClickListener {
            scrollToViewPosition(jumpDownPos!!())
        }
    }

    fun goToPlayingSong() {
        if (adapter is SongAdapter) {
            adapter.getPlayingSong()?.let { scrollToViewPosition(
                (offsetPos?.invoke() ?: 0) + itemCount + it) }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.sortButton.setOnClickListener(null)
        holder.sortOrderButton.setOnClickListener(null)
        holder.playAll.setOnClickListener(null)
        holder.shuffleAll.setOnClickListener(null)
        holder.jumpUp.setOnClickListener(null)
        holder.jumpDown.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun scrollToViewPosition(pos: Int) {
        val smoothScroller = object : QuickLinearSmoothScroller(context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return (super.calculateDtToFit(
                    viewStart,
                    viewEnd,
                    boxStart,
                    boxEnd,
                    snapPreference
                )) + (viewEnd - viewStart) / 2
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        smoothScroller.targetPosition = pos
        recyclerView?.startSmoothScrollCompat(smoothScroller)
    }

    protected open fun onSortButtonPressed(popupMenu: PopupMenu) {}
    protected open fun onExtraMenuButtonPressed(menuItem: MenuItem): Boolean = false

    private fun isAscending(type: Sorter.Type): Boolean {
        return type.name.endsWith("Ascending")
    }

    private fun showFilterDialog(holder: ViewHolder) {
        if (adapter !is BaseAdapter<*>) return
        val baseAdapter = adapter as BaseAdapter<*>
        val currentType = adapter.sortType.value
        if (currentType == Sorter.Type.BySizeAscending || currentType == Sorter.Type.BySizeDescending) {
            val maxSize = baseAdapter.getMaxSize()
            val current = baseAdapter.filterRange.value
            FilterRangeDialog.showSizeFilter(
                context, 0f, maxSize,
                current?.min ?: 0f, current?.max ?: maxSize,
                onApply = { min, max ->
                    baseAdapter.setFilterRange(min, max)
                    updateSortButtons(holder)
                },
                onReset = {
                    baseAdapter.clearFilter()
                    updateSortButtons(holder)
                }
            )
        } else if (currentType == Sorter.Type.ByDurationAscending || currentType == Sorter.Type.ByDurationDescending) {
            val maxDuration = baseAdapter.getMaxDuration()
            val current = baseAdapter.filterRange.value
            FilterRangeDialog.showDurationFilter(
                context, 0f, maxDuration,
                current?.min ?: 0f, current?.max ?: maxDuration,
                onApply = { min, max ->
                    baseAdapter.setFilterRange(min, max)
                    updateSortButtons(holder)
                },
                onReset = {
                    baseAdapter.clearFilter()
                    updateSortButtons(holder)
                }
            )
        }
    }

    private fun updateSortButtons(holder: ViewHolder) {
        val currentType = adapter.sortType.value
        val canToggle = currentType != Sorter.Type.None
                && Sorter.Type.inverse(currentType) != null
        holder.sortOrderButton.visibility = if (canToggle) View.VISIBLE else View.GONE
        if (canToggle) {
            holder.sortOrderButton.icon = ResourcesCompat.getDrawable(
                context.resources,
                if (isAscending(currentType)) R.drawable.baseline_arrow_upward_24
                else R.drawable.baseline_arrow_downward_24,
                context.theme
            )
            holder.sortOrderButton.tooltipText = context.getString(R.string.sort_order)
        }
        val isFilterable = currentType == Sorter.Type.BySizeAscending
                || currentType == Sorter.Type.BySizeDescending
                || currentType == Sorter.Type.ByDurationAscending
                || currentType == Sorter.Type.ByDurationDescending
        holder.filterButton.visibility = if (isFilterable) View.VISIBLE else View.GONE
        val hasFilter = isFilterable && (adapter as? BaseAdapter<*>)?.filterRange?.value != null
        holder.filterBadge.visibility = if (hasFilter) View.VISIBLE else View.GONE
        holder.shuffleAll.isChecked = (context as? MainActivity)?.getPlayer()?.shuffleModeEnabled == true
    }

    override fun getItemCount(): Int = 1
    override fun getItemViewType(position: Int): Int = R.layout.general_decor

    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val sortButton: MaterialButton = view.findViewById(R.id.sort)
        val displayLayoutButton: MaterialButton = view.findViewById(R.id.display_layout)
        val sortOrderButton: MaterialButton = view.findViewById(R.id.sort_order)
        val filterButton: MaterialButton = view.findViewById(R.id.filter)
        val filterBadge: View = view.findViewById(R.id.filter_badge)
        val createPlaylist: MaterialButton = view.findViewById(R.id.create_playlist)
        val playAll: MaterialButton = view.findViewById(R.id.play_all)
        val shuffleAll: MaterialButton = view.findViewById(R.id.shuffle_all)
        val jumpUp: MaterialButton = view.findViewById(R.id.jumpUp)
        val jumpDown: MaterialButton = view.findViewById(R.id.jumpDown)
        val counter: TextView = view.findViewById(R.id.song_counter)
    }

    fun updateSongCounter() {
        notifyItemChanged(0)
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        return if (to > 0) dpHeight else 0
    }
}
