package com.rushi.blockescape.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PrivacyOptionsStatus { Unknown, NotRequired, Required }

/**
 * The only door to the ad and consent SDKs. Implemented once over the real SDKs
 * ([AdMobConsentAdapter]); tests use a fake. Callbacks must arrive on the main thread. `ok` is
 * false when the SDK reported an error or no Activity was available.
 */
interface ConsentAdapter {
    fun canRequestAds(): Boolean
    fun privacyOptionsStatus(): PrivacyOptionsStatus
    fun requestInfoUpdate(onDone: (ok: Boolean) -> Unit)
    fun showFormIfRequired(onDone: (ok: Boolean) -> Unit)
    fun showPrivacyOptionsForm(onDone: (ok: Boolean) -> Unit)

    /** Initialises the ad SDK (once per process). */
    fun initializeAds(onDone: (ok: Boolean) -> Unit)
}

data class AdsState(
    val sdkInitialized: Boolean = false,
    val canRequestAds: Boolean = false,
    val privacyOptions: PrivacyOptionsStatus = PrivacyOptionsStatus.Unknown,
) {
    /** A banner is only created once consent allows ads AND the ad SDK is up. */
    val showBanner: Boolean get() = sdkInitialized && canRequestAds

    /** The Settings "Privacy options" row shows only when the SDK says it is required. */
    val privacyEntryVisible: Boolean get() = privacyOptions == PrivacyOptionsStatus.Required
}

/**
 * Consent to ads, in one place and free of Android types.
 *
 * Order, per Google's UMP guidance: refresh consent info at every launch, show the form if
 * required, initialise the ad SDK only when `canRequestAds()` is true (also straight away at
 * launch when consent was already gathered in an earlier session), and let the SDK's own answer,
 * not a guess, decide. The game never waits for any of this: it is fully playable with no banner.
 */
class AdsController(private val adapter: ConsentAdapter) {
    private val _state = MutableStateFlow(AdsState())
    val state: StateFlow<AdsState> = _state.asStateFlow()

    private var started = false
    private var initializing = false
    private var flowInProgress = false
    private var lastFlowFailed = false

    /** Call from the launch Activity. Safe to call again; later calls do nothing. */
    fun start() {
        if (started) return
        started = true
        flowInProgress = true
        refresh() // consent from an earlier session may already allow ads
        adapter.requestInfoUpdate { infoOk ->
            // Even if the update failed, a form may not be needed and earlier consent still counts.
            adapter.showFormIfRequired { formOk ->
                flowInProgress = false
                lastFlowFailed = !infoOk || !formOk
                refresh()
            }
        }
    }

    /**
     * Re-reads the consent state from the SDK. The Activity calls this on every resume: a form
     * callback can be lost when its Activity is recreated, and this brings the banner and the
     * Privacy options row back in line with the SDK's answer. If the last attempt failed
     * (offline, no Activity yet) the whole flow is retried.
     */
    fun refreshFromSdk() {
        if (!started) return
        if (lastFlowFailed && !flowInProgress) {
            started = false
            lastFlowFailed = false
            start()
            return
        }
        refresh()
    }

    /** Settings entry point. Does nothing unless the SDK reported that privacy options are required. */
    fun openPrivacyOptions() {
        if (!_state.value.privacyEntryVisible) return
        adapter.showPrivacyOptionsForm { _ -> refresh() }
    }

    private fun refresh() {
        val can = adapter.canRequestAds()
        val privacy = adapter.privacyOptionsStatus()
        _state.value = _state.value.copy(canRequestAds = can, privacyOptions = privacy)
        if (can && !_state.value.sdkInitialized && !initializing) {
            initializing = true
            try {
                adapter.initializeAds { ok ->
                    initializing = false // on failure the next refresh tries again
                    if (ok) _state.value = _state.value.copy(sdkInitialized = true)
                }
            } catch (e: RuntimeException) {
                initializing = false
            }
        }
    }
}
