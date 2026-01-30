rootProject.name = "v7-shop-services"

pluginManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

include("v7-soft-core")
include("v7-shop-dao")
include("v7-shop-common")
include("v7-shop-account-service")
include("v7-shop-admin")
include("v7-shop-entrance")


project(":v7-soft-core").projectDir =
    File("E:\\V7Soft\\Workspace\\v7-soft-core\\sources\\v7-soft-repositories\\v7-soft-core\\")