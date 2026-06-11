import com.github.gradle.node.yarn.task.YarnTask
import com.github.gradle.node.npm.task.NpmTask

plugins {
    java
    id("com.github.node-gradle.node") version("7.0.2")
}

// Path to the generated-resources folder; webpack writes the UMD bundle into mounted/ below this.
val projectOutput: String by extra("$buildDir/generated-resources/")

node {
    version.set("18.0.0")
    yarnVersion.set("1.22.18")
    npmVersion.set("8.5.5")
    download.set(true)
    nodeProjectDir.set(file(project.projectDir))
}

// Install npm dependencies via yarn.
val yarnPackages by tasks.registering(YarnTask::class) {
    description = "Runs 'yarn install' at web-ui/ to install npm dependencies."
    args.set(listOf("install", "--verbose"))

    inputs.files(
        fileTree(project.projectDir).matching {
            include("**/package.json", "**/yarn.lock")
        }
    )
    outputs.dirs(file("node_modules"))

    dependsOn("${project.path}:yarn", ":web-ui:npmSetup")
}

// Build the React bundle with webpack.
val webpack by tasks.registering(NpmTask::class) {
    group = "Ignition Module"
    description = "Runs 'npm run build-dev' (webpack) to build the Config Versioning page bundle."
    args.set(listOf("run", "build-dev"))

    dependsOn(yarnPackages)

    inputs.files(project.fileTree(project.projectDir).matching {
        exclude("**/node_modules/**", "**/dist/**", "**/.awcache/**", "**/yarn-error.log")
    }.toList())

    outputs.files(fileTree(projectOutput))
}

tasks {
    processResources {
        dependsOn(webpack, yarnPackages)
    }
}

val deepClean by tasks.registering {
    doLast {
        delete(file(".gradle"))
        delete(file("node_modules"))
    }
    dependsOn(project.tasks.named("clean"))
}

// The gateway jar must include the built bundle, so block its processResources on webpack.
project(":gateway")?.tasks?.named("processResources")?.configure {
    dependsOn(webpack)
}

sourceSets {
    main {
        output.dir(projectOutput, "builtBy" to listOf(webpack))
    }
}
