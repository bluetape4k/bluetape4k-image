plugins {
    application
}

dependencies {
    implementation(project(":bluetape4k-images"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bluetape4k.junit5)
}

sourceSets {
    main {
        resources {
            srcDir("../../images/src/test/resources")
            include("images/cafe.jpg")
            include("images/landscape.jpg")
        }
        resources {
            srcDir("../../docs/images")
            include("image-workbench.png")
        }
    }
}

application {
    mainClass.set("io.bluetape4k.images.examples.basic.BasicImageProcessingQuickstartKt")
}
