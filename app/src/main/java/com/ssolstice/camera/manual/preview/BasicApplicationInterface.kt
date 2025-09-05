package com.ssolstice.camera.manual.preview

import android.app.Activity
import android.graphics.Canvas
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Pair
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.ssolstice.camera.manual.cameracontroller.CameraController
import com.ssolstice.camera.manual.cameracontroller.CameraController.TonemapProfile
import com.ssolstice.camera.manual.cameracontroller.RawImage
import com.ssolstice.camera.manual.preview.ApplicationInterface.CameraResolutionConstraints
import com.ssolstice.camera.manual.preview.ApplicationInterface.NRModePref
import com.ssolstice.camera.manual.preview.ApplicationInterface.NoFreeStorageException
import com.ssolstice.camera.manual.preview.ApplicationInterface.RawPref
import com.ssolstice.camera.manual.preview.ApplicationInterface.VideoMaxFileSize
import com.ssolstice.camera.manual.preview.ApplicationInterface.VideoMethod
import java.util.Date

/** A partial implementation of ApplicationInterface that provides "default" implementations. So
 * sub-classing this is easier than implementing ApplicationInterface directly - you only have to
 * provide the unimplemented methods to get started, and can later override
 * BasicApplicationInterface's methods as required.
 * Note there is no need for your subclass of BasicApplicationInterface to call "super" methods -
 * these are just default implementations that should be overridden as required.
 */
abstract class BasicApplicationInterface : ApplicationInterface {
    override fun getLocation(): Location? {
        return null
    }

    override fun getCameraIdPref(): Int {
        return 0
    }

    override fun getCameraIdSPhysicalPref(): String? {
        return null
    }

    override fun getFlashPref(): String {
        return "flash_off"
    }

    override fun getFocusPref(is_video: Boolean): String {
        return "focus_mode_continuous_picture"
    }

    override fun isVideoPref(): Boolean {
        return false
    }

    override fun getSceneModePref(): String {
        return CameraController.SCENE_MODE_DEFAULT
    }

    override fun getColorEffectPref(): String {
        return CameraController.COLOR_EFFECT_DEFAULT
    }

    override fun getWhiteBalancePref(): String {
        return CameraController.WHITE_BALANCE_DEFAULT
    }

    override fun getWhiteBalanceTemperaturePref(): Int {
        return 0
    }

    override fun getAntiBandingPref(): String {
        return CameraController.ANTIBANDING_DEFAULT
    }

    override fun getEdgeModePref(): String {
        return CameraController.EDGE_MODE_DEFAULT
    }

    override fun getCameraNoiseReductionModePref(): String {
        return CameraController.NOISE_REDUCTION_MODE_DEFAULT
    }

    override fun getISOPref(): String {
        return CameraController.ISO_DEFAULT
    }

    override fun getExposureCompensationPref(): Int {
        return 0
    }

    override fun getCameraResolutionPref(constraints: CameraResolutionConstraints?): Pair<Int?, Int?>? {
        return null
    }

    override fun getImageQualityPref(): Int {
        return 90
    }

    override fun getFaceDetectionPref(): Boolean {
        return false
    }

    override fun getVideoQualityPref(): String? {
        return ""
    }

    override fun getVideoStabilizationPref(): Boolean {
        return false
    }

    override fun getForce4KPref(): Boolean {
        return false
    }

    override fun getRecordVideoOutputFormatPref(): String {
        return "preference_video_output_format_default"
    }

    override fun getVideoBitratePref(): String {
        return "default"
    }

    override fun getVideoFPSPref(): String {
        return "default"
    }

    override fun getVideoCaptureRateFactor(): Float {
        return 1.0f
    }

    override fun getVideoTonemapProfile(): TonemapProfile {
        return TonemapProfile.TONEMAPPROFILE_OFF
    }

    override fun getVideoLogProfileStrength(): Float {
        return 0f
    }

    override fun getVideoProfileGamma(): Float {
        return 0f
    }

    override fun getVideoMaxDurationPref(): Long {
        return 0
    }

