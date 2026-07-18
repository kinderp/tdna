plugins {
    // Android-only plugins are resolved inside apps/android. Keeping them out of
    // the root plugin block preserves the foundation-only Gradle graph when the
    // Android module is excluded with -Ptdna.includeAndroid=false.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "org.traveldna"
    version = "0.1.0-SNAPSHOT"
}
