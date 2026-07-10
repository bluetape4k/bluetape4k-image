# Issue #202 Design — Decouple the Binding-Neutral Vips API

## Context

`bluetape4k-images-vips-api` promises a binding-neutral libvips contract, but
its main artifact declares `api(project(":bluetape4k-images"))`. That project
exports Scrimage and TwelveMonkeys dependencies, so a consumer of only
`VipsImage` and `VipsRuntime` receives the Scrimage/Java2D stack on its public
compile boundary.

Current source inspection shows that the main Vips API source imports only
`io.bluetape4k.images.IncubatingImageApi` from `bluetape4k-images`. Its test
fixtures separately use `bluetape4k-images` for pixel comparison through
`VipsGoldenAssert`.

## Goals

1. Remove `bluetape4k-images` from the main Vips API artifact's public
   dependency graph.
2. Keep AVIF/HEIC capability markers explicit and opt-in guarded for Vips API
   consumers.
3. Preserve the Vips test-fixture pixel-comparison support without publishing
   Scrimage as a main-artifact dependency.
4. Make the generated Maven POM the release evidence for the intended boundary.

## Non-Goals

- Do not add a shared module or a new external dependency.
- Do not change libvips JNI/FFM runtime behavior, codec detection, or image
  encode/decode semantics.
- Do not remove or rename `IncubatingImageApi` from `bluetape4k-images`; it
  remains the opt-in marker for Scrimage-family APIs.
- Do not publish or release artifacts in this issue.

## Verified Constraints

- Gradle requires a public annotation type to be an API dependency; changing
  the existing project dependency to `implementation` would hide a type used
  by Vips public annotations instead of repairing the ABI boundary.
- `images-vips-api` has a `generatePomFileForBluetapeImagePublication` task.
- The only main-source dependency on `bluetape4k-images` is the existing image
  incubating annotation. Test-fixture support is a separate dependency scope.
- The existing Vips API README has English and Korean variants and must stay in
  sync for this user-visible migration.

## Approaches Considered

### A. Reclassify `bluetape4k-images` as `implementation`

Rejected. `IncubatingImageApi` is a public annotation type on Vips API
declarations. Hiding it from consumers leaves the public contract incomplete
and does not make the API genuinely binding-neutral.

### B. Add a Vips-owned opt-in annotation and remove the main project dependency

Selected. Add `VipsIncubatingApi` under
`io.bluetape4k.images.vips`, migrate Vips-only capability declarations to it,
and remove the main `api(project(":bluetape4k-images"))` dependency. Keep the
test-fixture project dependency because it is not part of the main publication.

This gives consumers an explicit, Vips-scoped opt-in surface and keeps the
existing image-module annotation independent.

### C. Introduce a new shared image-contract module

Rejected. A module added only to share one opt-in marker would expand settings,
BOM, CI, Nightly, coverage, and consumer migration scope without improving the
boundary addressed by this issue.

## Selected Design

### Public Contract

Create `VipsIncubatingApi` as a `@RequiresOptIn(Level.WARNING)`,
`@MustBeDocumented`, binary-retained annotation in the Vips API package. Its
message states that Vips codec capabilities may change and gives the exact
`@OptIn(VipsIncubatingApi::class)` migration path.

Replace `IncubatingImageApi` usage only within Vips API declarations:

- `VipsImageFormat.AVIF` and `VipsImageFormat.HEIC`.
- `VipsCodecCapability` and `VipsCodecCapabilityReport` where they expose
  those incubating formats.

`IncubatingImageApi` remains unchanged in `bluetape4k-images`. Existing callers
that explicitly opted into Vips AVIF/HEIC functionality must change their
opt-in import to `VipsIncubatingApi`; this is an intentional source migration
for an already incubating API and must be documented in both Vips API READMEs.

### Dependency Boundary

Remove `api(project(":bluetape4k-images"))` from the main dependencies of
`images-vips-api`. Keep `testFixturesApi(project(":bluetape4k-images"))` for
`VipsGoldenAssert`, because fixture-only dependencies must not establish the
main library POM contract.

No main-source type may import `io.bluetape4k.images.*` outside the Vips package
after the migration. Existing `bluetape4k-core`, IO, Okio, and coroutine
dependencies remain intentional Vips API dependencies.

### Documentation and Migration

Update `images-vips-api/README.md` and `README.ko.md` to:

- state that the main Vips API artifact does not require the Scrimage image
  implementation artifact;
- show the new Vips-specific opt-in import for AVIF/HEIC;
- distinguish main-artifact dependencies from test-fixture support;
- keep native libvips codec availability caveats unchanged.

Add English KDoc to the new public annotation and update the relevant Vips KDoc
references to its exact name.

## Verification Design

1. Compile and run `:bluetape4k-images-vips-api:test`.
2. Compile both backend consumers (`:bluetape4k-images-vips-java21:compileKotlin`
   and `:bluetape4k-images-vips-java25:compileKotlin`) to prove they use the
   revised public contract.
3. Generate the Vips API publication POM with
   `:bluetape4k-images-vips-api:generatePomFileForBluetapeImagePublication`.
4. Assert that the generated main POM has no dependency on
   `bluetape4k-images`, Scrimage, or TwelveMonkeys; retain an allow-list of
   intentional API dependencies.
5. Add focused tests that compile/use the Vips opt-in marker and preserve
   codec capability model behavior.
6. Verify English/Korean README claims against the generated POM and current
   source names, then run `git diff --check`.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Consumers cannot compile Vips AVIF/HEIC code after the boundary change | Document the exact Vips opt-in migration and compile both backend modules. |
| A fixture dependency leaks into the main POM | Inspect the generated main POM, not only Gradle source declarations. |
| Public annotation migration is accidentally broadened to Scrimage APIs | Restrict source changes to `images-vips-api`; assert `IncubatingImageApi` remains in `images`. |
| Runtime codec behavior changes during the refactor | Do not change backend/runtime code; retain existing codec tests and run targeted Vips API tests. |
| Publication metadata uses a different configuration than local resolution | Use the actual `BluetapeImage` POM generation task as the acceptance evidence. |

## Acceptance Criteria

- `bluetape4k-images-vips-api` main publication does not expose
  `bluetape4k-images`, Scrimage, or TwelveMonkeys.
- Vips AVIF/HEIC capability APIs remain explicitly opt-in with a public,
  Vips-scoped marker.
- Test-fixture support remains usable without changing the main artifact
  boundary.
- Both backend modules compile against the revised API.
- README/KDoc accurately describe the dependency and migration boundary.

## Rollback

Before publication, reverting the feature branch restores the existing POM and
annotation contract. If generated-POM validation reveals an unaccounted public
type dependency, stop before PR creation, retain the evidence, and revise the
spec/plan instead of reintroducing the broad `images` API dependency by default.
