plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    // Declared here only to put it on the build classpath; composeApp applies it, and only when a
    // google-services.json is actually present.
    alias(libs.plugins.googleServices) apply false
}
