pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "PaymentSolution"
include(":upi-intent-wrapper")
include(":sample")
project(":sample").projectDir = file("samples/android-app")