    override fun getVideoRestartTimesPref(): Int {
        return 0
    }

    @Throws(NoFreeStorageException::class)
    override fun getVideoMaxFileSizePref(): VideoMaxFileSize {
        val video_max_filesize = VideoMaxFileSize()
        video_max_filesize.max_filesize = 0
        video_max_filesize.auto_restart = true
        return video_max_filesize
    }

    override fun getVideoFlashPref(): Boolean {
        return false
    }

    override fun getVideoLowPowerCheckPref(): Boolean {
        return true
    }

    override fun getPreviewSizePref(): String {
        return "preference_preview_size_wysiwyg"
    }

    override fun getLockOrientationPref(): String {
        return "none"
    }

    override fun getTouchCapturePref(): Boolean {
        return false
    }

    override fun getDoubleTapCapturePref(): Boolean {
        return false
    }

    override fun getPausePreviewPref(): Boolean {
        return false
    }

    override fun getShowToastsPref(): Boolean {
        return true
    }

    override fun getShutterSoundPref(): Boolean {
        return true
    }

    override fun getStartupFocusPref(): Boolean {
        return true
    }

    override fun getTimerPref(): Long {
        return 0
    }

    override fun getRepeatPref(): String {
        return "1"
    }

    override fun getRepeatIntervalPref(): Long {
        return 0
    }

    override fun getGeotaggingPref(): Boolean {
        return false
    }

    override fun getRequireLocationPref(): Boolean {
        return false
    }

    override fun getRecordAudioPref(): Boolean {
        return true
    }

    override fun getRecordAudioChannelsPref(): String {
        return "audio_default"
    }

    override fun getRecordAudioSourcePref(): String {
        return "audio_src_camcorder"
    }

    override fun getZoomPref(): Int {
        return -1
    }

    override fun getCalibratedLevelAngle(): Double {
        return 0.0
    }

    override fun canTakeNewPhoto(): Boolean {
        return true
    }

    override fun imageQueueWouldBlock(n_raw: Int, n_jpegs: Int): Boolean {
        return false
    }

    override fun getDisplayRotation(prefer_later: Boolean): Int {
        val activity = this.getContext() as Activity
        return activity.getWindowManager().getDefaultDisplay().getRotation()
    }

    override fun getExposureTimePref(): Long {
        return CameraController.EXPOSURE_TIME_DEFAULT
    }

    override fun getFocusDistancePref(isTargetDistance: Boolean): Float {
        return 0f
    }

    override fun isExpoBracketingPref(): Boolean {
        return false
    }

    override fun getExpoBracketingNImagesPref(): Int {
        return 3
    }

    override fun getExpoBracketingStopsPref(): Double {
        return 2.0
    }

    override fun getFocusBracketingNImagesPref(): Int {
        return 3
    }

    override fun getFocusBracketingAddInfinityPref(): Boolean {
        return false
    }

    override fun isFocusBracketingPref(): Boolean {
        return false
    }

    override fun isCameraBurstPref(): Boolean {
        return false
    }

    override fun getBurstNImages(): Int {
        return 5
    }

    override fun getBurstForNoiseReduction(): Boolean {
        return false
    }

    override fun getNRModePref(): NRModePref {
        return NRModePref.NRMODE_NORMAL
    }

