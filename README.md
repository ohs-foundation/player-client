# OHS Player Reference Client Library

A Kotlin Multiplatform library that renders healthcare UI from configuration instead of
hand-written mapping code. FHIR resources are projected into typed view-state by declarative
[SQL-on-FHIR ViewDefinitions](https://sql-on-fhir.org/), and that state is rendered by Compose
Multiplatform renderers resolved through a registry.

It is used by the
[OHS Player Reference Client App](https://github.com/ohs-foundation/ohs-player-reference-client-app),
which serves as a complete working example of everything described below.

## Features

- **Configuration-driven extraction.** Declare *what* a screen shows as FHIRPath columns in a
  `ViewDefinition`; the library evaluates them against FHIR search results and returns plain,
  serializable Kotlin data classes.
- **Registry-based rendering.** Screens ask for a *view-type*, not a concrete composable, so
  renderers can be swapped or reconfigured without touching screen code.
- **Multiplatform.** One source set targets Android, iOS, Desktop (JVM), and Web (JS and Wasm).

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
