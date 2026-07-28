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

import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.ConfiguredRenderer
import dev.ohs.player.client.renderer.LayoutRenderer
import dev.ohs.player.client.renderer.withConfig

/**
 * Mutable registry mapping `(ViewType, KClass<T>)` keys to renderers.
 *
 * Populated at app start (typically inside a `buildAppViewRegistry()` helper) and then installed
 * into the composition via [LocalViewRegistry] so scaffolds can resolve renderers by name without
 * the screen importing them directly. Lookups throw [NoSuchElementException] on miss with a
 * diagnostic message naming the absent key.
 *
 * View-type constants are typically declared once in an `object`; see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val Card = ViewType("Card")
 *     val VerticalList = ViewType("VerticalList")
 * }
 *
 * val registry = ViewRegistry().apply {
 *     registerComponent<PatientView, PatientCardConfig>(
 *         AppViewTypes.Card, PatientCardRenderer(), PatientCardConfig(),
 *     )
 *     registerLayout<PatientView>(
 *         AppViewTypes.VerticalList,
 *         VerticalListRenderer(),
 *     )
 * }
 * CompositionLocalProvider(LocalViewRegistry provides registry) { App() }
 * ```
 */
class ViewRegistry {
  /**
   * Storage entry pairing the original [ComponentRenderer] with its config and the pre-built
   * [ConfiguredRenderer] wrapper. [configured] is built once at construction so lookups are O(1)
   * and the same wrapper instance is returned on repeated calls.
   *
   * @param renderer the original renderer, retained for identity / introspection.
   * @param config the config baked into [configured].
   */
  @PublishedApi
  internal class ComponentEntry<T : Any, C : Any>(
    val renderer: ComponentRenderer<T, C>,
    val config: C,
  ) {
    /** Bound renderer, ready to invoke without carrying the config type. */
    val configured: ConfiguredRenderer<T> = renderer.withConfig(config)
  }

  /** Component entries keyed by [ViewTypeKey]. */
  @PublishedApi internal val components = mutableMapOf<ViewTypeKey<*>, ComponentEntry<*, *>>()

  /** Layout renderers keyed by [ViewTypeKey]. */
  @PublishedApi internal val layouts = mutableMapOf<ViewTypeKey<*>, LayoutRenderer<*>>()

  /** Stores a component entry under [key]. Called by [registerComponent]. */
  @PublishedApi
  internal fun <T : Any, C : Any> putComponent(
    key: ViewTypeKey<T>,
    renderer: ComponentRenderer<T, C>,
    config: C,
  ) {
    components[key] = ComponentEntry(renderer, config)
  }

  /** Stores a layout renderer under [key]. Called by [registerLayout]. */
  @PublishedApi
  internal fun <T : Any> putLayout(key: ViewTypeKey<T>, renderer: LayoutRenderer<T>) {
    layouts[key] = renderer
  }

  /**
   * Returns the cached [ConfiguredRenderer] registered under [key].
   *
   * @throws NoSuchElementException if no component is registered for [key].
   */
  @Suppress("UNCHECKED_CAST")
  @PublishedApi
  internal fun <T : Any> getComponent(key: ViewTypeKey<T>): ConfiguredRenderer<T> =
    (components[key] ?: missing("component", key)).configured as ConfiguredRenderer<T>

  /**
   * Returns the original [ComponentRenderer] registered under [key], before [withConfig] was
   * applied. Intended for identity-based tests and debugging.
   *
   * @throws NoSuchElementException if no component is registered for [key].
   */
  @Suppress("UNCHECKED_CAST")
  @PublishedApi
  internal fun <T : Any> getComponentSource(key: ViewTypeKey<T>): ComponentRenderer<T, *> =
    (components[key] ?: missing("component", key)).renderer as ComponentRenderer<T, *>

