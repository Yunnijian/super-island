/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.superisland.source.lyric.hyperlyric.view

import io.github.superisland.source.lyric.hyperlyric.model.interfaces.IRichLyricLine

data class RichLyricLineModel(private val source: IRichLyricLine) : IRichLyricLine by source {
    var previous: RichLyricLineModel? = null
    var next: RichLyricLineModel? = null
}

