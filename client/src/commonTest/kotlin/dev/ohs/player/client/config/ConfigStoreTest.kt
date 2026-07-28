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
package dev.ohs.player.client.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable

class ConfigStoreTest {

  private val viewDefinition =
    """
    { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
      "name": "PatientSummary", "resource": "Patient",
      "select": [{ "column": [{ "name": "patientId", "path": "id" }] }] }
    """

  private val viewJoinMap =
    """
    { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
      "name": "patientSummary", "from": "root", "resource": "Patient", "view": "PatientSummary" }
    """

  // An arbitrary resource the app might also treat as config, keyed by `id`.
  private val questionnaire =
    """{ "resourceType": "Questionnaire", "id": "intake", "title": "Intake form" }"""

  @Serializable private data class Questionnaire(val id: String, val title: String)

  private fun storeOf(vararg resources: String) = ConfigStore { resources.toList() }

  @Test
  fun get_returnsResource_byResourceTypeAndKey() = runTest {
    val store = storeOf(viewDefinition, viewJoinMap, questionnaire)

    assertEquals(
      "Patient",
      store.get(VIEW_DEFINITION, "PatientSummary", ViewDefinition.serializer())?.resource,
    )
    assertEquals(
      "PatientSummary",
      store.get(VIEW_JOIN_MAP, "patientSummary", ViewJoinMap.serializer())?.view,
    )
    // The same store serves any resource kind — proving it isn't view-specific.
    assertEquals(
      "Intake form",
      store.get("Questionnaire", "intake", Questionnaire.serializer())?.title,
    )
  }

  @Test
  fun get_loadsSourceOnce_andMemoizesDecodedResults() = runTest {
    var reads = 0
    val store = ConfigStore {
      reads++
      listOf(viewDefinition, viewJoinMap)
    }

    val first = store.get(VIEW_DEFINITION, "PatientSummary", ViewDefinition.serializer())
    val second = store.get(VIEW_DEFINITION, "PatientSummary", ViewDefinition.serializer())
    store.get(VIEW_JOIN_MAP, "patientSummary", ViewJoinMap.serializer())

    assertEquals(1, reads)
    assertSame(first, second)
  }

  @Test
  fun get_returnsNull_whenResourceAbsent() = runTest {
    val store = storeOf(viewJoinMap)
    assertNull(store.get(VIEW_DEFINITION, "PatientSummary", ViewDefinition.serializer()))
    assertNull(store.get("Questionnaire", "intake", Questionnaire.serializer()))
  }

  private companion object {
    const val VIEW_DEFINITION = "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition"
    const val VIEW_JOIN_MAP = "http://ohs.dev/StructureDefinition/ViewJoinMap"
  }
}
