plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.plugin")
}
version = "1.0.0"
dependencies {
    add("pluginApi", project(path = ":apps:sca4d-server", configuration = "pluginApiElements"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
sca4djPlugin {
    id.set("SCA4D:SERVER:MFA")
    entryClass.set("com.sca4d.app.mfa.MfaPolicyPlugin")
    api.set("SCA4D:SERVER")
    requiresApi.set(">=1.0.0 & <2.0.0")
}
