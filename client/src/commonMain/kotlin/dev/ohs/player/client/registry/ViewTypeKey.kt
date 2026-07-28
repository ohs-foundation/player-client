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
package dev.ohs.player.client.registry

import kotlin.reflect.KClass

/**
 * Composite lookup key for the [ViewRegistry]: `(view-type × data type)`.
 *
 * Both dimensions are part of the key so the same [ViewType] name registered for different `T`s
 * never collides. Looking up `(Card, PatientView)` and `(Card, MedicationView)` return distinct
 * renderers; querying with the wrong `T` throws rather than returning a mismatched renderer.
 *
 * ```
 * val key = ViewTypeKey(ViewType("Card"), PatientView::class)
 * ```
 *
 * @param viewType the visual role label.
 * @param dataType the Kotlin class of the data model that this renderer handles.
 */
data class ViewTypeKey<T : Any>(val viewType: ViewType, val dataType: KClass<T>)
