package io.github.superisland.source.lyric

import org.json.JSONObject

object LyricPayloadBuilder {
    // OS3/OS4 single miui.focus path per 223ab0e evidence
    fun buildFocusLyricJson(line: LyricLine): String {
        val paramIsland = JSONObject()
            .put("islandProperty", 1)
            .put("islandPriority", 1)
            .put("islandOrder", false)
            .put("bigIslandArea", JSONObject().put("imageTextInfoLeft", JSONObject().put("textInfo", JSONObject().put("title", line.text))))
            .put("smallIslandArea", JSONObject().put("textInfo", JSONObject().put("title", line.text)))
        val paramV2 = JSONObject()
            .put("protocol", 1)
            .put("business", "super_island_lyric")
            .put("param_island", paramIsland)
        return JSONObject().put("param_v2", paramV2).toString()
    }
}
