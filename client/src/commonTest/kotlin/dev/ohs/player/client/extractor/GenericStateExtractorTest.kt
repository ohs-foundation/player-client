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

package dev.ohs.player.client.extractor

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.player.client.config.ConfigSource
import dev.ohs.player.client.config.ConfigStore
import dev.ohs.player.client.config.FhirDateSerializer
import dev.ohs.player.client.config.FhirDateTimeSerializer
import dev.ohs.player.client.config.FhirDecimalSerializer
import dev.ohs.player.client.model.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json

@Serializable
private data class PatientAllergyTestState(
  val allergyId: String? = null,
  val substance: String? = null,
  val patientRef: String? = null,
  val manifestation: String? = null,
  val severity: String? = null,
  val patientId: String? = null,
  val familyName: String? = null,
)

@Serializable
private data class PatientTelecomTestState(
  val patientId: String? = null,
  val telecomSystem: String? = null,
  val telecomValue: String? = null,
)

@Serializable
private data class ScalarTypesTestState(
  val active: Boolean? = null,
  val birthDate: FhirDate? = null,
  val gender: String? = null,
)

@Serializable private data class DecimalTypesTestState(val longitude: BigDecimal? = null)

@Serializable private data class DateTimeTypesTestState(val recorded: FhirDateTime? = null)

@Serializable private data class DecimalConstTestState(val locId: String? = null)

@Serializable private data class DateConstTestState(val patientId: String? = null)

class GenericStateExtractorTest {

  private val patient =
    """
    { "resourceType": "Patient", "id": "p1", "active": true, "gender": "female", "birthDate": "1990-03-14",
      "name": [{ "family": "Diallo", "given": ["Amina"] }],
      "telecom": [{ "system": "phone", "value": "555-0100" },
                  { "system": "email", "value": "amina@example.org" }] }
    """

  private val activeAllergy =
    """
    { "resourceType": "AllergyIntolerance", "id": "ai1", "patient": { "reference": "Patient/p1" },
      "code": { "coding": [{ "display": "Penicillin" }] },
      "clinicalStatus": { "coding": [{ "code": "active" }] },
      "reaction": [{ "manifestation": [{ "coding": [{ "display": "Rash" }] }], "severity": "moderate" },
                   { "manifestation": [{ "coding": [{ "display": "Hives" }] }], "severity": "severe" }] }
    """

  private val inactiveAllergy =
    """
    { "resourceType": "AllergyIntolerance", "id": "ai2", "patient": { "reference": "Patient/p1" },
      "code": { "coding": [{ "display": "Aspirin" }] },
      "clinicalStatus": { "coding": [{ "code": "inactive" }] },
      "reaction": [{ "manifestation": [{ "coding": [{ "display": "Nausea" }] }], "severity": "mild" }] }
    """

  private val allergyView =
    """
    { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
      "name": "AllergyView", "resource": "AllergyIntolerance",
      "where": [{ "path": "clinicalStatus.coding.where(code = 'active').exists()" }],
      "select": [
        { "column": [{ "name": "allergyId", "path": "id" },
                     { "name": "substance", "path": "code.coding.display.first()" },
                     { "name": "patientRef", "path": "patient.reference.replace('Patient/', '')" }] },
        { "forEach": "reaction",
          "column": [{ "name": "manifestation", "path": "manifestation.coding.display.first()" },
                     { "name": "severity", "path": "severity" }] }
      ] }
    """

  private val patientView =
    """
    { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
      "name": "PatientView", "resource": "Patient",
      "select": [{ "column": [{ "name": "patientId", "path": "id" },
                              { "name": "familyName", "path": "name.family.first()" }] }] }
    """

  private val patientAllergyMap =
    """
    { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
      "name": "patientAllergyTest", "from": "revIncluded", "resource": "AllergyIntolerance", "view": "AllergyView",
      "joins": [{ "view": "PatientView", "from": "included", "resource": "Patient",
                  "searchParam": "patient", "matchKey": "patientRef" }] }
    """

  private val telecomView =
    """
    { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
      "name": "TelecomView", "resource": "Patient",
      "select": [
        { "column": [{ "name": "patientId", "path": "id" }] },
        { "unionAll": [
            { "column": [{ "name": "telecomSystem", "path": "telecom.where(system = 'phone').system.first()" },
                         { "name": "telecomValue", "path": "telecom.where(system = 'phone').value.first()" }] },
            { "column": [{ "name": "telecomSystem", "path": "telecom.where(system = 'email').system.first()" },
                         { "name": "telecomValue", "path": "telecom.where(system = 'email').value.first()" }] }
        ] }
      ] }
    """

