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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.player.client.registry.LocalViewRegistry
import dev.ohs.player.client.registry.ViewRegistry
import dev.ohs.player.client.registry.ViewType
import dev.ohs.player.client.registry.registerComponent
import dev.ohs.player.client.registry.registerLayout
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.ConfiguredRenderer
import dev.ohs.player.client.renderer.LayoutRenderer
import dev.ohs.player.client.renderer.RenderOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

private val TextItemViewType = ViewType("TextItem")
private val PlainListViewType = ViewType("PlainList")

private data object ListTestConfig

private class TextRenderer : ComponentRenderer<String, ListTestConfig> {
  @Composable
  override fun Render(item: String, config: ListTestConfig, options: RenderOptions) {
    Text(text = item, modifier = options.modifier.clickable { options.onClick?.invoke() })
  }
}

private class RecordingLayout : LayoutRenderer<String> {
  var renderInvocations = 0
    private set

  @Composable
  override fun Render(
    items: List<String>,
    component: ConfiguredRenderer<String>,
    key: ((String) -> Any)?,
    onItemClick: (String) -> Unit,
    modifier: Modifier,
  ) {
    renderInvocations++
    Column {
      items.forEach { item ->
        component.Render(item, RenderOptions(onClick = { onItemClick(item) }))
      }
    }
  }
}

@OptIn(ExperimentalTestApi::class)
class ListScaffoldTest {

  @Test
  fun emptyList_showsEmptyState_andDoesNotInvokeLayout() = runComposeUiTest {
    val registry =
      ViewRegistry().apply { registerComponent(TextItemViewType, TextRenderer(), ListTestConfig) }
    val layout = RecordingLayout()

    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        ListScaffold<String>(items = emptyList(), onItemClick = {}, key = { it }) {
          component(TextItemViewType)
          layout(layout)
          emptyState { Text("nothing here") }
        }
      }
    }

    onNodeWithText("nothing here").assertIsDisplayed()
    assertEquals(0, layout.renderInvocations)
  }

  @Test
  fun rendersItems_andForwardsClicksFromRegistry() = runComposeUiTest {
    var clicked: String? = null
    val registry =
      ViewRegistry().apply {
        registerComponent(TextItemViewType, TextRenderer(), ListTestConfig)
        registerLayout<String>(PlainListViewType, RecordingLayout())
      }

    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        ListScaffold<String>(
          items = listOf("alpha", "beta", "gamma"),
          onItemClick = { clicked = it },
          key = { it },
        ) {
          component(TextItemViewType)
          layout(PlainListViewType)
        }
      }
    }

    onNodeWithText("alpha").assertIsDisplayed()
    onNodeWithText("beta").assertIsDisplayed()
    onNodeWithText("gamma").assertIsDisplayed()

    onNodeWithText("beta").performClick()
    assertEquals("beta", clicked)
  }

  @Test
  fun omittingLayout_fallsBackToVerticalListRenderer() = runComposeUiTest {
    val registry =
      ViewRegistry().apply { registerComponent(TextItemViewType, TextRenderer(), ListTestConfig) }

    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        ListScaffold<String>(items = listOf("one", "two"), onItemClick = {}, key = { it }) {
          component(TextItemViewType)
        }
      }
    }

    onNodeWithText("one").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
  }

  @Test
  fun unregisteredViewType_throwsWithDescriptiveMessage() = runComposeUiTest {
    val thrown = assertFails {
      setContent {
        ListScaffold<String>(items = listOf("x"), onItemClick = {}, key = { it }) {
          component(TextItemViewType)
        }
      }
    }
    val msg = thrown.message.orEmpty()
    assertTrue(msg.contains("String"), "Message should mention data type: $msg")
    assertTrue(msg.contains(TextItemViewType.value), "Message should mention view type value: $msg")
  }
}
