plugins {
    `java-platform`
}

group = "io.github.rajveer43"
version = "0.1.0-SNAPSHOT"

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":veloxquant-core"))
        api(project(":veloxquant-java"))
        api(project(":veloxquant-memory"))
        api(project(":veloxquant-optimize"))
        api(project(":veloxquant-runtime"))
        api(project(":veloxquant-system"))
        api(project(":veloxquant-monitor"))
    }
}
