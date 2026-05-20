// Foojay toolchain resolver — required since Gradle 8.4 when Java toolchain auto-provisioning is used
// (we ask for Java 21 in build.gradle.kts). Without this plugin, Gradle 10 will refuse to download
// toolchains from disco.foojay.io. Same plugin Folia itself uses in its build.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "DiscordSRV"
