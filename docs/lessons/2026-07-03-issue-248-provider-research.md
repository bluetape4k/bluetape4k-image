# Issue #248 Commercial and Native Provider Research

## Context

#248 evaluated whether commercial or native barcode engines should become
optional providers after the API, ZXing provider, BoofCV research, and fixture
documentation landed.

## Decision or Finding

Do not add commercial or native providers for 0.4.0. Dynamsoft and Aspose are
future optional commercial candidates only after license and CI policy exists.
OpenCV is not broad enough to replace ZXing as a barcode provider. ZBar carries
native/JNI and LGPL review cost that does not fit the default JVM module path.

## Outcome

The provider architecture remains API plus ZXing for 0.4.0. #248 should close as
research, with no follow-up implementation issues created yet.

## Verification

Research checked current vendor docs, pricing/license pages, Maven/package
metadata, and native/runtime documentation. The same conclusion was preserved in
`bluetape4k-wiki` for cross-repo retrieval.

## Future Guidance

If a customer or downstream module needs a commercial barcode backend, open a
new issue only after license key handling, CI secret policy, redistribution
terms, and runtime platform targets are explicit.
