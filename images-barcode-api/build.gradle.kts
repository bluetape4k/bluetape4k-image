dependencies {
    api(project(":bluetape4k-images"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
