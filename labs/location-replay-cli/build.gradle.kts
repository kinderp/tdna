plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared:location-replay"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("org.traveldna.lab.location.MainKt")
}
