package com.mediascroll.app.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean
) : Parcelable
