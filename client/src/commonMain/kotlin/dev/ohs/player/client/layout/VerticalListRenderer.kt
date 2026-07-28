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
package dev.ohs.player.client.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ohs.player.client.registry.ViewType
import dev.ohs.player.client.renderer.ConfiguredRenderer
import dev.ohs.player.client.renderer.LayoutRenderer
import dev.ohs.player.client.renderer.RenderOptions

/**
 * Renders items vertically as a `LazyColumn`. The default layout for
 * [dev.ohs.player.client.scaffold.ListScaffold] when none is specified.
 *
 * View-type constants are typically declared once in an `object`; see
 * [dev.ohs.player.client.registry.ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val VerticalList = ViewType("VerticalList")
 * }
 *
 * registry.registerLayout<PatientView>(
 *     AppViewTypes.VerticalList,
 *     VerticalListRenderer(
 *         contentPadding = PaddingValues(16.dp),
 *         itemSpacing = 12.dp,
 *     ),
 * )
 * ```
 *
 * @param contentPadding padding around the list content (not the items themselves).
 * @param itemSpacing vertical gap between items.
 */
class VerticalListRenderer<T>(
  private val contentPadding: PaddingValues = PaddingValues(0.dp),
  private val itemSpacing: Dp = 0.dp,
) : LayoutRenderer<T> {
  companion object {
    /**
     * ViewType constant for registering a [VerticalListRenderer] in the
     * [dev.ohs.player.client.registry.ViewRegistry].
     */
    val VIEW_TYPE = ViewType("VerticalList")
  }

  /** Lays out [items] in a `LazyColumn`, invoking [component] per item with [key]. */
  @Composable
  override fun Render(
    items: List<T>,
    component: ConfiguredRenderer<T>,
    key: ((T) -> Any)?,
    onItemClick: (T) -> Unit,
    modifier: Modifier,
  ) {
    LazyColumn(
      modifier = modifier,
      contentPadding = contentPadding,
      verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
      items(items = items, key = key) { item ->
        component.Render(item, RenderOptions(onClick = { onItemClick(item) }))
      }
    }
  }
}