    override fun isCameraExtensionPref(): Boolean {
        return false
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    override fun getCameraExtensionPref(): Int {
        return 0
    }

    override fun getAperturePref(): Float {
        return -1.0f
    }

    override fun getJpegRPref(): Boolean {
        return false
    }

    override fun getRawPref(): RawPref {
        return RawPref.RAWPREF_JPEG_ONLY
    }

    override fun getMaxRawImages(): Int {
        return 2
    }

    override fun useCamera2DummyCaptureHack(): Boolean {
        return false
    }

    override fun useCamera2FakeFlash(): Boolean {
        return false
    }

    override fun useCamera2FastBurst(): Boolean {
        return true
    }

    override fun usePhotoVideoRecording(): Boolean {
        return true
    }

    override fun isPreviewInBackground(): Boolean {
        return false
    }

    override fun allowZoom(): Boolean {
        return true
    }

    override fun optimiseFocusForLatency(): Boolean {
        return true
    }

    override fun isTestAlwaysFocus(): Boolean {
        return false
    }

    override fun cameraSetup() {
    }

    override fun touchEvent(event: MotionEvent?) {
    }

    override fun startingVideo() {
    }

    override fun startedVideo() {
    }

    override fun stoppingVideo() {
    }

    override fun stoppedVideo(video_method: VideoMethod?, uri: Uri?, filename: String?) {
    }

    override fun restartedVideo(video_method: VideoMethod?, uri: Uri?, filename: String?) {
    }

    override fun deleteUnusedVideo(video_method: VideoMethod?, uri: Uri?, filename: String?) {
    }

    override fun onFailedStartPreview() {
    }

    override fun onCameraError() {
    }

    override fun onPhotoError() {
    }

    override fun onVideoInfo(what: Int, extra: Int) {
    }

    override fun onVideoError(what: Int, extra: Int) {
    }

    override fun onVideoRecordStartError(profile: VideoProfile?) {
    }

    override fun onVideoRecordStopError(profile: VideoProfile?) {
    }

    override fun onFailedReconnectError() {
    }

    override fun onFailedCreateVideoFileError() {
    }

    override fun hasPausedPreview(paused: Boolean) {
    }

    override fun cameraInOperation(in_operation: Boolean, is_video: Boolean) {
    }

    override fun turnFrontScreenFlashOn() {
    }

    override fun cameraClosed() {
    }

    override fun timerBeep(remaining_time: Long) {
    }

    override fun multitouchZoom(new_zoom: Int) {
    }

    override fun requestTakePhoto() {
    }

    override fun setCameraIdPref(cameraId: Int, cameraIdSPhysical: String?) {
    }

    override fun setFlashPref(flash_value: String?) {
    }

    override fun setFocusPref(focus_value: String?, is_video: Boolean) {
    }

    override fun setFocusModePref(focus_value: String?) {

    }

    override fun getFocusModePref(): String? {
        return ""
    }

    override fun setVideoPref(is_video: Boolean) {
    }

    override fun setSceneModePref(scene_mode: String?) {
    }

    override fun clearSceneModePref() {
    }

    override fun setColorEffectPref(color_effect: String?) {
    }

    override fun clearColorEffectPref() {
    }

    override fun setWhiteBalancePref(white_balance: String?) {
    }

    override fun clearWhiteBalancePref() {
    }

    override fun setWhiteBalanceTemperaturePref(white_balance_temperature: Int) {
    }

    override fun setISOPref(iso: String?) {
    }

    override fun clearISOPref() {
    }

    override fun setExposureCompensationPref(exposure: Int) {
    }

    override fun clearExposureCompensationPref() {
    }

    override fun setCameraResolutionPref(width: Int, height: Int) {
    }

    override fun setVideoQualityPref(video_quality: String?) {
    }

    override fun setZoomPref(zoom: Int) {
    }

    override fun setExposureTimePref(exposure_time: Long) {
    }

    override fun clearExposureTimePref() {
    }

    override fun setFocusDistancePref(focus_distance: Float, is_target_distance: Boolean) {
    }

    override fun onDrawPreview(canvas: Canvas?) {
    }

    override fun onBurstPictureTaken(
        images: MutableList<ByteArray>?, current_date: Date?
    ): Boolean {
        return false
    }

    override fun onRawPictureTaken(raw_image: RawImage?, current_date: Date?): Boolean {
        return false
    }

    override fun onRawBurstPictureTaken(
        raw_images: MutableList<RawImage>?, current_date: Date?
    ): Boolean {
        return false
    }

    override fun onCaptureStarted() {
    }

    override fun onPictureCompleted() {
    }

    override fun onExtensionProgress(progress: Int) {
    }

    override fun onContinuousFocusMove(start: Boolean) {
    }
}
