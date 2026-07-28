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

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The active [ViewRegistry] for the composition. Defaults to an empty registry so direct-pass DSL
 * paths (e.g. `component(MyRenderer(), MyConfig())`) work without a provider. Provide an app-level
 * registry via:
 * ```
 * CompositionLocalProvider(LocalViewRegistry provides registry) { ... }
 * ```
 */
val LocalViewRegistry = staticCompositionLocalOf { ViewRegistry() }
