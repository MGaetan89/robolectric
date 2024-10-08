plugins {
  alias(libs.plugins.robolectric.deployed.java.module)
  alias(libs.plugins.robolectric.java.module)
  alias(libs.plugins.robolectric.shadows)
}

shadows {
  packageName = "org.robolectric.shadows.multidex"
  sdkCheckMode = "OFF"
}

dependencies {
  compileOnly(project(":shadows:framework"))
  api(project(":annotations"))

  compileOnly(AndroidSdk.MAX_SDK.coordinates)

  testImplementation(project(":robolectric"))
}
