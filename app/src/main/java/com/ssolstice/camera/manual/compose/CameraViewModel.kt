package com.ssolstice.camera.manual.compose

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssolstice.camera.manual.models.ResolutionModel
import com.ssolstice.camera.manual.models.SettingItemModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    // resolution
    val resolutionss =
        arrayListOf(
            ResolutionModel("1", "1920x1080", "16:9"),
            ResolutionModel("1920x1080", "1920x1080", "16:9"),
            ResolutionModel("1920x1080", "1920x1080", "16:9"),
            ResolutionModel("1920x1080", "1920x1080", "16:9"),
            ResolutionModel("1920x1080", "1920x1080", "16:9"),
            ResolutionModel("1920x1080", "1920x1080", "16:9"),
        )
    private val _resolutions = MutableStateFlow(arrayListOf<ResolutionModel>())
    val resolutions: StateFlow<ArrayList<ResolutionModel>> = _resolutions

    fun setResolution(resolution: ArrayList<ResolutionModel>) {
        viewModelScope.launch {
            _resolutions.value = resolution
        }
    }

    // repeats
    val repeatss =
        arrayListOf(
            SettingItemModel("Off", "Off", 0),
            SettingItemModel("2x", "2x", 2),
            SettingItemModel("3x", "3x", 3),
            SettingItemModel("4x", "4x", 4),
            SettingItemModel("5x", "5x", 5),
            SettingItemModel("10x", "10x", 10),
            SettingItemModel("20x", "20x", 20),
            SettingItemModel("30x", "30x", 30),
            SettingItemModel("40x", "40x", 40),
            SettingItemModel("50x", "50x", 50),
            SettingItemModel("100x", "100x", 100),
            SettingItemModel("200x", "200x", 200),
            SettingItemModel("500x", "500x", 500),
        )

    private val _repeats = MutableStateFlow(arrayListOf<SettingItemModel>())
    val repeats: StateFlow<ArrayList<SettingItemModel>> = _repeats

    fun setRepeat(repeat: ArrayList<SettingItemModel>) {
        viewModelScope.launch {
            _repeats.value = repeat
        }
    }

    // timers
    val timerss =
        arrayListOf(
            SettingItemModel("Off", "Off", 0),
            SettingItemModel("1s", "1s", 2),
            SettingItemModel("3s", "3s", 3),
            SettingItemModel("4s", "4s", 4),
            SettingItemModel("5s", "5s", 5),
            SettingItemModel("10s", "10s", 10),
            SettingItemModel("20s", "20s", 20),
            SettingItemModel("30s", "30s", 30),
            SettingItemModel("40s", "40s", 40),
            SettingItemModel("50s", "50s", 50),
            SettingItemModel("100s", "100s", 100),
            SettingItemModel("200s", "200s", 200),
            SettingItemModel("500s", "500s", 500),
        )

    private val _timers = MutableStateFlow(arrayListOf<SettingItemModel>())
    val timers: StateFlow<ArrayList<SettingItemModel>> = _timers

    fun setTimer(timer: ArrayList<SettingItemModel>) {
        viewModelScope.launch {
            _timers.value = timer
        }
    }

    // speeds
    val speeds =
        arrayListOf(
            SettingItemModel("Off", "Off", 0),
            SettingItemModel("2x", "2x", 2),
            SettingItemModel("3x", "3x", 3),
            SettingItemModel("4x", "4x", 4),
            SettingItemModel("5x", "5x", 5),
            SettingItemModel("10x", "10x", 10),
            SettingItemModel("20x", "20x", 20),
            SettingItemModel("30x", "30x", 30),
            SettingItemModel("40x", "40x", 40),
            SettingItemModel("50x", "50x", 50),
            SettingItemModel("100x", "100x", 100),
            SettingItemModel("200x", "200x", 200),
            SettingItemModel("500x", "500x", 500),
        )
    private val _speed = MutableStateFlow(arrayListOf<SettingItemModel>())
    val speed: StateFlow<ArrayList<SettingItemModel>> = _speed
    fun setSpeed(speed: ArrayList<SettingItemModel>) {
        viewModelScope.launch {
            _speed.value = speed
        }
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPhotoMode = MutableStateFlow(false)
    val isPhotoMode: StateFlow<Boolean> = _isPhotoMode

    private val _isVideoRecordingPaused = MutableStateFlow(false)
    val isVideoRecordingPaused: StateFlow<Boolean> = _isVideoRecordingPaused

    fun setVideoRecordingPaused(isPaused: Boolean) {
        viewModelScope.launch {
            _isVideoRecordingPaused.value = isPaused
        }
    }

    fun setPhotoMode(isPhotoMode: Boolean) {
        viewModelScope.launch {
            _isPhotoMode.value = isPhotoMode
        }
    }

    fun setVideoRecording(isRecording: Boolean) {
        viewModelScope.launch {
            _isRecording.value = isRecording
        }
    }

    private val _galleryBitmap = MutableStateFlow<Bitmap?>(null)
    val galleryBitmap: StateFlow<Bitmap?> = _galleryBitmap

    fun setGalleryBitmap(galleryBitmap: Bitmap?) {
        viewModelScope.launch {
            _galleryBitmap.value = galleryBitmap
        }
    }
}