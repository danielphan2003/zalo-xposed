package io.github.danielphan2003.zaloxposed.features.ads

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsoredJsonTest {

    @Test
    fun topLevelTrackingMarksAnAd() {
        assertTrue(SponsoredJson.isSponsored(JSONObject("""{"id":"1","track_ads":{"imp":"https://x"}}""")))
    }

    @Test
    fun adFieldsInPhotosMarkAnAd() {
        val item = JSONObject("""{"id":"2","content":{"list_photos":[{"heading":"h"},{"adsUrl":"https://shop"}]}}""")
        assertTrue(SponsoredJson.isSponsored(item))
    }

    @Test
    fun organicPostIsKept() {
        val item = JSONObject("""{"id":"3","content":{"caption":"hi","list_photos":[{"heading":"h","info":"i"}]}}""")
        assertFalse(SponsoredJson.isSponsored(item))
    }

    @Test
    fun emptyAdFieldsDoNotCount() {
        val item = JSONObject("""{"id":"4","track_ads":{},"content":{"list_photos":[{"adsData":"","adsAction":null}]}}""")
        assertFalse(SponsoredJson.isSponsored(item))
    }
}
