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
package dev.ohs.player.reference.library.extractor

import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime

/**
 * Lightweight wrapper around a single FHIRPath evaluation result.
 *
 * The engine returns scalars as Kotlin types (`Boolean`, `String`, `BigDecimal`, …) and date/time
 * values as its own `FhirPathDate`/`FhirPathDateTime`/`FhirPathTime` types. These accessors
 * normalise both so callers read a value without casts.
 */
class EvalResult(val raw: Any?) {
  val str: String?
    get() =
      when (raw) {
        null -> null
        is FhirPathDate -> raw.toFhirString()
        is FhirPathDateTime -> raw.toFhirString()
        is FhirPathTime -> raw.toFhirString()
        else -> raw.toString()
      }

  val bool: Boolean?
    get() = raw as? Boolean

  val int: Int?
    get() = (raw as? Number)?.toInt()

  val long: Long?
    get() = (raw as? Number)?.toLong()

  val decimal: BigDecimal?
    get() = raw as? BigDecimal

  val date: FhirDate?
    get() =
      when (raw) {
        is FhirDate -> raw
        is FhirPathDate -> FhirDate.fromString(raw.toFhirString())
        else -> null
      }

  val dateTime: FhirDateTime?
    get() =
      when (raw) {
        is FhirDateTime -> raw
        is FhirPathDateTime -> FhirDateTime.fromString(raw.toFhirString())
        else -> null
      }
}

private fun Int.pad2(): String = toString().padStart(2, '0')

/** Renders a [FhirPathDate] as a FHIR `date` string (`YYYY`, `YYYY-MM`, or `YYYY-MM-DD`). */
private fun FhirPathDate.toFhirString(): String = buildString {
  append(year.toString().padStart(4, '0'))
  month?.let { append('-').append(it.pad2()) }
  day?.let { append('-').append(it.pad2()) }
}

/** Renders a [FhirPathTime] as a FHIR `time` string (`HH:MM:SS`). */
private fun FhirPathTime.toFhirString(): String = buildString {
  append(hour.pad2())
  append(':').append((minute ?: 0).pad2())
  append(':').append((second ?: 0.0).toInt().pad2())
}

/** Renders a [FhirPathDateTime] as a FHIR `dateTime` string, preserving its precision. */
private fun FhirPathDateTime.toFhirString(): String = buildString {
  append(year.toString().padStart(4, '0'))
  month?.let { append('-').append(it.pad2()) }
  day?.let { append('-').append(it.pad2()) }
  hour?.let { h ->
    append('T').append(h.pad2())
    append(':').append((minute ?: 0).pad2())
    second?.let { append(':').append(it.toInt().pad2()) }
    utcOffset?.let { append(it.toString()) }
  }
}

/**
 * Evaluates [path] against [focus] and returns an [EvalResult] for the first result.
 *
 * [variables] are accessible in the expression via `%name` syntax (ViewDefinition `constant`
 * entries). Swallows evaluation errors — a failed expression returns [EvalResult] wrapping null.
 */
fun FhirPathEngine.eval(
  focus: Any?,
  path: String,
  variables: Map<String, Any?> = emptyMap(),
): EvalResult =
  EvalResult(
    runCatching {
        evaluateExpression(expression = path, base = focus, variables = variables).firstOrNull()
      }
      .onFailure { Logger.e(it) { it.message ?: "Error evaluating expression: $path" } }
      .getOrNull()
  )

/**
 * Evaluates [path] against [focus] and returns an [EvalResult] for every element in the result
 * collection. Use for columns declared with `collection: true` in a ViewDefinition.
 *
 * [variables] are accessible in the expression via `%name` syntax. Returns an empty list on
 * evaluation error.
 */
fun FhirPathEngine.evalList(
  focus: Any?,
  path: String,
  variables: Map<String, Any?> = emptyMap(),
): List<EvalResult> =
  runCatching {
      evaluateExpression(expression = path, base = focus, variables = variables).map {
        EvalResult(it)
      }
    }
    .onFailure { Logger.e(it) { it.message ?: "Error evaluating list for the expression: $path" } }
    .getOrElse { emptyList() }
