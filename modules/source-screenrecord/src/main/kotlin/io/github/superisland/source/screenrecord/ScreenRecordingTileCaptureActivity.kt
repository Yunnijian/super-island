package io.github.superisland.source.screenrecord

/**
 * QS-tile entry for MediaProjection consent.
 *
 * Declared with an empty task affinity, so `FLAG_ACTIVITY_NEW_TASK` creates a disposable task
 * without attaching MainActivity. Avoiding `singleInstance` lets warsaw keep the status-bar
 * container through the consent handoff instead of briefly painting it black.
 */
class ScreenRecordingTileCaptureActivity : ScreenRecordingCaptureActivity()
