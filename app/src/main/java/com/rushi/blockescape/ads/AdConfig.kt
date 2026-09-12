package com.rushi.blockescape.ads

/**
 * Real AdMob banner ad unit ID (publisher account 5615007545017549), replacing the
 * placeholder Google test ID this project shipped with during development. Must stay in
 * sync with the `admob_app_id` string resource in app/src/main/res/values/strings.xml
 * (referenced from AndroidManifest.xml) — both must belong to the same AdMob publisher
 * account, or ad requests get rejected.
 *
 * IMPORTANT: never tap/click these live ads yourself during testing (on an emulator or a
 * real device) — Google treats self-clicks on real ad units as invalid traffic and can
 * suspend the AdMob account over it. Use Google's official test device ID mechanism
 * (AdRequest.Builder().addTestDevice(...) — see the AdMob docs) if on-device ad-rendering
 * verification is needed again before real users see this build.
 */
object AdConfig {
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-5615007545017549/9037745123"
}
