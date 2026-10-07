plugins {
    `java-test-fixtures`
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}


dependencies {
    api(project(":bluetape4k-images"))

    // Coroutines
    compileOnly(bt4k.bluetape4k.coroutines)
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(bt4k.bluetape4k.junit5)

    testFixturesApi(project(":bluetape4k-images"))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
