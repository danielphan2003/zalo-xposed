package io.github.danielphan2003.zaloxposed.features.ads

import org.json.JSONArray
import org.json.JSONObject

/**
 * Recognises a sponsored item in Zalo's feed JSON. Zalo's parser reads ad fields from the item
 * itself (`track_ads`) and from its photos (`adsUrl`, `adsData`, `adsAction`, `track_ads`); an
 * organic post carries none of them.
 */
internal object SponsoredJson {

    private val AD_KEYS = setOf("track_ads", "adsUrl", "adsData", "adsAction")
    private const val MAX_DEPTH = 4

    fun isSponsored(item: JSONObject): Boolean = hasAdKey(item, 0)

    private fun hasAdKey(obj: JSONObject, depth: Int): Boolean {
        for (key in obj.keys()) {
            val value = obj.opt(key)
            if (key in AD_KEYS && !isEmpty(value)) return true
            if (depth < MAX_DEPTH && nestedHasAdKey(value, depth + 1)) return true
        }
        return false
    }

    private fun nestedHasAdKey(value: Any?, depth: Int): Boolean = when (value) {
        is JSONObject -> hasAdKey(value, depth)
        is JSONArray -> hasAdKey(value, depth)
        else -> false
    }

    private fun hasAdKey(array: JSONArray, depth: Int): Boolean {
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            if (value is JSONObject && hasAdKey(value, depth + 1)) return true
        }
        return false
    }

    private fun isEmpty(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> true
        is String -> value.isEmpty()
        is JSONObject -> value.length() == 0
        is JSONArray -> value.length() == 0
        else -> false
    }
}
