plugins {
    id("java")
    id("org.springframework.boot") version "3.3.0"
}

group = "cn.v7soft"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":v7-soft-core"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

}
tasks.bootJar {
    enabled = false // 禁用 bootJar 任务
}
tasks.test {
    useJUnitPlatform()
}