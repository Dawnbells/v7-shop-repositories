plugins {
    id("java")
}

group = "cn.v7soft"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":v7-soft-core"))
    implementation(project(":v7-shop-dao"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    /** forest */
    implementation("com.dtflys.forest:forest-spring-boot3-starter:1.5.33")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
}

tasks.test {
    useJUnitPlatform()
}