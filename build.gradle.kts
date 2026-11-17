// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    // Only sets the Kotlin version of AGP's built-in Kotlin: Firebase is compiled with Kotlin 2.4
    alias(libs.plugins.kotlin.android) apply false
}
