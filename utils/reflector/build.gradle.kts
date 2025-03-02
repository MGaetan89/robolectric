plugins {
  alias(libs.plugins.robolectric.deployed.java.module)
  alias(libs.plugins.robolectric.java.module)
}

dependencies {
  api(libs.asm)
  api(libs.asm.commons)
  api(project(":utils"))

  testImplementation(project(":shadowapi"))
  testImplementation(libs.junit4)
  testImplementation(libs.truth)
}
