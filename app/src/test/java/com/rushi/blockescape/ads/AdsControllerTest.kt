package com.rushi.blockescape.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsControllerTest {

    /** A scripted stand-in for the Google SDKs; callbacks are held so tests control their timing. */
    private class FakeAdapter : ConsentAdapter {
        var can = false
        var privacy = PrivacyOptionsStatus.Unknown
        var initCalls = 0
        var infoCalls = 0
        var formCalls = 0
        var privacyFormCalls = 0
        var infoOk = true
        var formOk = true
        var onInfo: ((Boolean) -> Unit)? = null
        var onForm: ((Boolean) -> Unit)? = null
        var onInit: ((Boolean) -> Unit)? = null
        var onPrivacyForm: ((Boolean) -> Unit)? = null

        override fun canRequestAds() = can
        override fun privacyOptionsStatus() = privacy
        override fun requestInfoUpdate(onDone: (Boolean) -> Unit) { infoCalls++; onInfo = onDone }
        override fun showFormIfRequired(onDone: (Boolean) -> Unit) { formCalls++; onForm = onDone }
        override fun showPrivacyOptionsForm(onDone: (Boolean) -> Unit) { privacyFormCalls++; onPrivacyForm = onDone }
        override fun initializeAds(onDone: (Boolean) -> Unit) { initCalls++; onInit = onDone }
    }

    @Test
    fun `no banner and no ad sdk until consent allows ads`() {
        val a = FakeAdapter()
        val c = AdsController(a)
        c.start()
        assertFalse(c.state.value.showBanner)
        assertEquals(0, a.initCalls)
        // the form is shown and the player consents
        a.onInfo!!(true)
        a.can = true
        a.privacy = PrivacyOptionsStatus.Required
        a.onForm!!(true)
        assertEquals(1, a.initCalls)
        assertFalse("banner waits for the ad sdk", c.state.value.showBanner)
        a.onInit!!(true)
        assertTrue(c.state.value.showBanner)
        assertTrue(c.state.value.privacyEntryVisible)
    }

    @Test
    fun `while the sdk does not allow ads, no ad sdk and no banner`() {
        val a = FakeAdapter()
        val c = AdsController(a)
        c.start()
        a.onInfo!!(true)
        a.privacy = PrivacyOptionsStatus.Required
        a.onForm!!(true) // flow finished but the SDK still says no (e.g. form could not be shown)
        assertEquals(0, a.initCalls)
        assertFalse(c.state.value.showBanner)
        assertTrue("the player can still reopen the choice", c.state.value.privacyEntryVisible)
    }

    @Test
    fun `earlier consent starts ads at launch before any network answer`() {
        val a = FakeAdapter().apply { can = true; privacy = PrivacyOptionsStatus.Required }
        val c = AdsController(a)
        c.start()
        assertEquals(1, a.initCalls) // already at launch, while the info update is still pending
        a.onInit!!(true)
        assertTrue(c.state.value.showBanner)
    }

    @Test
    fun `ad sdk is initialised once even if consent is refreshed repeatedly`() {
        val a = FakeAdapter().apply { can = true }
        val c = AdsController(a)
        c.start()
        c.start()
        c.refreshFromSdk()
        c.refreshFromSdk()
        assertEquals(1, a.initCalls)
        a.onInit!!(true)
        a.onInfo!!(true)
        a.onForm!!(true)
        c.refreshFromSdk()
        assertEquals(1, a.initCalls)
    }

    @Test
    fun `failed init is retried on the next refresh`() {
        val a = FakeAdapter().apply { can = true }
        val c = AdsController(a)
        c.start()
        a.onInit!!(false)
        assertFalse(c.state.value.showBanner)
        c.refreshFromSdk()
        assertEquals(2, a.initCalls)
        a.onInit!!(true)
        assertTrue(c.state.value.showBanner)
    }

    @Test
    fun `offline launch retries the whole consent flow on the next resume`() {
        val a = FakeAdapter()
        val c = AdsController(a)
        c.start()
        a.onInfo!!(false) // update failed
        a.onForm!!(true)
        c.refreshFromSdk()
        assertEquals(2, a.infoCalls)
    }

    @Test
    fun `privacy options does nothing unless required, and the banner follows canRequestAds`() {
        val a = FakeAdapter().apply { can = true; privacy = PrivacyOptionsStatus.NotRequired }
        val c = AdsController(a)
        c.start()
        a.onInit!!(true)
        c.openPrivacyOptions()
        assertEquals(0, a.privacyFormCalls)

        a.privacy = PrivacyOptionsStatus.Required
        c.refreshFromSdk()
        assertTrue(c.state.value.privacyEntryVisible)
        c.openPrivacyOptions()
        assertEquals(1, a.privacyFormCalls)
        a.can = false // the SDK no longer allows ad requests
        a.onPrivacyForm!!(true)
        assertFalse(c.state.value.showBanner)
    }

    @Test
    fun `debuggable builds use the sample banner unit and release uses the real one`() {
        assertEquals(AdConfig.TEST_BANNER_AD_UNIT_ID, AdConfig.bannerUnitId(debuggable = true))
        assertEquals(AdConfig.BANNER_AD_UNIT_ID, AdConfig.bannerUnitId(debuggable = false))
    }
}
