plugins {
  alias(libs.plugins.robolectric.deployed.java.module)
  alias(libs.plugins.robolectric.java.module)
  alias(libs.plugins.robolectric.shadows)
}

shadows {
  packageName = "org.robolectric.shadows.gms"
  sdkCheckMode = "OFF"
}

dependencies {
  compileOnly(project(":shadows:framework"))
  api(project(":annotations"))
  api(libs.guava)

  compileOnly(libs.bundles.play.services.`for`.shadows)
  compileOnly(AndroidSdk.MAX_SDK.coordinates)

  testCompileOnly(AndroidSdk.MAX_SDK.coordinates)
  testCompileOnly(libs.bundles.play.services.`for`.shadows)

  testImplementation(project(":robolectric"))
  testImplementation(libs.junit4)
  testImplementation(libs.truth)
  testImplementation(libs.mockito)
  testImplementation(libs.mockito.subclass)
  testRuntimeOnly(libs.bundles.play.services.`for`.shadows)
  testRuntimeOnly(AndroidSdk.MAX_SDK.coordinates)
}
