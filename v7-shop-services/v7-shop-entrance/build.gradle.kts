plugins {
    id("java")
}

group = "cn.v7soft"
version = "0.0.1-SNAPSHOT"


dependencies {
    implementation(project(":v7-soft-core"))
    implementation(project(":v7-shop-dao"))
    implementation(project(":v7-shop-common"))
    implementation(project(":v7-shop-account-service"))
    implementation(project(":v7-shop-admin"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // poi
    implementation("org.apache.poi:poi-ooxml:5.2.3") // or a later version
    // pcap4j
    implementation("org.pcap4j:pcap4j-core:1.8.1")
    implementation("org.jsoup:jsoup:1.17.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}