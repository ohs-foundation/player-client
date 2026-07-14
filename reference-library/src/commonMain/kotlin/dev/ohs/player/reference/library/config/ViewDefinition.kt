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
@file:UseSerializers(
  FhirDateSerializer::class,
  FhirDateTimeSerializer::class,
  FhirDecimalSerializer::class,
)

package dev.ohs.player.reference.library.config

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Runtime model of a SQL-on-FHIR ViewDefinition — a projection of a FHIR resource into flat rows.
 *
 * Mirrors the
 * [SQL-on-FHIR ViewDefinition](https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition.html):
 * `constant`s (FHIRPath variables), `where` filters, and a tree of `select` nodes. Metadata fields
 * (url, title, publisher, …) and `column.tag` are not modelled — they don't affect extraction.
 */
@Serializable
data class ViewDefinition(
  val resourceType: String = "",
  val name: String = "",
  val resource: String = "",
  val status: String? = null,
  val constant: List<Constant> = emptyList(),
  val where: List<WhereClause> = emptyList(),
  val select: List<SelectBlock> = emptyList(),
) {
  /**
   * A named constant usable in FHIRPath expressions via `%name`. Mirrors the full SQL-on-FHIR
   * `value[x]` choice; [value] returns whichever variant is set.
   */
  @Serializable
  data class Constant(
    val name: String,
    val valueBase64Binary: String? = null,
    val valueBoolean: Boolean? = null,
    val valueCanonical: String? = null,
    val valueCode: String? = null,
    val valueDate: FhirDate? = null,
    val valueDateTime: FhirDateTime? = null,
    val valueDecimal: BigDecimal? = null,
    val valueId: String? = null,
    val valueInstant: FhirDateTime? = null,
    val valueInteger: Int? = null,
    val valueInteger64: Long? = null,
    val valueOid: String? = null,
    val valuePositiveInt: Int? = null,
    val valueString: String? = null,
    val valueTime: String? = null,
    val valueUnsignedInt: Int? = null,
    val valueUri: String? = null,
    val valueUrl: String? = null,
    val valueUuid: String? = null,
  ) {
    val value: Any?
      get() =
        valueBoolean
          ?: valueInteger
          ?: valueInteger64
          ?: valuePositiveInt
          ?: valueUnsignedInt
          ?: valueDecimal
          ?: valueString
          ?: valueCode
          ?: valueDate
          ?: valueDateTime
          ?: valueInstant
          ?: valueTime
          ?: valueUri
          ?: valueUrl
          ?: valueCanonical
          ?: valueId
          ?: valueOid
          ?: valueUuid
          ?: valueBase64Binary
  }

  /** A FHIRPath boolean filter on the resource; non-`true` rows are dropped. Clauses are AND-ed. */
  @Serializable data class WhereClause(val path: String)

  /**
   * One node of the select tree. Per SQL-on-FHIR, a node may carry [column]s, nested [select]
   * nodes, a [forEach]/[forEachOrNull] that re-roots evaluation at each element of a path, and a
   * [unionAll] of further select nodes whose rows are concatenated. Columns + nested selects +
   * unionAll combine by cross-join.
   */
  @Serializable
  data class SelectBlock(
    val column: List<Column> = emptyList(),
    val select: List<SelectBlock> = emptyList(),
    val forEach: String? = null,
    val forEachOrNull: String? = null,
    val unionAll: List<SelectBlock> = emptyList(),
  )

  /** One output column. [collection] = true yields a `List<T>` field instead of `T?`. */
  @Serializable
  data class Column(
    val name: String,
    val path: String,
    val type: String? = null,
    val collection: Boolean = false,
    val description: String? = null,
  )

  /**
   * Every column name the view can produce, walking the whole select tree (for the flat row
   * schema).
   */
  fun allColumns(): List<Column> = select.flatMap { it.collectColumns() }

  private fun SelectBlock.collectColumns(): List<Column> = buildList {
    addAll(column)
    select.forEach { addAll(it.collectColumns()) }
    // unionAll branches share one schema, so the first branch defines the columns.
    unionAll.firstOrNull()?.let { addAll(it.collectColumns()) }
  }
}
