package com.rushi.blockescape.ads

/**
 * ============================================================================================
 *  DEVELOPMENT-ONLY AD CONFIGURATION — READ BEFORE SHIPPING
 * ============================================================================================
 *
 *  [BANNER_AD_UNIT_ID] below is Google's OFFICIAL, PUBLICLY-DOCUMENTED **TEST** banner ad
 *  unit ID (developers.google.com/admob/android/test-ads — "Fixed Size Banner" row). It is
 *  NOT tied to any real AdMob account, is safe to commit, and always serves ads that are
 *  clearly and visibly labeled "Test Ad" — this is intentional and expected during
 *  development.
 *
 *  BEFORE ANY RELEASE BUILD OR PLAY STORE SUBMISSION, YOU MUST:
 *    1. Create a real AdMob account + app + banner ad unit at https://apps.admob.com
 *    2. Replace [BANNER_AD_UNIT_ID] below with the real ad unit ID from that console.
 *    3. Replace the `admob_app_id` string resource in
 *       app/src/main/res/values/strings.xml with the real AdMob App ID (it currently
 *       holds Google's matching public TEST App ID, referenced from AndroidManifest.xml).
 *
 *  Shipping a release build with test IDs still in place will only ever show test ads
 *  (fine, but earns no real revenue). Shipping a release build with a MISMATCHED real
 *  App ID / test ad unit ID (or vice versa) will get ad requests rejected. Change both
 *  places together.
 * ============================================================================================
 */
object AdConfig {

    /** Google's official test banner ad unit ID. See the class-level comment above. */
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111"
}