  private val patientTelecomMap =
    """
    { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
      "name": "patientTelecomTest", "from": "root", "resource": "Patient", "view": "TelecomView" }
    """

  private val jsonFormat = Json { ignoreUnknownKeys = true }

  private fun resource(json: String): Resource =
    jsonFormat.decodeFromString(Resource.serializer(), json)

  private fun extractorOf(vararg configs: String): GenericStateExtractor =
    GenericStateExtractor(ConfigStore(ConfigSource { configs.toList() }))

  @Test
  fun extract_appliesWhere_expandsForEach_andDynamicJoins() = runTest {
    val p1 = resource(patient)
    val result =
      SearchResult(
        resource = p1,
        included = mapOf("patient" to listOf(p1)),
        revIncluded =
          mapOf(
            "AllergyIntolerance" to
              "patient" to
              listOf(resource(activeAllergy), resource(inactiveAllergy))
          ),
      )

    val states =
      extractorOf(allergyView, patientView, patientAllergyMap)
        .extract<PatientAllergyTestState>(result)

    // The inactive allergy is dropped by `where`; the active one fans out one row per reaction,
    // each carrying the joined patient columns.
    assertEquals(2, states.size)
    assertEquals(listOf("Rash", "Hives"), states.map { it.manifestation })
    assertEquals(listOf("moderate", "severe"), states.map { it.severity })
    assertEquals(setOf("Penicillin"), states.map { it.substance }.toSet())
    assertEquals(setOf("Diallo"), states.map { it.familyName }.toSet())
    assertEquals(setOf("p1"), states.map { it.patientId }.toSet())
  }

  @Test
  fun extract_crossJoinsUnionAll_withAnchorColumns() = runTest {
    val p1 = resource(patient)
    val result = SearchResult(resource = p1)

    val states =
      extractorOf(telecomView, patientTelecomMap).extract<PatientTelecomTestState>(result)

    // unionAll yields one row per telecom system, each cross-joined with the anchor patientId
    // column.
    assertEquals(2, states.size)
    assertEquals(setOf("p1"), states.map { it.patientId }.toSet())
    assertEquals(
      setOf("phone" to "555-0100", "email" to "amina@example.org"),
      states.map { it.telecomSystem to it.telecomValue }.toSet(),
    )
  }

  private val location =
    """{ "resourceType": "Location", "id": "loc1", "status": "active",
         "position": { "longitude": -122.5, "latitude": 37.7 } }"""

  private val condition =
    """{ "resourceType": "Condition", "id": "c1", "subject": { "reference": "Patient/p1" },
         "recordedDate": "2021-06-15T08:30:00Z" }"""

  private fun rootView(name: String, resource: String, vararg columns: String) =
    """
    { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
      "name": "$name", "resource": "$resource",
      "select": [{ "column": [${columns.joinToString(",")}] }] }
    """

  private fun rootMap(name: String, resource: String, view: String) =
    """
    { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
      "name": "$name", "from": "root", "resource": "$resource", "view": "$view" }
    """

  @Test
  fun extract_coercesBooleanDateAndStringTypes() = runTest {
    val view =
      rootView(
        "ScalarTypesView",
        "Patient",
        """{ "name": "active", "path": "active", "type": "boolean" }""",
        """{ "name": "birthDate", "path": "birthDate", "type": "date" }""",
        """{ "name": "gender", "path": "gender", "type": "code" }""",
      )
    val result = SearchResult(resource = resource(patient))

    val state =
      extractorOf(view, rootMap("scalarTypesTest", "Patient", "ScalarTypesView"))
        .extract<ScalarTypesTestState>(result)
        .single()

    assertEquals(true, state.active)
    assertEquals("female", state.gender)
    assertNotNull(state.birthDate, "date column must map to a non-null FhirDate")
    assertEquals("1990-03-14", state.birthDate.toString())
  }

  @Test
  fun extract_coercesDecimalToBigDecimal() = runTest {
    val view =
      rootView(
        "DecimalTypesView",
        "Location",
        """{ "name": "longitude", "path": "position.longitude", "type": "decimal" }""",
      )
    val result = SearchResult(resource = resource(location))

    val state =
      extractorOf(view, rootMap("decimalTypesTest", "Location", "DecimalTypesView"))
        .extract<DecimalTypesTestState>(result)
        .single()

    assertNotNull(state.longitude, "decimal column must map to a non-null BigDecimal")
    assertEquals(BigDecimal.parseString("-122.5"), state.longitude)
  }

