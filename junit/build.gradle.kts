plugins {
  alias(libs.plugins.robolectric.deployed.java.module)
  alias(libs.plugins.robolectric.java.module)
}

dependencies {
  api(project(":annotations"))
  api(project(":sandbox"))
  api(project(":pluginapi"))
  api(project(":shadowapi"))
  api(project(":utils:reflector"))

  compileOnly(libs.findbugs.jsr305)
  compileOnly(libs.junit4)
}
