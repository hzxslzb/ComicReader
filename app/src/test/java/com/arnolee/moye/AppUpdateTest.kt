package com.arnolee.moye

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private fun release()=JSONObject().put("tag_name","v1.10.0").put("body","更新公告")
        .put("assets",JSONArray().put(JSONObject().put("name","ComicReader-1.10.0.apk")
            .put("browser_download_url","${AppRelease.REPO}/releases/download/v1.10.0/ComicReader-1.10.0.apk")
            .put("size",1024).put("digest","sha256:"+"a".repeat(64))))
    private fun rejected(json: JSONObject) { assertTrue(runCatching { AppRelease.parse(json.toString()) }.isFailure) }
    @Test fun versionsCompareNumericallyAndNeverOfferOlderOrSame() {
        assertTrue(UpdateVersions.newer("1.10.0","1.9.9"))
        assertTrue(UpdateVersions.newer("v2.0.0","1.99.99"))
        assertTrue(UpdateVersions.newer("1.3.5","1.3.4-dev"))
        assertFalse(UpdateVersions.newer("1.3.4","1.3.4"))
        assertFalse(UpdateVersions.newer("1.3.3","1.3.4"))
        assertFalse(UpdateVersions.newer("1.3.5-beta","1.3.4"))
        assertFalse(UpdateVersions.newer("99999999999999999999999.0.0","1.0.0"))
    }
    @Test fun officialReleaseSelectsInstallableApkAndNotes() {
        val parsed=AppRelease.parse(release().toString())
        assertEquals("1.10.0",parsed.version);assertEquals("更新公告",parsed.notes);assertEquals(1024L,parsed.size)
    }
    @Test fun draftsAndPrereleasesCannotBeOffered() {
        rejected(release().put("draft",true));rejected(release().put("prerelease",true))
    }
    @Test fun missingOrAmbiguousApkIsRejected() {
        rejected(release().put("assets",JSONArray()))
        val json=release();val assets=json.getJSONArray("assets");assets.put(assets.getJSONObject(0));rejected(json)
    }
    @Test fun WrongRepositoryHttpAndMissingDigestAreRejected() {
        for(url in listOf("http://github.com/hzxslzb/ComicReader/releases/download/v1.10.0/ComicReader-1.10.0.apk","https://github.com/other/repo/app.apk","https://example.com/app.apk")) {
            val json=release();json.getJSONArray("assets").getJSONObject(0).put("browser_download_url",url);rejected(json)
        }
        val json=release();json.getJSONArray("assets").getJSONObject(0).remove("digest");rejected(json)
    }
    @Test fun invalidSizesAndTagsAreRejected() {
        for(size in listOf(0L,-1L,151L*1024*1024)) { val json=release();json.getJSONArray("assets").getJSONObject(0).put("size",size);rejected(json) }
        rejected(release().put("tag_name","../1.10.0"))
    }
}
