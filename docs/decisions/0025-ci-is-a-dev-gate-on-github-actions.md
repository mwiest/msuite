# 0025: CI is a dev gate on GitHub Actions; Renovate keeps dependencies current

Date: 2026-07-05. Status: accepted.

## Context

The repo is on GitHub (`github.com/mwiest/msuite`). F-Droid builds release APKs itself,
reproducibly, from its metadata.

## Decision

GitHub Actions runs the quality gate on every push and pull request: formatting and static
analysis (0026), the tests including the merge/HLC simulator on `jvm`, debug APK assembly and the
relay jar. It is not a release pipeline. Renovate opens dependency updates, using its support for
`libs.versions.toml`.

## Rejected

- CI as the release pipeline: F-Droid does its own builds.

## Consequences

Dependency updates arrive as pull requests that must pass the gate. Coupled versions (Kotlin,
Compose compiler, Compose Multiplatform) need grouping in the Renovate config.
