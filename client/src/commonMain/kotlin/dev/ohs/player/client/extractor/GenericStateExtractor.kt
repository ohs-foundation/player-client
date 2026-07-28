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
package dev.ohs.player.client.extractor

import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.player.client.config.ConfigStore
import dev.ohs.player.client.config.ViewDefinition
import dev.ohs.player.client.config.ViewJoinMap
import dev.ohs.player.client.config.fhirJson
import dev.ohs.player.client.model.SearchResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/**
 * A config-driven extractor used to extract data from FHIR resources to a typed data class.
 *
 * Given a target state type it derives the config name from that type, resolves the downloaded
 * [ViewJoinMap] + its [ViewDefinition]s from the [configStore], interprets them against a
 * [SearchResult] — pivots, joins, `where` filters, `forEach`/`forEachOrNull`/`unionAll` expansion
 * and constants — into one JSON row per output row, and deserializes each row into the typed state
 * using the state's own (compiler-generated) serializer.
 *
 * Not safe for concurrent use: the underlying [FhirPathEngine] holds mutable evaluation state, so
 * callers must serialize calls to [extract] (e.g. confine them to a single-threaded dispatcher).
 * [ConfigStore] loading is internally synchronized and may be shared.
 */
class GenericStateExtractor(
  private val configStore: ConfigStore,
  private val engine: FhirPathEngine = FhirPathEngineProvider.r4,
) {

  /** Extracts every [T] the matching config produces from [result]. */
  suspend inline fun <reified T : Any> extract(result: SearchResult<Resource>): List<T> =
    extract(result, serializer<T>())

  @PublishedApi
  internal suspend fun <T : Any> extract(
    result: SearchResult<Resource>,
    deserializer: KSerializer<T>,
  ): List<T> {
    // The ViewJoinMap name is the lowercase-first form of the state's simple name minus "State"
    // (e.g. PatientAllergyState -> patientAllergy) — the inverse of the codegen naming rule.
    val configName =
      deserializer.descriptor.serialName
        .substringAfterLast('.')
        .removeSuffix("State")
        .replaceFirstChar { it.lowercase() }

    val joinMap =
      configStore.get(VIEW_JOIN_MAP, configName, ViewJoinMap.serializer())
        ?: error("ViewJoinMap '$configName' was not provided to the ConfigStore.")
    val pivotView =
      configStore.get(VIEW_DEFINITION, joinMap.view, ViewDefinition.serializer())
        ?: error("ViewDefinition '${joinMap.view}' (pivot of '$configName') was not provided.")
    val joins =
      joinMap.joins.map { join ->
        val view =
          configStore.get(VIEW_DEFINITION, join.view, ViewDefinition.serializer())
            ?: error("ViewDefinition '${join.view}' (join of '$configName') was not provided.")
        join to view
      }

    validate(configName, deserializer, pivotView, joinMap, joins.map { it.second })

    return buildRows(result, pivotView, joinMap, joins).map {
      fhirJson.decodeFromJsonElement(deserializer, it)
    }
  }

  private fun buildRows(
    result: SearchResult<Resource>,
    pivotView: ViewDefinition,
    joinMap: ViewJoinMap,
    joins: List<Pair<ViewJoinMap.Join, ViewDefinition>>,
  ): List<JsonObject> {
    val constants = collectConstants(pivotView, joins.map { it.second })
    val pivots = resourcesInScope(joinMap.from, joinMap.resource, joinMap.searchParam, result)
    val joinResolvers =
      joins.map { (join, view) -> JoinResolver.of(this, result, join, view, constants) }

    return buildList {
      for (pivot in pivots) {
        if (pivotView.where.any { eval(pivot, it.path, constants).bool != true }) continue

        for (row in viewRows(pivotView.select, pivot, constants)) {
          val full = row.toMutableMap()
          joinResolvers.forEach {
            full.putAll(it.columnsForRow(this@GenericStateExtractor, full, constants))
          }
          add(JsonObject(full))
        }
      }
    }
  }

  /** Cross-joins every top-level select node, each evaluated against [focus]. */
  private fun viewRows(
    selects: List<ViewDefinition.SelectBlock>,
    focus: Any?,
    constants: Map<String, Any?>,
  ): List<Map<String, JsonElement>> {
    var rows = listOf(emptyMap<String, JsonElement>())
    selects.forEach { node -> rows = crossJoin(rows, selectRows(node, focus, constants)) }
    return rows
  }

  /**
   * Rows produced by one select node, per SQL-on-FHIR: `forEach`/`forEachOrNull` re-root [focus] at
   * each element of a path; the node's own columns, its nested selects, and its `unionAll` branches
   * combine by cross-join (with `unionAll` branches concatenated first).
   */
  private fun selectRows(
    node: ViewDefinition.SelectBlock,
    focus: Any?,
    constants: Map<String, Any?>,
  ): List<Map<String, JsonElement>> {
    val foci: List<Any?> =
      when {
        node.forEach != null -> evalList(focus, node.forEach, constants).map { it.raw }
        node.forEachOrNull != null ->
          evalList(focus, node.forEachOrNull, constants).map { it.raw }.ifEmpty { listOf(null) }
        else -> listOf(focus)
      }

    return foci.flatMap { element ->
      var rows = listOf(columnRow(node.column, element, constants))
      node.select.forEach { child -> rows = crossJoin(rows, selectRows(child, element, constants)) }
      if (node.unionAll.isNotEmpty()) {
        rows = crossJoin(rows, node.unionAll.flatMap { selectRows(it, element, constants) })
      }
      rows
    }
  }

  /** Evaluates [columns] against [focus] into a single row. */
  private fun columnRow(
    columns: List<ViewDefinition.Column>,
    focus: Any?,
    constants: Map<String, Any?>,
  ): Map<String, JsonElement> = columns.associate { it.name to columnValue(focus, it, constants) }

  /**
   * Resolves the joined resource(s) for one join, computing column values lazily per output row.
   */
  private class JoinResolver
  private constructor(
    private val view: ViewDefinition,
    private val matchKey: String?,
    private val staticValues: Map<String, JsonElement>,
    private val index: Map<String, Resource>,
  ) {

    fun columnsForRow(
      extractor: GenericStateExtractor,
      base: Map<String, JsonElement>,
      constants: Map<String, Any?>,
    ): Map<String, JsonElement> {
      if (matchKey == null) return staticValues
      val joined = (base[matchKey] as? JsonPrimitive)?.asString()?.let { index[it] }
      return view.allColumns().associate { col ->
        col.name to extractor.columnValue(joined, col, constants)
      }
    }

    companion object {
      fun of(
        extractor: GenericStateExtractor,
        result: SearchResult<Resource>,
        join: ViewJoinMap.Join,
        view: ViewDefinition,
        constants: Map<String, Any?>,
      ): JoinResolver {
        val candidates = resourcesInScope(join.from, join.resource, join.searchParam, result)
        return if (join.matchKey == null) {
          val resource = candidates.firstOrNull()
          val values =
            view.allColumns().associate { col ->
              col.name to extractor.columnValue(resource, col, constants)
            }
          JoinResolver(view, null, values, emptyMap())
        } else {
          val index = buildMap {
            candidates.forEach { resource ->
              extractor.eval(resource, "id").str?.let { id -> put(id, resource) }
            }
          }
          JoinResolver(view, join.matchKey, emptyMap(), index)
        }
      }

      private fun JsonPrimitive.asString(): String? = if (this is JsonNull) null else content
    }
  }

  private fun validate(
    configName: String,
    deserializer: KSerializer<*>,
    pivotView: ViewDefinition,
    joinMap: ViewJoinMap,
    joinViews: List<ViewDefinition>,
  ) {
    val descriptor = deserializer.descriptor
    val fieldKinds =
      (0 until descriptor.elementsCount).associate {
        descriptor.getElementName(it) to descriptor.getElementDescriptor(it).kind
      }
    val producedColumns = pivotView.allColumns() + joinViews.flatMap { it.allColumns() }

    val unexpected = producedColumns.map { it.name }.toSet() - fieldKinds.keys
    require(unexpected.isEmpty()) {
      "Config '$configName' produces columns $unexpected absent from the generated state ${fieldKinds.keys}. " +
        "The provided binaries do not match the generated state."
    }

    producedColumns.forEach { col ->
      if (col.collection) return@forEach
      val required = requiredTypeCategory(fieldKinds[col.name]) ?: return@forEach
      require(typeCategory(col.type) == required) {
        "Column '${col.name}' in config '$configName' feeds a $required state field but its " +
          "ViewDefinition type is '${col.type}'. Declare a matching scalar 'type' so the value is " +
          "not decoded as a string."
      }
    }

    val scalarColumns = producedColumns.filterNot { it.collection }.map { it.name }.toSet()
    joinMap.joins.forEach { join ->
      val matchKey = join.matchKey ?: return@forEach
      require(matchKey in scalarColumns) {
        "Join matchKey '$matchKey' in config '$configName' must reference a scalar (non-collection) " +
          "column produced by the pivot or an earlier join; produced scalar columns are $scalarColumns."
      }
    }
  }

  /** Evaluates one column against [focus] and returns its JSON cell value (or [JsonNull]). */
  private fun columnValue(
    focus: Any?,
    col: ViewDefinition.Column,
    constants: Map<String, Any?>,
  ): JsonElement {
    if (focus == null) return if (col.collection) JsonArray(emptyList()) else JsonNull
    return if (col.collection) {
      JsonArray(evalList(focus, col.path, constants).map { scalarValue(it, col.type) })
    } else {
      scalarValue(eval(focus, col.path, constants), col.type)
    }
  }

  /** Maps a single [EvalResult] to a JSON value using the column's FHIR type. */
  private fun scalarValue(result: EvalResult, type: String?): JsonElement =
    when (type?.substringAfterLast('/')) {
      "boolean" -> JsonPrimitive(result.bool)
      "integer",
      "positiveInt",
      "unsignedInt" -> JsonPrimitive(result.int)
      "integer64" -> JsonPrimitive(result.long)
      "decimal" -> JsonPrimitive(result.decimal?.toStringExpanded())
      "date" -> JsonPrimitive(result.date?.toString())
      "dateTime",
      "instant" -> JsonPrimitive(result.dateTime?.toString())
      else -> JsonPrimitive(result.str)
    }

  private fun eval(
    focus: Any?,
    path: String,
    constants: Map<String, Any?> = emptyMap(),
  ): EvalResult = engine.eval(focus, path, constants)

  private fun evalList(
    focus: Any?,
    path: String,
    constants: Map<String, Any?> = emptyMap(),
  ): List<EvalResult> = engine.evalList(focus, path, constants)

  private companion object {

    const val VIEW_JOIN_MAP = "http://ohs.dev/StructureDefinition/ViewJoinMap"
    const val VIEW_DEFINITION = "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition"

    /**
     * The scalar category a non-string state field requires, or `null` if a string value is fine.
     */
    fun requiredTypeCategory(kind: SerialKind?): String? =
      when (kind) {
        PrimitiveKind.BOOLEAN -> "boolean"
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT -> "integer"
        PrimitiveKind.LONG -> "long"
        else -> null
      }

    /** The scalar category a ViewDefinition column [type] resolves to, mirroring [scalarValue]. */
    fun typeCategory(type: String?): String? =
      when (type?.substringAfterLast('/')) {
        "boolean" -> "boolean"
        "integer",
        "positiveInt",
        "unsignedInt" -> "integer"
        "integer64" -> "long"
        else -> null
      }

    /**
     * Cartesian product of two row sets (empty if either side is empty — i.e. inner-join
     * semantics).
     */
    fun crossJoin(
      left: List<Map<String, JsonElement>>,
      right: List<Map<String, JsonElement>>,
    ): List<Map<String, JsonElement>> = left.flatMap { l -> right.map { r -> l + r } }

    fun resourcesInScope(
      from: String,
      resource: String,
      searchParam: String?,
      result: SearchResult<Resource>,
    ): List<Resource> =
      when (from) {
        "root" -> listOf(result.resource)
        "included" ->
          if (searchParam != null) result.included?.get(searchParam).orEmpty()
          else result.included?.values?.flatten().orEmpty()
        "revIncluded" ->
          if (searchParam != null) result.revIncluded?.get(resource to searchParam).orEmpty()
          else
            result.revIncluded
              ?.entries
              ?.filter { it.key.first == resource }
              ?.flatMap { it.value }
              .orEmpty()
        else -> emptyList()
      }

    fun collectConstants(
      pivotView: ViewDefinition,
      joinViews: List<ViewDefinition>,
    ): Map<String, Any?> = buildMap {
      pivotView.constant.forEach { put(it.name, fhirPathValue(it.value)) }
      joinViews.forEach { view -> view.constant.forEach { put(it.name, fhirPathValue(it.value)) } }
    }

    /**
     * Converts a constant's kotlin-fhir model value to the FHIRPath type the engine expects for a
     * `%constant` variable. Date/time models must be the engine's own types to compare against
     * extracted fields; numbers, booleans, and strings pass through unchanged.
     */
    fun fhirPathValue(value: Any?): Any? =
      when (value) {
        is FhirDate -> FhirPathDate.fromFhirR4Date(value)
        is FhirDateTime -> FhirPathDateTime.fromFhirR4DateTime(value)
        else -> value
      }
  }
}
