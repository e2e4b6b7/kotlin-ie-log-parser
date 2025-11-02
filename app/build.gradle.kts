plugins {
    kotlin("jvm") version "2.2.20"
    id("codegen")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("schemas:schemas:1.0")
    implementation("com.charleskorn.kaml:kaml:0.97.0")
}

codegen {
    dataDir.set(project.layout.projectDirectory.dir("../Logs"))
}

kotlin {
    jvmToolchain(21)
}
