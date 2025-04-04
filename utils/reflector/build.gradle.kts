plugins {
  alias(libs.plugins.robolectric.deployed.java.module)
  alias(libs.plugins.robolectric.java.module)
}

dependencies {
  api(libs.asm)
  api(libs.asm.commons)
  compileOnly(libs.findbugs.jsr305)
  api(project(":utils"))

  testImplementation(project(":shadowapi"))
  testCompileOnly(libs.findbugs.jsr305)
  testImplementation(libs.junit4)
  testImplementation(libs.truth)
}
