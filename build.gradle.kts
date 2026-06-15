plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
}

val cleanBuildReports by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("reports/problems"))
    delete(project(":app").layout.buildDirectory.dir("reports/problems"))
}

gradle.projectsEvaluated {
    allprojects {
        tasks.matching { it.name == "assembleDebug" }.configureEach {
            dependsOn(cleanBuildReports)
        }
    }
}
