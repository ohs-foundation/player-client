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
package dev.ohs.player.client.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.ConfiguredRenderer
import dev.ohs.player.client.renderer.LayoutRenderer
import dev.ohs.player.client.renderer.RenderOptions
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

private val FooViewType = ViewType("Foo")

private data object TestConfig

private class StringRenderer : ComponentRenderer<String, TestConfig> {
  @Composable override fun Render(item: String, config: TestConfig, options: RenderOptions) {}
}

private class StringLayoutRenderer : LayoutRenderer<String> {
  @Composable
  override fun Render(
    items: List<String>,
    component: ConfiguredRenderer<String>,
    key: ((String) -> Any)?,
    onItemClick: (String) -> Unit,
    modifier: Modifier,
  ) {}
}

class ViewRegistryTest {

  @Test
  fun registerAndLookup_works_forComponentAndLayout() {
    val registry = ViewRegistry()
    val component = StringRenderer()
    val layout = StringLayoutRenderer()

    registry.registerComponent(FooViewType, component, TestConfig)
    registry.registerLayout<String>(FooViewType, layout)

    assertSame(component, registry.componentSource<String>(FooViewType))
    assertSame(layout, registry.layoutRenderer<String>(FooViewType))
  }

  @Test
  fun differentDataType_throwsOnLookup() {
    val registry = ViewRegistry()
    registry.registerComponent(FooViewType, StringRenderer(), TestConfig)

    // Same view-type value, different T, must throw to prevent silent fallback.
    assertFailsWith<NoSuchElementException> { registry.componentRenderer<Int>(FooViewType) }
  }
}
