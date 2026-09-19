plugins {
    application
    java
    id("com.sca4dj.build.java")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.boot.dependencies
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation(libs.hivemq.mqtt.client)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)
    implementation("ch.qos.logback:logback-classic")
    compileOnly(libs.jetbrains.annotations)
}

application {
    mainClass.set("com.sca4d.app.iot.simulator.IotDeviceSimulatorApplication")
}
