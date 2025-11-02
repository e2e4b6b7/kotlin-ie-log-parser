plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup:kotlinpoet:1.18.1")
    implementation("com.charleskorn.kaml:kaml:0.97.0")
    implementation("schemas:schemas:1.0")
}

gradlePlugin {
    plugins {
        create("logParserCodegen") {
            id = "codegen"
            implementationClass = "codegen.LogParserCodegenPlugin"
        }
    }
}
