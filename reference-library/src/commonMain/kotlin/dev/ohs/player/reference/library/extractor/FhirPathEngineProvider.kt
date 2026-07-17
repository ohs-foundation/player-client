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

import dev.ohs.fhir.fhirpath.FhirPathEngine

/**
 * Shared, lazily-initialized R4 FHIRPath engine. [GenericStateExtractor] uses it by default.
 *
 * The engine holds mutable evaluation state and is therefore not safe for concurrent evaluation:
 * callers sharing this singleton must serialize their use of it (e.g. a single-threaded
 * dispatcher).
 */
object FhirPathEngineProvider {
  val r4: FhirPathEngine by lazy { FhirPathEngine.forR4() }
}
