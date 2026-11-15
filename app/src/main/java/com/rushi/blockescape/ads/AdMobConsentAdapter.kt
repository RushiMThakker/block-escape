package com.rushi.blockescape.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * The one class that talks to the Google Mobile Ads and User Messaging Platform SDKs; every
 * decision lives in [AdsController]. Callbacks arrive on the main thread.
 *
 * [activity] supplies the current Activity (UMP needs one). [forceEeaGeography] is a debug-only
 * switch (the Activity only ever sets it for debuggable builds) that makes UMP behave as if the
 * player were in the EEA. [testDeviceIds] are the hashed ids of the owner's phones: they get test
 * ads, and the UMP debug settings apply to them.
 */
class AdMobConsentAdapter(
    private val appContext: Context,
    private val activity: () -> Activity?,
    private val forceEeaGeography: () -> Boolean,
    private val testDeviceIds: List<String>,
) : ConsentAdapter {

    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val main = Handler(Looper.getMainLooper())

    override fun canRequestAds(): Boolean = info.canRequestAds()

    override fun privacyOptionsStatus(): PrivacyOptionsStatus = when (info.privacyOptionsRequirementStatus) {
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED -> PrivacyOptionsStatus.Required
        ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED -> PrivacyOptionsStatus.NotRequired
        else -> PrivacyOptionsStatus.Unknown
    }

    override fun requestInfoUpdate(onDone: (Boolean) -> Unit) {
        val act = activity() ?: return onDone(false)
        val params = ConsentRequestParameters.Builder().apply {
            if (forceEeaGeography()) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(appContext)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .apply { testDeviceIds.forEach { addTestDeviceHashedId(it) } }
                        .build(),
                )
            }
        }.build()
        info.requestConsentInfoUpdate(
            act, params,
            { onDone(true) },
            { error ->
                Log.w(TAG, "consent info update failed: ${error.errorCode} ${error.message}")
                onDone(false)
            },
        )
    }

    override fun showFormIfRequired(onDone: (Boolean) -> Unit) {
        val act = activity() ?: return onDone(false)
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(act) { error ->
            if (error != null) Log.w(TAG, "consent form error: ${error.errorCode} ${error.message}")
            onDone(error == null)
        }
    }

    override fun showPrivacyOptionsForm(onDone: (Boolean) -> Unit) {
        val act = activity() ?: return onDone(false)
        UserMessagingPlatform.showPrivacyOptionsForm(act) { error ->
            if (error != null) Log.w(TAG, "privacy options error: ${error.errorCode} ${error.message}")
            onDone(error == null)
        }
    }

    override fun initializeAds(onDone: (Boolean) -> Unit) {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build(),
        )
        // Google recommends initialising off the main thread; the completion callback is posted back.
        Thread {
            try {
                MobileAds.initialize(appContext) { main.post { onDone(true) } }
            } catch (e: Exception) {
                Log.w(TAG, "ad SDK initialisation failed: ${e.message}")
                main.post { onDone(false) }
            }
        }.start()
    }

    private companion object {
        const val TAG = "BlockEscapeAds"
    }
}
