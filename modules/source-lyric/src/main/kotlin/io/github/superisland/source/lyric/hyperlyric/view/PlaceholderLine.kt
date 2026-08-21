/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.superisland.source.lyric.hyperlyric.view

import io.github.superisland.source.lyric.hyperlyric.model.LyricLine
import io.github.superisland.source.lyric.hyperlyric.model.interfaces.IRichLyricLine
import io.github.superisland.source.lyric.hyperlyric.view.line.model.LyricModel

internal const val METADATA_TITLE_LINE = "TitleLine"
internal const val METADATA_COUNTDOWN_LINE = "CountdownLine"

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_TITLE_LINE, false) == true

fun IRichLyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricModel.isCountdownLine(): Boolean =
    metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true
