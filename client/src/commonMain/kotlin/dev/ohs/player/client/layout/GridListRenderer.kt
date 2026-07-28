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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ohs.player.client.registry.ViewType
import dev.ohs.player.client.renderer.ConfiguredRenderer
import dev.ohs.player.client.renderer.LayoutRenderer
import dev.ohs.player.client.renderer.RenderOptions

/**
 * Renders items in a vertically-scrolling `LazyVerticalGrid`.
 *
 * View-type constants are typically declared once in an `object`; see
 * [dev.ohs.player.client.registry.ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val Grid = ViewType("Grid")
 * }
 *
 * registry.registerLayout<PatientView>(
 *     AppViewTypes.Grid,
 *     GridListRenderer(
 *         cells = GridCells.Fixed(2),
 *         contentPadding = PaddingValues(16.dp),
 *         itemSpacing = 12.dp,
 *     ),
 * )
 * ```
 *
 * @param cells column specification; defaults to two equal columns.
 * @param contentPadding padding around the grid content.
 * @param itemSpacing gap between items in both axes.
 */
class GridListRenderer<T>(
  private val cells: GridCells = GridCells.Fixed(2),
  private val contentPadding: PaddingValues = PaddingValues(0.dp),
  private val itemSpacing: Dp = 0.dp,
) : LayoutRenderer<T> {
  companion object {
    /**
     * ViewType constant for registering a [GridListRenderer] in the
     * [dev.ohs.player.client.registry.ViewRegistry].
     */
    val VIEW_TYPE = ViewType("Grid")
  }

  /** Lays out [items] in a `LazyVerticalGrid`, invoking [component] per item with [key]. */
  @Composable
  override fun Render(
    items: List<T>,
    component: ConfiguredRenderer<T>,
    key: ((T) -> Any)?,
    onItemClick: (T) -> Unit,
    modifier: Modifier,
  ) {
    LazyVerticalGrid(
      columns = cells,
      modifier = modifier,
      contentPadding = contentPadding,
      verticalArrangement = Arrangement.spacedBy(itemSpacing),
      horizontalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
      items(items = items, key = key) { item ->
        component.Render(item, RenderOptions(onClick = { onItemClick(item) }))
      }
    }
  }
}
