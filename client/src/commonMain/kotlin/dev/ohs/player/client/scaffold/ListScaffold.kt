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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.ohs.player.client.layout.VerticalListRenderer
import dev.ohs.player.client.registry.LocalViewRegistry
import dev.ohs.player.client.registry.ViewRegistry
import dev.ohs.player.client.registry.ViewType
import dev.ohs.player.client.registry.ViewTypeKey
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.ConfiguredRenderer
import dev.ohs.player.client.renderer.LayoutRenderer
import dev.ohs.player.client.renderer.withConfig
import kotlin.reflect.KClass

/**
 * Builder receiver for [ListScaffold]. Holds the chosen component renderer, layout renderer, top
 * bar, and empty-state composable. One of the `component(...)` overloads is required; `layout(...)`
 * is optional (defaults to [VerticalListRenderer]).
 *
 * View-type constants are typically declared once in an `object`; see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val Card = ViewType("Card")
 *     val VerticalList = ViewType("VerticalList")
 * }
 *
 * ListScaffold<PatientView>(items = patients, onItemClick = ::onClick, key = { it.id }) {
 *     component(AppViewTypes.Card)
 *     layout(AppViewTypes.VerticalList)
 *     topBar { TopAppBar(title = { Text("Patients") }) }
 *     emptyState { Text("No patients") }
 * }
 * ```
 *
 * @param registry the registry to resolve view-type-based renderers from.
 * @param dataType the data class for the list items, used in registry lookups.
 */
class ListDslScope<T : Any>
@PublishedApi
internal constructor(
  @PublishedApi internal val registry: ViewRegistry,
  @PublishedApi internal val dataType: KClass<T>,
) {
  /** Per-item renderer; must be set via one of the `component(...)` overloads before render. */
  @PublishedApi internal var component: ConfiguredRenderer<T>? = null

  /** Layout renderer; defaults to [VerticalListRenderer] if not set. */
  @PublishedApi internal var layout: LayoutRenderer<T>? = null

  /** Optional top bar composable. */
  @PublishedApi internal var topBar: (@Composable () -> Unit)? = null

  /** Composable shown when the items list is empty. */
  @PublishedApi internal var emptyState: (@Composable () -> Unit)? = null

  /**
   * Sets the component renderer from a pre-built [ComponentRenderer] and its [config].
   *
   * ```
   * component(PatientCardRenderer(), PatientCardConfig())
   * ```
   */
  fun <C : Any> component(renderer: ComponentRenderer<T, C>, config: C) {
    component = renderer.withConfig(config)
  }

  /**
   * Sets the component renderer from an inline composable. Use for ad-hoc lists.
   *
   * ```
   * component { item, onClick ->
   *     Card(onClick = onClick) { Text(item.fullName) }
   * }
   * ```
   */
  fun component(content: @Composable (T, onClick: (() -> Unit)?) -> Unit) {
    component = ConfiguredRenderer { item, options -> content(item, options.onClick) }
  }

  /**
   * Resolves the component renderer from the registry by [viewType].
   *
   * ```
   * component(ViewType("Card"))
   * ```
   */
  fun component(viewType: ViewType) {
    component = registry.getComponent(ViewTypeKey(viewType, dataType))
  }

  /**
   * Overrides the default vertical layout with [renderer].
   *
   * ```
   * layout(GridListRenderer(cells = GridCells.Fixed(2)))
   * ```
   */
  fun layout(renderer: LayoutRenderer<T>) {
    layout = renderer
  }

  /**
   * Resolves the layout renderer from the registry by [viewType].
   *
   * ```
   * layout(ViewType("HorizontalList"))
   * ```
   */
  fun layout(viewType: ViewType) {
    layout = registry.getLayout(ViewTypeKey(viewType, dataType))
  }

  /**
   * Sets the optional top bar composable.
   *
   * ```
   * topBar { TopAppBar(title = { Text("Patients") }) }
   * ```
   */
  fun topBar(content: @Composable () -> Unit) {
    topBar = content
  }

  /**
   * Sets the composable shown when the items list is empty.
   *
   * ```
   * emptyState { Text("No patients") }
   * ```
   */
  fun emptyState(content: @Composable () -> Unit) {
    emptyState = content
  }
}

/**
 * Scaffold for a list of [T]. Builds via [ListDslScope]. Empty [items] renders the `emptyState`
 * composable and never invokes the layout renderer; non-empty delegates to the chosen
 * [LayoutRenderer] (defaults to [VerticalListRenderer]).
 *
 * View-type constants are typically declared once in an `object`; see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val Card = ViewType("Card")
 *     val VerticalList = ViewType("VerticalList")
 * }
 *
 * val patients by viewModel.patients.collectAsStateWithLifecycle()
 * ListScaffold<PatientView>(
 *     items = patients,
 *     onItemClick = { onPatientClick(it.id) },
 *     key = { it.id },
 * ) {
 *     component(AppViewTypes.Card)
 *     layout(AppViewTypes.VerticalList)
 *     topBar { TopAppBar(title = { Text("Patients") }) }
 *     emptyState { Text("No patients") }
 * }
 * ```
 *
 * @param items the data list.
 * @param onItemClick invoked when the user taps an item.
 * @param key stable key function used by the underlying lazy list.
 * @param modifier applied to the root scaffold.
 * @param builder DSL block configuring component, layout, top bar, and empty state.
 */
@Composable
inline fun <reified T : Any> ListScaffold(
  items: List<T>,
  noinline onItemClick: (T) -> Unit,
  noinline key: (T) -> Any,
  modifier: Modifier = Modifier,
  builder: ListDslScope<T>.() -> Unit,
) {
  val registry = LocalViewRegistry.current
  val scope = ListDslScope(registry, T::class).apply(builder)
  val component =
    requireNotNull(scope.component) {
      "ListScaffold requires component(...) to be called in the builder."
    }
  val defaultLayout = remember { VerticalListRenderer<T>() }
  val layoutRenderer = scope.layout ?: defaultLayout

  Scaffold(modifier = modifier, topBar = scope.topBar ?: {}) { padding ->
    if (items.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        scope.emptyState?.invoke()
      }
    } else {
      layoutRenderer.Render(
        items = items,
        component = component,
        key = key,
        onItemClick = onItemClick,
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    }
  }
}
