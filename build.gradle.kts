/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
  // this is necessary to avoid the plugins to be loaded multiple times
  // in each subproject's classloader
  alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.spotless)
}

spotless {
  val ktfmtVersion = libs.versions.ktfmt.get()

  kotlin {
    target("**/src/**/*.kt")
    targetExclude("**/build/**")
    ktfmt(ktfmtVersion).googleStyle()
    licenseHeaderFile(rootProject.file("license-header.txt"))
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktfmt(ktfmtVersion).googleStyle()
    licenseHeaderFile(rootProject.file("license-header.txt"), "(^(?![\\/ ]\\*).*$)")
  }
}
