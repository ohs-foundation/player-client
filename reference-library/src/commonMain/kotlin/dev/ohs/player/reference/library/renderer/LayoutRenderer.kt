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
package dev.ohs.player.reference.library.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Arranges a `List<T>` and delegates per-item rendering to a [ConfiguredRenderer].
 *
 * The library ships three implementations —
 * [dev.ohs.player.reference.library.layout.VerticalListRenderer],
 * [dev.ohs.player.reference.library.layout.HorizontalListRenderer], and
 * [dev.ohs.player.reference.library.layout.GridListRenderer] — but apps can supply their own.
 *
 * ```
 * class StaggeredRenderer<T> : LayoutRenderer<T> {
 *     @Composable
 *     override fun Render(items, component, key, onItemClick, modifier) {
 *         LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(2), modifier) {
 *             items(items, key) { item ->
 *                 component.Render(item, { onItemClick(item) }, Modifier)
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface LayoutRenderer<T> {
  /**
   * Lays out [items] using [component] for each entry.
   *
   * @param items the data to render.
   * @param component the bound per-item renderer.
   * @param key optional stable key function for the underlying lazy list; non-lazy renderers ignore
   *   it, and lazy renderers fall back to positional keys when it is `null`.
   * @param onItemClick invoked when the user taps an item.
   * @param modifier applied to the outer container.
   */
  @Composable
  fun Render(
    items: List<T>,
    component: ConfiguredRenderer<T>,
    key: ((T) -> Any)? = null,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
  )
}
