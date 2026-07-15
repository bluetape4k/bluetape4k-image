plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":bluetape4k-images"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)

    testFixturesApi(project(":bluetape4k-images"))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
