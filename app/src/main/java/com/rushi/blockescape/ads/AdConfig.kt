package com.rushi.blockescape.ads

/**
 * AdMob banner ad unit ids. The release id belongs to publisher account 5615007545017549 and
 * must stay in sync with the `admob_app_id` string resource (referenced from AndroidManifest.xml):
 * both must belong to the same AdMob account or ad requests get rejected. Ad unit and app ids are
 * identifiers that ship inside every APK, not secrets; they have always lived in this repo.
 *
 * Debuggable builds use Google's public test banner unit, so nothing run during development can
 * ever count as a real impression or click on the live account. IMPORTANT: never tap real ads
 * yourself; Google treats self-clicks as invalid traffic.
 */
object AdConfig {
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-5615007545017549/9037745123"

    /** Google's documented sample banner unit; always returns clearly labelled test ads. */
    const val TEST_BANNER_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111"

    fun bannerUnitId(debuggable: Boolean): String =
        if (debuggable) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID
}
