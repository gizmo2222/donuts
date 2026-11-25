package com.donuts.game

import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Guards the promise in docs\parent-promise.md and docs\privacy-policy.md: no network access, one permission
 * (vibrate), and no library that could collect or send data. A failure here means the policy and the Play
 * Data safety answers must be rewritten before the change ships; update this allow list after that.
 */
@RunWith(RobolectricTestRunner::class)
class PrivacyPromiseTest {

    private fun requestedPermissions(): Set<String> {
        val app = TestViews.app()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toSet() ?: emptySet()
    }

    @Test fun theMergedManifestAsksOnlyToVibrate() {
        val pkg = TestViews.app().packageName
        // androidx.core adds a signature permission of the app's own, guarding its own receivers; it grants nothing.
        val allowed = setOf("android.permission.VIBRATE", "$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val requested = requestedPermissions()
        assertTrue("asks to vibrate", "android.permission.VIBRATE" in requested)
        assertEquals("permissions beyond the privacy promise", emptySet<String>(), requested - allowed)
    }

    @Test fun nothingAsksForTheNetworkTheAdIdOrTheLocation() {
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )
        val requested = requestedPermissions()
        for (p in forbidden) assertFalse("$p breaks the privacy promise", p in requested)
    }

    @Test fun theAppDependsOnlyOnLibrariesThatSendNothing() {
        // Gradle runs unit tests from the module folder, so build.gradle is the app module's own.
        val gradle = File("build.gradle").readText()
        val deps = Regex("""^\s*implementation\s+['"]([^:'"]+:[^:'"]+)""", RegexOption.MULTILINE)
            .findAll(gradle).map { it.groupValues[1] }.toSet()
        val allowed = setOf(
            "androidx.core:core-ktx",
            "androidx.appcompat:appcompat",
            "com.google.android.material:material",
        )
        assertTrue("found the app's dependencies", "androidx.core:core-ktx" in deps)
        assertEquals("new libraries need the privacy policy reviewed", emptySet<String>(), deps - allowed)
        assertFalse("no platform() or project() dependencies to slip past the list",
            Regex("""^\s*implementation\s+(platform|project|files|fileTree)\b""", RegexOption.MULTILINE)
                .containsMatchIn(gradle))
    }
}
