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
package dev.ohs.player.client.model

import dev.ohs.fhir.model.r4.Resource

// TODO delete this class as soon as FHIR Engine is integrated
/**
 * Stands in for the engine's SearchResult until the KMP migration of kotlin-fhir-engine lands.
 *
 * Mirrors the engine's search result shape: a primary resource plus side-loaded resources returned
 * by forward includes and reverse includes.
 *
 * Replace [ResourceType] with the engine's actual type and [Resource] with the engine's base
 * interface once available.
 *
 * @param R the FHIR resource type of the primary (pivot) resource.
 */
data class SearchResult<R : Resource>(
  /** The primary resource returned by the search query. */
  val resource: R,
  /**
   * Resources returned via forward include (`_include`), keyed by search parameter name. For
   * example, `_include=AllergyIntolerance:patient` returns `"patient" → [Patient]`.
   */
  val included: Map<SearchParamName, List<Resource>>? = null,
  /**
   * Resources returned via reverse include (`_revinclude`), keyed by `Pair(resourceType,
   * searchParamName)`. For example, `_revinclude=AllergyIntolerance:patient` with resource type
   * `"AllergyIntolerance"` returns `Pair("AllergyIntolerance", "patient") →
   * [AllergyIntolerance, ...]`.
   */
  val revIncluded: Map<Pair<ResourceType, SearchParamName>, List<Resource>>? = null,
)

/** The FHIR resource type name used as a key in [SearchResult.revIncluded]. */
typealias ResourceType = String

/**
 * The search parameter name used as a key in [SearchResult.included] and
 * [SearchResult.revIncluded].
 */
typealias SearchParamName = String
