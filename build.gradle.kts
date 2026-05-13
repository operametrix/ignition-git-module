import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("io.ia.sdk.modl") version("0.4.1")
}

val sdk_version by extra("8.1.0")

val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"))

allprojects {
    version = "1.0.3.$timestamp"
}

ignitionModule {
    name.set("Git")
    fileName.set("Git.modl")
    id.set("com.operametrix.ignition.git")
    moduleVersion.set("${project.version}")
    moduleDescription.set("Embeds a Git client into the Ignition Designer for version-controlling project resources.")
    requiredIgnitionVersion.set(sdk_version)

    projectScopes.putAll(mapOf(
        ":common" to "DG",
        ":designer" to "D",
        ":gateway" to "G"
    ))

    moduleDependencies.set(mapOf<String, String>())

    hooks.putAll(mapOf(
        "com.operametrix.ignition.git.DesignerHook" to "D",
        "com.operametrix.ignition.git.GatewayHook" to "G"
    ))

    skipModlSigning.set(false)
}
