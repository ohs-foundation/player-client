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

import kotlin.jvm.JvmInline

/**
 * Opaque string label used to dispatch a renderer for a particular visual role.
 *
 * Paired with a data-type [kotlin.reflect.KClass] in [ViewTypeKey] to form the registry's lookup
 * key. View-types are normally declared once per app as constants.
 *
 * ```
 * val Card = ViewType("Card")
 * val PatientHeader = ViewType("PatientHeader")
 * ```
 *
 * @param value the underlying identifier; conventionally PascalCase.
 */
@JvmInline value class ViewType(val value: String)
