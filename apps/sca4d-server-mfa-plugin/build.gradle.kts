import org.gradle.jvm.tasks.Jar
import java.util.jar.JarFile

plugins {
    `java-library`
    id("com.sca4dj.build.java")
    id("com.sca4dj.plugin")
}
version = "1.0.0"
dependencies {
    add("pluginApi", project(path = ":apps:sca4d-server", configuration = "pluginApiElements"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${libs.versions.jackson.asProvider().get()}")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val privateArtifacts = configurations.named("runtimeClasspath")
val mergedServices = layout.buildDirectory.dir("generated/mfa-private-services")
val mergeMfaPluginServices =
    tasks.register("mergeMfaPluginServices") {
        inputs.files(privateArtifacts)
        outputs.dir(mergedServices)
        doLast {
            val services = linkedMapOf<String, MutableSet<String>>()
            privateArtifacts.get().files.sortedBy { it.name }.forEach { artifact ->
                JarFile(artifact).use { jar ->
                    jar
                        .entries()
                        .asSequence()
                        .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") }
                        .forEach { entry ->
                            val providers = services.getOrPut(entry.name) { linkedSetOf() }
                            jar.getInputStream(entry).bufferedReader(Charsets.UTF_8).use {
                                providers.addAll(it.readLines())
                            }
                        }
                }
            }
            val destination = mergedServices.get().asFile
            delete(destination)
            services.forEach { (name, providers) ->
                val target = destination.resolve(name)
                target.parentFile.mkdirs()
                target.writeText(providers.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }
        }
    }

tasks.named<Jar>("pluginJar") {
    dependsOn(mergeMfaPluginServices)
    from(mergedServices)
    // 私有依赖的许可证分别保留，避免多个 Jackson 构件的同名资源互相覆盖。
    eachFile {
        if (path == "META-INF/LICENSE" || path == "META-INF/NOTICE") {
            val sourceFile = file
            val artifact = privateArtifacts.get().files.single { zipTree(it).contains(sourceFile) }
            path = "META-INF/licenses/${artifact.name}/$name"
        } else if (path.startsWith("META-INF/services/") && !file.toPath().startsWith(mergedServices.get().asFile.toPath())) {
            exclude()
        }
    }
}
