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
package dev.ohs.player.reference.library.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Plain JSON used to parse authored config resources and decode extracted state rows. FHIR scalar
 * types resolve via the `@file:UseSerializers` annotations on the `@Serializable` classes, so no
 * contextual `SerializersModule` is needed. `ignoreUnknownKeys` skips the config metadata fields
 * (url, title, publisher, …) that the runtime models intentionally omit.
 */
internal val fhirJson: Json = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

/**
 * A generic store of runtime configuration resources loaded from a [ConfigSource].
 *
 * Each resource is any FHIR-style JSON with a `resourceType` — ViewJoinMap, ViewDefinition, or
 * anything else an app treats as config (Composition, Questionnaire, …). Resources are indexed by
 * `resourceType` + key (the resource's `name`, else `id`, else `url`) and fetched with [get]. The
 * store doesn't care where the bytes came from — bundled resources, a server download, etc. —
 * that's the [ConfigSource]'s job. Loading is lazy and runs once.
 */
class ConfigStore(private val source: ConfigSource) {

  private val resources = mutableMapOf<String, JsonObject>()
  private val decoded = mutableMapOf<String, Any?>()
  private val mutex = Mutex()
  private var loaded = false

  /**
   * The resource of [resourceType] identified by [key] (its `name`/`id`/`url`), decoded via
   * [deserializer], or `null` if no such resource was provided. Configs are immutable once loaded,
   * so decoded results are memoized and reused across calls.
   */
  suspend fun <T> get(
    resourceType: String,
    key: String,
    deserializer: DeserializationStrategy<T>,
  ): T? =
    mutex.withLock {
      ensureLoaded()
      val resourceKey = indexKey(resourceType, key)
      val cacheKey = "$resourceKey|${deserializer.descriptor.serialName}"
      if (!decoded.containsKey(cacheKey)) {
        decoded[cacheKey] =
          resources[resourceKey]?.let { fhirJson.decodeFromJsonElement(deserializer, it) }
      }
      @Suppress("UNCHECKED_CAST")
      decoded[cacheKey] as T?
    }

  private suspend fun ensureLoaded() {
    if (loaded) return
    source.readAll().forEach { index(fhirJson.parseToJsonElement(it).jsonObject) }
    loaded = true
  }

  private fun index(resource: JsonObject) {
    val type = resource.string("resourceType") ?: return
    val key = resource.string("name") ?: resource.string("id") ?: resource.string("url") ?: return
    resources[indexKey(type, key)] = resource
  }

  private fun indexKey(resourceType: String, key: String) = "$resourceType/$key"

  private fun JsonObject.string(field: String): String? =
    (this[field] as? JsonPrimitive)?.contentOrNull
}
