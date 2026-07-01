import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("io.ia.sdk.modl") version("0.4.1")
}

// SDK jars to compile against (latest stable 8.3.x on IA Nexus).
val sdk_version by extra("8.3.6")

// Minimum gateway the module installs on. Decoupled from sdk_version: the migration
// only uses APIs present since 8.3.0, so any 8.3.x gateway can load this module.
val min_ignition_version = "8.3.0"

val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"))

allprojects {
    version = "2.0.2.$timestamp"
}

ignitionModule {
    name.set("Git")
    fileName.set("Git.modl")
    id.set("com.operametrix.ignition.git")
    moduleVersion.set("${project.version}")
    moduleDescription.set("Embeds a Git client into the Ignition Designer for version-controlling project resources.")
    license.set("license.html")
    requiredIgnitionVersion.set(min_ignition_version)

    projectScopes.putAll(mapOf(
        ":common" to "DG",
        ":designer" to "D",
        ":gateway" to "G"
    ))

    // Ignition 8.3+ dependency declaration (replaces moduleDependencies). Empty: no module deps.
    moduleDependencySpecs { }

    hooks.putAll(mapOf(
        "com.operametrix.ignition.git.DesignerHook" to "D",
        "com.operametrix.ignition.git.GatewayHook" to "G"
    ))

    skipModlSigning.set(true)
}