  /**
   * Returns the [LayoutRenderer] registered under [key].
   *
   * @throws NoSuchElementException if no layout is registered for [key].
   */
  @Suppress("UNCHECKED_CAST")
  @PublishedApi
  internal fun <T : Any> getLayout(key: ViewTypeKey<T>): LayoutRenderer<T> {
    val renderer = layouts[key] ?: missing("layout", key)
    return renderer as LayoutRenderer<T>
  }

  /** Throws a [NoSuchElementException] naming the missing renderer kind and key. */
  private fun missing(kind: String, key: ViewTypeKey<*>): Nothing =
    throw NoSuchElementException(
      "No $kind renderer registered for " + "(${key.dataType.simpleName}, ${key.viewType.value})."
    )
}

/**
 * Registers [renderer] under [viewType] for data type [T], with [config] baked in.
 *
 * The same renderer instance can be registered under multiple view-types with different configs to
 * vary visual behavior per role. View-type constants are typically declared once in an `object`;
 * see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val Card = ViewType("Card")
 *     val PatientHeader = ViewType("PatientHeader")
 * }
 *
 * registry.registerComponent<PatientView, PatientCardConfig>(
 *     AppViewTypes.Card, PatientCardRenderer(), PatientCardConfig(),
 * )
 * registry.registerComponent<PatientView, PatientCardConfig>(
 *     AppViewTypes.PatientHeader,
 *     PatientCardRenderer(),
 *     PatientCardConfig(showLastVisit = false),
 * )
 * ```
 */
inline fun <reified T : Any, C : Any> ViewRegistry.registerComponent(
  viewType: ViewType,
  renderer: ComponentRenderer<T, C>,
  config: C,
) = putComponent(ViewTypeKey(viewType, T::class), renderer, config)

/**
 * Registers [renderer] as the [LayoutRenderer] for [viewType] / data type [T].
 *
 * View-type constants are typically declared once in an `object`; see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val VerticalList = ViewType("VerticalList")
 * }
 *
 * registry.registerLayout<PatientView>(
 *     AppViewTypes.VerticalList,
 *     VerticalListRenderer(contentPadding = PaddingValues(16.dp), itemSpacing = 12.dp),
 * )
 * ```
 */
inline fun <reified T : Any> ViewRegistry.registerLayout(
  viewType: ViewType,
  renderer: LayoutRenderer<T>,
) = putLayout(ViewTypeKey(viewType, T::class), renderer)

/**
 * Looks up the bound [ConfiguredRenderer] for [viewType] / data type [T] — the form scaffolds and
 * layouts invoke.
 *
 * ```
 * val renderer = registry.componentRenderer<PatientView>(ViewType("Card"))
 * ```
 *
 * @throws NoSuchElementException if nothing is registered for the key.
 */
inline fun <reified T : Any> ViewRegistry.componentRenderer(
  viewType: ViewType
): ConfiguredRenderer<T> = getComponent(ViewTypeKey(viewType, T::class))

/**
 * Looks up the original [ComponentRenderer] (pre-[withConfig]) for [viewType] / data type [T].
 * Useful for identity-based assertions in tests and runtime introspection; not normally needed by
 * app code.
 *
 * ```
 * val original = registry.componentSource<PatientView>(ViewType("Card"))
 * assertSame(myRendererInstance, original)
 * ```
 *
 * @throws NoSuchElementException if nothing is registered for the key.
 */
inline fun <reified T : Any> ViewRegistry.componentSource(
  viewType: ViewType
): ComponentRenderer<T, *> = getComponentSource(ViewTypeKey(viewType, T::class))

/**
 * Looks up the [LayoutRenderer] for [viewType] / data type [T].
 *
 * ```
 * val layout = registry.layoutRenderer<PatientView>(ViewType("VerticalList"))
 * ```
 *
 * @throws NoSuchElementException if nothing is registered for the key.
 */
inline fun <reified T : Any> ViewRegistry.layoutRenderer(viewType: ViewType): LayoutRenderer<T> =
  getLayout(ViewTypeKey(viewType, T::class))
