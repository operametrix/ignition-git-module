plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(project(":common"))

    modlImplementation("com.intellij:forms_rt:7.0.3")

    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:client-api:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:designer-api:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:vision-designer-api:${rootProject.extra["sdk_version"]}")

    // Provided at runtime by the Ignition Designer (bundled FlatLaf). compileOnly so it
    // isn't packaged into the .modl. Used for resolution-independent SVG icon rendering.
    compileOnly("com.formdev:flatlaf:3.6")
    compileOnly("com.formdev:flatlaf-extras:3.6")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}
