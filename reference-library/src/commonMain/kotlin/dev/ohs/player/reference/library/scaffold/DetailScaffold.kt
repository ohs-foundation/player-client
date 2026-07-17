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
package dev.ohs.player.reference.library.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ohs.player.reference.library.registry.LocalViewRegistry
import dev.ohs.player.reference.library.registry.ViewRegistry
import dev.ohs.player.reference.library.registry.ViewType
import dev.ohs.player.reference.library.registry.ViewTypeKey
import dev.ohs.player.reference.library.renderer.ComponentRenderer
import dev.ohs.player.reference.library.renderer.ConfiguredRenderer
import dev.ohs.player.reference.library.renderer.RenderOptions
import dev.ohs.player.reference.library.renderer.withConfig
import kotlin.reflect.KClass

/**
 * Builder receiver for [DetailScaffold]. Collects the ordered list of sections plus optional top
 * bar and not-found composables. Sections are appended via the `section(...)` overloads and render
 * in the order they're declared.
 *
 * View-type constants are typically declared once in an `object`; see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val PersonalSection = ViewType("PersonalSection")
 *     val MedicalSection = ViewType("MedicalSection")
 * }
 *
 * DetailScaffold(patient) {
 *     topBar { TopAppBar(title = { Text("Patient") }) }
 *     notFound { Text("Patient not found") }
 *     section(AppViewTypes.PersonalSection)
 *     section(AppViewTypes.MedicalSection)
 * }
 * ```
 *
 * @param registry the registry to resolve view-type-based sections from.
 * @param dataType the data class for the detail item, used in registry lookups.
 */
class DetailDslScope<T : Any>
@PublishedApi
internal constructor(
  @PublishedApi internal val registry: ViewRegistry,
  @PublishedApi internal val dataType: KClass<T>,
) {
  /** Sections to render in declared order, each as a bound renderer. */
  @PublishedApi
  internal var sections: List<ConfiguredRenderer<T>> = emptyList()
    private set

  /** Optional top bar composable. */
  @PublishedApi internal var topBar: (@Composable () -> Unit)? = null

  /** Composable shown when the detail item is null. */
  @PublishedApi internal var notFound: (@Composable () -> Unit)? = null

  /**
   * Appends a pre-built [ComponentRenderer] with its [config] as a section.
   *
   * ```
   * section(PersonalSectionRenderer(), PersonalSectionConfig)
   * ```
   */
  fun <C : Any> section(renderer: ComponentRenderer<T, C>, config: C) {
    sections += renderer.withConfig(config)
  }

  /**
   * Appends an inline section composable. Use for one-off sections that don't warrant a
   * [ComponentRenderer] class.
   *
   * ```
   * section { patient -> Text("ID: ${patient.id}") }
   * ```
   */
  fun section(content: @Composable (T) -> Unit) {
    sections += ConfiguredRenderer { item, _ -> content(item) }
  }

  /**
   * Resolves the section renderer from the registry by [viewType].
   *
   * ```
   * section(ViewType("PersonalSection"))
   * ```
   */
  fun section(viewType: ViewType) {
    sections += registry.getComponent(ViewTypeKey(viewType, dataType))
  }

  /**
   * Sets the optional top bar composable.
   *
   * ```
   * topBar { TopAppBar(title = { Text("Patient") }) }
   * ```
   */
  fun topBar(content: @Composable () -> Unit) {
    topBar = content
  }

  /**
   * Sets the composable shown when the detail item is null.
   *
   * ```
   * notFound { Text("Patient not found") }
   * ```
   */
  fun notFound(content: @Composable () -> Unit) {
    notFound = content
  }
}

/**
 * Scaffold for a single-item detail view of [T]. Null [item] renders the `notFound` composable and
 * skips sections; non-null renders sections vertically in declared order inside a `LazyColumn`.
 * Sections are read-only — their `onClick` is a no-op.
 *
 * View-type constants are typically declared once in an `object`; see [ViewType].
 *
 * ```
 * object AppViewTypes {
 *     val PatientHeader = ViewType("PatientHeader")
 *     val PersonalSection = ViewType("PersonalSection")
 *     val MedicalSection = ViewType("MedicalSection")
 *     val ContactSection = ViewType("ContactSection")
 * }
 *
 * val patient by viewModel.patient.collectAsStateWithLifecycle()
 * DetailScaffold<PatientView>(item = patient) {
 *     topBar { TopAppBar(title = { Text(patient?.fullName.orEmpty()) }) }
 *     notFound { Text("Patient not found") }
 *     section(AppViewTypes.PatientHeader)
 *     section(AppViewTypes.PersonalSection)
 *     section(AppViewTypes.MedicalSection)
 *     section(AppViewTypes.ContactSection)
 * }
 * ```
 *
 * @param item the detail item; null renders [DetailDslScope.notFound].
 * @param contentPadding padding around the section column.
 * @param sectionSpacing vertical gap between sections.
 * @param modifier applied to the root scaffold.
 * @param builder DSL block configuring sections, top bar, and not-found state.
 */
@Composable
inline fun <reified T : Any> DetailScaffold(
  item: T?,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  sectionSpacing: Dp = 16.dp,
  modifier: Modifier = Modifier,
  builder: DetailDslScope<T>.() -> Unit,
) {
  val registry = LocalViewRegistry.current
  val scope = DetailDslScope(registry, T::class).apply(builder)
  Scaffold(modifier = modifier, topBar = scope.topBar ?: {}) { padding ->
    if (item == null) {
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        scope.notFound?.invoke()
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
      ) {
        items(scope.sections) { section -> section.Render(item, RenderOptions()) }
      }
    }
  }
}
