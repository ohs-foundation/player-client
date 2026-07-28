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
package dev.ohs.player.client.scaffold

import androidx.compose.foundation.text.BasicText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.player.client.registry.LocalViewRegistry
import dev.ohs.player.client.registry.ViewRegistry
import dev.ohs.player.client.registry.ViewType
import dev.ohs.player.client.registry.registerComponent
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.RenderOptions
import kotlin.test.Test

private val SectionA = ViewType("SectionA")
private val SectionB = ViewType("SectionB")
private val SectionC = ViewType("SectionC")

private data object DetailTestConfig

private class LabeledRenderer(private val label: String) :
  ComponentRenderer<String, DetailTestConfig> {
  @Composable
  override fun Render(item: String, config: DetailTestConfig, options: RenderOptions) {
    Text("[$label] $item")
  }
}

@OptIn(ExperimentalTestApi::class)
class DetailScaffoldTest {

  @Test
  fun nullItem_showsNotFound_andSkipsSections() = runComposeUiTest {
    val registry =
      ViewRegistry().apply { registerComponent(SectionA, LabeledRenderer("A"), DetailTestConfig) }
    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        DetailScaffold<String>(item = null) {
          section(SectionA)
          notFound { Text("missing") }
        }
      }
    }
    onNodeWithText("missing").assertIsDisplayed()
    // The section renderer would emit "[A] x" if invoked; assert no such node exists.
    onAllNodesWithText("[A] x").assertCountEquals(0)
  }

  @Test
  fun sections_renderInDeclaredOrder() = runComposeUiTest {
    val registry =
      ViewRegistry().apply {
        registerComponent(SectionA, LabeledRenderer("A"), DetailTestConfig)
        registerComponent(SectionB, LabeledRenderer("B"), DetailTestConfig)
        registerComponent(SectionC, LabeledRenderer("C"), DetailTestConfig)
      }
    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        DetailScaffold<String>(item = "x") {
          section(SectionA)
          section(SectionB)
          section(SectionC)
        }
      }
    }
    onNodeWithText("[A] x").assertIsDisplayed()
    onNodeWithText("[B] x").assertIsDisplayed()
    onNodeWithText("[C] x").assertIsDisplayed()
  }
}
