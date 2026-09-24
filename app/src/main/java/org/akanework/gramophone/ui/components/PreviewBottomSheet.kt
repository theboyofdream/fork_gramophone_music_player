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

package org.akanework.gramophone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.dispose
import coil3.size.Scale
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.ui.MainActivity
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class PreviewBottomSheet(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) :
    ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Player.Listener {
    private val activity
        get() = context as MainActivity
    private val instance: MediaController?
        get() = activity.getPlayer()
    private val bottomSheetPreviewCover: ImageView
    private val bottomSheetPreviewTitle: TextView
    private val bottomSheetPreviewSubtitle: TextView
    private val bottomSheetPreviewControllerButton: MaterialButton

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isHorizontalSwipe = false
    private val swipeDetector: GestureDetector

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        inflate(context, R.layout.preview_player, this)
        bottomSheetPreviewTitle = findViewById(R.id.preview_song_name)
        bottomSheetPreviewSubtitle = findViewById(R.id.preview_artist_name)
        bottomSheetPreviewCover = findViewById(R.id.preview_album_cover)
        bottomSheetPreviewControllerButton = findViewById(R.id.preview_control)

        bottomSheetPreviewControllerButton.setOnClickListener {
            ViewCompat.performHapticFeedback(it, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
            instance?.playOrPause()
        }

        swipeDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80f
            private val SWIPE_VELOCITY_THRESHOLD = 200f

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX > 0) {
                        ViewCompat.performHapticFeedback(this@PreviewBottomSheet, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                        instance?.seekToPrevious()
                    } else {
                        ViewCompat.performHapticFeedback(this@PreviewBottomSheet, HapticFeedbackConstantsCompat.CONTEXT_CLICK)
                        instance?.seekToNext()
                    }
                    return true
                }
                return false
            }
        })

        activity.controllerViewModel.addRecreationalPlayerListener(activity.lifecycle, this) {
            onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = ev.rawX
                touchStartY = ev.rawY
                isHorizontalSwipe = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(ev.rawX - touchStartX)
                val deltaY = abs(ev.rawY - touchStartY)
                if (deltaX > 30f && deltaX > deltaY) {
                    isHorizontalSwipe = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
                isHorizontalSwipe = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.rawX - touchStartX)
                val deltaY = abs(event.rawY - touchStartY)
                if (!isHorizontalSwipe && deltaX > 30f && deltaX > deltaY) {
                    isHorizontalSwipe = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        if (isHorizontalSwipe) {
            return swipeDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged(instance?.playbackState ?: Player.STATE_IDLE)
    }

    private var currentPlayTag: Int = 0

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) return
        if (instance?.isPlaying == true && currentPlayTag != 1) {
            bottomSheetPreviewControllerButton.icon =
                AppCompatResources.getDrawable(context, R.drawable.play_anim)
            bottomSheetPreviewControllerButton.icon.startAnimation()
            currentPlayTag = 1
        } else if (instance?.isPlaying == false && currentPlayTag != 2) {
            bottomSheetPreviewControllerButton.icon =
                AppCompatResources.getDrawable(context, R.drawable.pause_anim)
            bottomSheetPreviewControllerButton.icon.startAnimation()
            currentPlayTag = 2
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: @Player.MediaItemTransitionReason Int
    ) {
        try {
            if ((instance?.mediaItemCount ?: 0) > 0) {
                bottomSheetPreviewCover.dispose()
                val defaultCover = AppCompatResources.getDrawable(context, R.drawable.ic_default_cover)
                bottomSheetPreviewCover.setImageDrawable(defaultCover)
                val uri = mediaItem?.mediaMetadata?.artworkUri
                if (uri != null) {
                    bottomSheetPreviewCover.loadNoPlaceholder(uri) {
                        scale(Scale.FILL)
                    }
                }
                bottomSheetPreviewTitle.text = mediaItem?.mediaMetadata?.title ?: ""
                bottomSheetPreviewSubtitle.text =
                    mediaItem?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist)
            } else {
                bottomSheetPreviewCover.dispose()
                bottomSheetPreviewCover.setImageDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_default_cover)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PreviewBottomSheet", "Error in onMediaItemTransition", e)
        }
    }
}