  @Test
  fun extract_coercesDateTimeToFhirDateTime() = runTest {
    val view =
      rootView(
        "DateTimeTypesView",
        "Condition",
        """{ "name": "recorded", "path": "recordedDate", "type": "dateTime" }""",
      )
    val result = SearchResult(resource = resource(condition))

    val state =
      extractorOf(view, rootMap("dateTimeTypesTest", "Condition", "DateTimeTypesView"))
        .extract<DateTimeTypesTestState>(result)
        .single()

    assertNotNull(state.recorded, "dateTime column must map to a non-null FhirDateTime")
    assertTrue(
      state.recorded.toString().startsWith("2021-06-15"),
      "expected recorded to start with 2021-06-15 but was ${state.recorded}",
    )
  }

  @Test
  fun extract_appliesDecimalConstantInWhere() = runTest {
    val view =
      """
      { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
        "name": "DecimalConstView", "resource": "Location",
        "constant": [{ "name": "boundary", "valueDecimal": 0.0 }],
        "where": [{ "path": "position.longitude < %boundary" }],
        "select": [{ "column": [{ "name": "locId", "path": "id" }] }] }
      """
    val map =
      """
      { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
        "name": "decimalConstTest", "from": "root", "resource": "Location", "view": "DecimalConstView" }
      """
    val states =
      extractorOf(view, map)
        .extract<DecimalConstTestState>(SearchResult(resource = resource(location)))

    // longitude -122.5 < 0.0 the row is kept only if the %boundary decimals compare
    assertEquals(listOf("loc1"), states.map { it.locId })
  }

  @Test
  fun extract_appliesDateConstantInWhere() = runTest {
    val view =
      """
      { "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
        "name": "DateConstView", "resource": "Patient",
        "constant": [{ "name": "cutoff", "valueDate": "2000-01-01" }],
        "where": [{ "path": "birthDate < %cutoff" }],
        "select": [{ "column": [{ "name": "patientId", "path": "id" }] }] }
      """
    val map =
      """
      { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
        "name": "dateConstTest", "from": "root", "resource": "Patient", "view": "DateConstView" }
      """
    val states =
      extractorOf(view, map).extract<DateConstTestState>(SearchResult(resource = resource(patient)))

    // birthDate 1990-03-14 < 2000-01-01 → kept only if the %cutoff date constant compares
    // correctly.
    assertEquals(listOf("p1"), states.map { it.patientId })
  }

  @Test
  fun extract_fails_whenNonStringColumnHasNoType() = runTest {
    val view =
      rootView(
        "ScalarTypesView",
        "Patient",
        """{ "name": "active", "path": "active" }""",
        """{ "name": "birthDate", "path": "birthDate", "type": "date" }""",
        """{ "name": "gender", "path": "gender", "type": "code" }""",
      )
    val extractor = extractorOf(view, rootMap("scalarTypesTest", "Patient", "ScalarTypesView"))

    val error =
      assertFailsWith<IllegalArgumentException> {
        extractor.extract<ScalarTypesTestState>(SearchResult(resource = resource(patient)))
      }
    assertTrue(
      error.message?.contains("active") == true,
      "expected the error to name the untyped boolean column, was: ${error.message}",
    )
  }

  @Test
  fun extract_fails_whenJoinMatchKeyIsNotAProducedColumn() = runTest {
    val badMap =
      """
      { "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
        "name": "patientAllergyTest", "from": "revIncluded", "resource": "AllergyIntolerance", "view": "AllergyView",
        "joins": [{ "view": "PatientView", "from": "included", "resource": "Patient",
                    "searchParam": "patient", "matchKey": "noSuchColumn" }] }
      """
    val p1 = resource(patient)
    val result =
      SearchResult(
        resource = p1,
        included = mapOf("patient" to listOf(p1)),
        revIncluded = mapOf("AllergyIntolerance" to "patient" to listOf(resource(activeAllergy))),
      )
    val extractor = extractorOf(allergyView, patientView, badMap)

    val error =
      assertFailsWith<IllegalArgumentException> {
        extractor.extract<PatientAllergyTestState>(result)
      }
    assertTrue(
      error.message?.contains("noSuchColumn") == true,
      "expected the error to name the bad matchKey, was: ${error.message}",
    )
  }
}
