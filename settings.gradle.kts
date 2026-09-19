pluginManagement {
    includeBuild("tools/sca4dj-build-gradle-plugin")
    includeBuild("tools/sca4dj-app-gradle-plugin")
    repositories {
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        maven(url = "https://maven.aliyun.com/repository/public")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "sca4d-j"
include("core:sca4dj-core-api")
include("core:sca4dj-core-spring")
include("modules:iam:sca4dj-iam")
include("modules:auth:sca4dj-auth")
include("modules:auth:sca4dj-auth-password")
include("modules:auth:sca4dj-auth-passkey")
include("modules:auth:sca4dj-auth-totp")
include("modules:auth:sca4dj-auth-email")
include("modules:auth:sca4dj-auth-sms")
include("modules:auth:sca4dj-auth-federation")
include("modules:auth:sca4dj-auth-shared-secret")
include("modules:auth:sca4dj-auth-hmac")
include("modules:auth:sca4dj-auth-x509")
include("modules:audit:sca4dj-audit")
include("modules:messaging:sca4dj-messaging")
include("modules:notification:sca4dj-notification")
include("modules:notification:sca4dj-notification-email")
include("modules:notification:sca4dj-notification-inbox")
include("modules:notification:sca4dj-notification-sms")
include("modules:notification:sca4dj-notification-sms-aliyun")
include("modules:job:sca4dj-job")
include("modules:scheduling:sca4dj-scheduling-quartz")
include("modules:web-realtime:sca4dj-web-realtime")
include("modules:iot:sca4dj-iot-api")
include("modules:iot:sca4dj-iot")
include("modules:iot:sca4dj-iot-emqx")
include("modules:iot:sca4dj-iot-timescale")
include("modules:storage:sca4dj-storage-file")
include("modules:storage:sca4dj-storage-local")
include("modules:storage:sca4dj-storage-minio")
include("modules:openapi:sca4dj-openapi")
include("tools")
include("apps:sca4d-iot-simulator")
include("apps:sca4d-server")
include("frontends:sca4d-admin")
include("gateway:sca4d-gateway")
