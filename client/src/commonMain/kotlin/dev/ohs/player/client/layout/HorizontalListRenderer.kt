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
import androidx.compose.foundation.lazy.LazyRow
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
 * Renders items horizontally as a `LazyRow`.
 *
 * View-type constants are typically declared once in an `object`; see
 * [dev.ohs.player.client.registry.ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val HorizontalList = ViewType("HorizontalList")
 * }
 *
 * registry.registerLayout<PatientView>(
 *     AppViewTypes.HorizontalList,
 *     HorizontalListRenderer(
 *         contentPadding = PaddingValues(horizontal = 16.dp),
 *         itemSpacing = 12.dp,
 *     ),
 * )
 * ```
 *
 * @param contentPadding padding around the list content.
 * @param itemSpacing horizontal gap between items.
 */
class HorizontalListRenderer<T>(
  private val contentPadding: PaddingValues = PaddingValues(0.dp),
  private val itemSpacing: Dp = 0.dp,
) : LayoutRenderer<T> {
  companion object {
    /**
     * ViewType constant for registering a [HorizontalListRenderer] in the
     * [dev.ohs.player.client.registry.ViewRegistry].
     */
    val VIEW_TYPE = ViewType("HorizontalList")
  }

  /** Lays out [items] in a `LazyRow`, invoking [component] per item with [key]. */
  @Composable
  override fun Render(
    items: List<T>,
    component: ConfiguredRenderer<T>,
    key: ((T) -> Any)?,
    onItemClick: (T) -> Unit,
    modifier: Modifier,
  ) {
    LazyRow(
      modifier = modifier,
      contentPadding = contentPadding,
      horizontalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
      items(items = items, key = key) { item ->
        component.Render(item, RenderOptions(onClick = { onItemClick(item) }))
      }
    }
  }
}
