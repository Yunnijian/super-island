package io.github.superisland.source.screenrecord

/**
 * QS-tile entry for MediaProjection consent.
 *
 * Declared as `singleInstance` with an isolated taskAffinity so collapsing the tile does not
 * surface [io.github.superisland.MainActivity]. The in-module path uses
 * [ScreenRecordingCaptureActivity] in the same task to avoid status-bar/task flash.
 */
class ScreenRecordingTileCaptureActivity : ScreenRecordingCaptureActivity()
