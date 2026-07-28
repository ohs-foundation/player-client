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

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serializers for the FHIR R4 scalar types that ViewDefinition/ViewConfig values and the generated
 * state classes carry. They read each type's canonical representation: a `BigDecimal` from a JSON
 * number or string (precision preserved), and `FhirDate`/`FhirDateTime` from their ISO strings.
 *
 * kotlin-fhir's own model serializers cover these types inside resource models, but they are
 * `internal`, so our own `@Serializable` classes still need these. They are attached directly via
 * `@file:UseSerializers(...)` on the classes that reference them — no contextual
 * `SerializersModule` or specially-configured `Json` is required; a plain `Json` resolves them.
 */
object FhirDecimalSerializer : KSerializer<BigDecimal> {
  override val descriptor = PrimitiveSerialDescriptor("decimal", PrimitiveKind.STRING)

  // Read the raw JSON token (number or string) so no precision is lost via Double.
  override fun deserialize(decoder: Decoder): BigDecimal =
    BigDecimal.parseString((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)

  override fun serialize(encoder: Encoder, value: BigDecimal) =
    encoder.encodeString(value.toStringExpanded())
}

object FhirDateSerializer : KSerializer<FhirDate> {
  override val descriptor = PrimitiveSerialDescriptor("date", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): FhirDate {
    val raw = decoder.decodeString()
    return FhirDate.fromString(raw) ?: throw SerializationException("Invalid FhirDate: '$raw'")
  }

  override fun serialize(encoder: Encoder, value: FhirDate) = encoder.encodeString(value.toString())
}

object FhirDateTimeSerializer : KSerializer<FhirDateTime> {
  override val descriptor = PrimitiveSerialDescriptor("dateTime", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): FhirDateTime {
    val raw = decoder.decodeString()
    return FhirDateTime.fromString(raw)
      ?: throw SerializationException("Invalid FhirDateTime: '$raw'")
  }

  override fun serialize(encoder: Encoder, value: FhirDateTime) =
    encoder.encodeString(value.toString())
}
