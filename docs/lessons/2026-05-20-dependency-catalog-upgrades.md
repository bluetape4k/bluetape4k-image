# Dependency Catalog Upgrades

## Context

`bluetape4k-dependencies` folded the AWS SDK Java Dependabot PR into the
central dependency upgrade batch.

## Decision

Materialize the central AWS SDK Java catalog version in this repository.

## Outcome

`gradle/libs.versions.toml` now carries AWS SDK Java `2.44.9`.

## Verification

- `./gradlew build -x test --no-daemon`
