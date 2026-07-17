# OHS Player Reference Client Library

A Kotlin Multiplatform library that renders healthcare UI from configuration instead of
hand-written mapping code. FHIR resources are projected into typed view-state by declarative
[SQL-on-FHIR ViewDefinitions](https://sql-on-fhir.org/), and that state is rendered by Compose
Multiplatform renderers resolved through a registry.

The library can be used by any project, new or existing. The
[OHS Player Reference Client App](https://github.com/ohs-foundation/ohs-player-reference-client-app)
is one such project: a complete client built with this library that serves as a working example of
everything described below.

## Why use this library

Most healthcare apps spend a large share of their UI effort on the same problem: getting data out
of FHIR and onto the screen. FHIR resources are deeply nested, almost every field is optional, and
each screen ends up with its own hand-written mapping code, duplicated per platform and rewritten
every time a card or form changes. This library moves that work into configuration:

- **Starting a new healthcare app?** Skip the mapping layer entirely. Declare each screen's fields
  as FHIRPath columns in a `ViewDefinition`, receive flat typed Kotlin classes back, and render
  them with your own Compose renderers, on Android, iOS, desktop, and web from a single codebase.
  You spend your first months on product, not on FHIR plumbing.
- **Already have an app?** Adopt it one screen at a time. This is a library, not a framework: it
  imposes no navigation, theming, or data layer, and renderers are ordinary composables you write.
  Each adopted screen shrinks to configuration plus a small renderer, and because configuration
  can be loaded from your backend through a `ConfigSource`, changing what a screen shows no longer
  requires shipping an app release.
- **Building on Open Health Stack?** This library is part of the OHS Player effort and sits on top
  of the OHS Foundational Libraries, `fhir-model` for typed FHIR models and `fhir-path` for
  FHIRPath evaluation, and pairs naturally with `fhir-data-capture` for questionnaires. If your
  stack is [Open Health Stack](https://developers.google.com/open-health-stack/overview), this is
  the display half of the same approach: standards-based configuration in, working UI out.

## Installation

```kotlin
commonMain.dependencies {
  implementation("dev.ohs.player:reference-library:1.0.0-alpha01")
}
```

> The library is not yet published to Maven Central. Until it is, clone this repository, run
> `./gradlew publishToMavenLocal`, and add `mavenLocal()` to your repositories.

## Usage

### 1. Author configuration

A `ViewDefinition` declares the columns of a view as FHIRPath expressions over a FHIR resource.
A `ViewJoinMap` names the view-state and binds it to a pivot `ViewDefinition` (and, where needed,
joined views).

```json
{
  "resourceType": "https://sql-on-fhir.org/ig/StructureDefinition/ViewDefinition",
  "name": "PatientSummary",
  "select": [
    {
      "column": [
        { "name": "patientId", "path": "id", "type": "http://hl7.org/fhir/StructureDefinition/string" },
        { "name": "familyName", "path": "name.family.first()", "type": "http://hl7.org/fhir/StructureDefinition/string" }
      ]
    }
  ]
}
```

```json
{
  "resourceType": "http://ohs.dev/StructureDefinition/ViewJoinMap",
  "name": "patientSummary",
  "from": "root",
  "resource": "Patient",
  "view": "PatientSummary"
}
```

### 2. Define a view-state class

A view-state is a flat `@Serializable` data class with one property per column. The class name
selects the configuration: `PatientSummaryState` resolves the ViewJoinMap named `patientSummary`.

```kotlin
@Serializable
data class PatientSummaryState(
  val patientId: String? = null,
  val familyName: String? = null,
)
```

### 3. Load configuration

Implement `ConfigSource` to supply the configuration JSON from wherever it lives (bundled files, a
server, ...), then wire a `ConfigStore` and one `GenericStateExtractor`:

```kotlin
object MyConfigSource : ConfigSource {
  override suspend fun readAll(): List<String> = TODO("return each config resource as a JSON string")
}

val extractor = GenericStateExtractor(ConfigStore(MyConfigSource))
```

### 4. Extract view-state

`extract<T>()` evaluates the configuration for `T` against a `SearchResult` (the pivot resource
plus any included resources, mirroring a FHIR search response) and returns typed rows.

```kotlin
val patients: List<PatientSummaryState> = extractor.extract(searchResult)
```

The FHIRPath engine holds mutable evaluation state and is not safe for concurrent use; confine
extraction to a single-threaded dispatcher, for example
`Dispatchers.Default.limitedParallelism(1)`.

### 5. Render

Write a `ComponentRenderer` for the state type, register it under a view-type, install the
registry at the composition root, and let `ListScaffold` (or `DetailScaffold`) resolve it:

```kotlin
data class PatientCardConfig(val showId: Boolean = true)

class PatientCardRenderer : ComponentRenderer<PatientSummaryState, PatientCardConfig> {
  @Composable
  override fun Render(item: PatientSummaryState, config: PatientCardConfig, options: RenderOptions) {
    PatientCard(patient = item, config = config, onClick = options.onClick, modifier = options.modifier)
  }
}

val PatientCard = ViewType("PatientCard")

@Composable
fun App() {
  val registry = remember {
    ViewRegistry().apply { registerComponent(PatientCard, PatientCardRenderer(), PatientCardConfig()) }
  }
  CompositionLocalProvider(LocalViewRegistry provides registry) {
    ListScaffold<PatientSummaryState>(
      items = patients,
      onItemClick = { /* navigate */ },
      key = { it.patientId ?: it.hashCode().toString() },
    ) {
      component(PatientCard)
      emptyState { Text("No patients") }
    }
  }
}
```

The library ships `VerticalListRenderer` (the default), `HorizontalListRenderer`, and
`GridListRenderer` for arranging lists; register your own `LayoutRenderer` for anything else.

## Development

```shell
./gradlew :reference-library:jvmTest    # fast local iteration
./gradlew :reference-library:allTests   # all targets
./gradlew spotlessApply                 # format before committing
```

## Contributing

Issues and pull requests are welcome. Run the tests and `spotlessApply` before submitting.

## License

Apache License, Version 2.0. See [LICENSE](./LICENSE).
