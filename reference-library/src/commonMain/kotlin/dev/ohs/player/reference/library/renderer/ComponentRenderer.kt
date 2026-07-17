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
 * Groups the presentation-level options passed to every [ComponentRenderer.Render] call.
 *
 * @param onClick optional tap handler; null means the component is non-interactive.
 * @param modifier applied to the root composable of the rendered item.
 */
data class RenderOptions(val modifier: Modifier = Modifier, val onClick: (() -> Unit)? = null)

/**
 * Author-facing renderer for a single item of type [T] using config [C].
 *
 * The two type parameters let one renderer class be reused under multiple
 * [dev.ohs.player.reference.library.registry.ViewType] registrations with different configs — e.g.
 * a patient card rendered both as a list card and as a detail header with `showLastVisit = false`.
 *
 * ```
 * data class PatientCardConfig(val showLastVisit: Boolean = true)
 *
 * class PatientCardRenderer : ComponentRenderer<PatientView, PatientCardConfig> {
 *     @Composable
 *     override fun Render(
 *         item: PatientView,
 *         config: PatientCardConfig,
 *         options: RenderOptions,
 *     ) {
 *         Card(onClick = options.onClick, modifier = options.modifier) {
 *             Text(item.fullName)
 *             if (config.showLastVisit) Text(item.lastVisitDate)
 *         }
 *     }
 * }
 * ```
 */
interface ComponentRenderer<T, C> {
  /**
   * Renders [item] with [config] and [options] applied.
   *
   * @param item the data model instance.
   * @param config configuration for this render call.
   * @param options presentation options — tap handler and root modifier.
   */
  @Composable fun Render(item: T, config: C, options: RenderOptions)
}

/**
 * Renders [item] with [config] and default [RenderOptions] (no click handler, [Modifier]).
 *
 * Convenience for Kotlin call sites that don't need custom options.
 */
@Composable
fun <T, C> ComponentRenderer<T, C>.Render(item: T, config: C) =
  Render(item, config, RenderOptions())

/**
 * A renderer with its config already applied.
 *
 * Created by [withConfig] when a [ComponentRenderer] is registered with its config, and then
 * invoked by scaffolds and layouts during rendering. App code doesn't usually build these by hand —
 * author a [ComponentRenderer] instead.
 *
 * ```
 * // Inside a LayoutRenderer.Render:
 * component.Render(item, RenderOptions(onClick = { onItemClick(item) }))
 * ```
 */
fun interface ConfiguredRenderer<T> {
  /**
   * Renders [item]; the config from registration is already applied.
   *
   * @param item the data model instance.
   * @param options presentation options — tap handler and root modifier.
   */
  @Composable fun Render(item: T, options: RenderOptions)
}

/**
 * Captures [boundConfig] in a closure, producing the bound form for registry storage.
 *
 * Each invocation produces a fresh [ConfiguredRenderer] instance — they are not cached or
 * value-equal. Callers needing identity (e.g. for tests) should retrieve the original
 * [ComponentRenderer] via [dev.ohs.player.reference.library.registry.componentSource] instead.
 */
@PublishedApi
internal fun <T : Any, C : Any> ComponentRenderer<T, C>.withConfig(
  boundConfig: C
): ConfiguredRenderer<T> {
  val source = this
  return ConfiguredRenderer { item, options -> source.Render(item, boundConfig, options) }
}
