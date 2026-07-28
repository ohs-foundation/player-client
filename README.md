# OHS Player Reference Client Library

A Kotlin Multiplatform library for rendering configuration-driven FHIR views with Compose
Multiplatform. Views are declared as FHIR `ViewDefinition`, `ViewJoinMap`, and `ViewConfig`
resources; the library extracts view state with FHIRPath and resolves renderers through a
pluggable view registry.

Extracted from the
[OHS Player Reference Client App](https://github.com/ohs-foundation/ohs-player-reference-client-app),
which remains the reference consumer of this library.

## Modules

| Module | Description |
|---|---|
| `reference-library` | The library (`dev.ohs.player:reference-library`), package `dev.ohs.player.reference.library` |

## Supported targets

Android, iOS (arm64, simulator arm64), Desktop (JVM), Web (JS and Wasm).

## Building and testing

```shell
# All targets
./gradlew :reference-library:allTests

# Fast local iteration (desktop JVM only)
./gradlew :reference-library:jvmTest
```

## Publishing

```shell
# To the local Maven repository, for consumption via mavenLocal()
./gradlew :reference-library:publishToMavenLocal
```

Coordinates and version are set in [`gradle.properties`](./gradle.properties).

## License

Apache License, Version 2.0. See [LICENSE](./LICENSE).
