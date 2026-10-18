# 0074: QR codes come from ZXing, behind the domain seam

Date: 2026-10-18. Status: accepted. Narrows the QR row of 0020 for v1.

## Context

Pairing needs both halves of QR: the sharing device draws a code (step 7 slice 4), the joining
device reads one with its camera (slice 5). The stack table already says ZXing over ML Kit, but
"ZXing / zxing-cpp" is two different libraries: zxing-cpp is C++ with JNI wrappers, and 0011 keeps
native code out of v1 (the F-Droid and SQLCipher motive). Drawing also has to happen in shared
Compose code, so the matrix crosses a seam rather than a library type (0002's platform split, 0012).

## Decision

- The encoder is `com.google.zxing:core` (Apache-2, pure Java): `qrcode.encoder.Encoder`, asked for
  modules instead of a pixel size, with charset ISO-8859-1 so the binary payload goes through byte
  mode 1:1 and error correction level M for a phone reading a screen.
- The scanner is `com.journeyapps:zxing-android-embedded` (Apache-2), which brings ZXing core with
  it: it owns the camera lifecycle and hands its result back through the activity-result API, the
  seam `SafSnapshotFiles` already uses.
- Both live in the Android shell behind `QrCodes` in `apps/todo/domain`; the shared UI only sees a
  `QrMatrix` of modules. A web build swaps in `BarcodeDetector` or a JS library (0012's table), and
  a build without a camera needs only `encode`.

## Rejected

- zxing-cpp: a native `.so`, which 0011 rules out for v1.
- ML Kit: proprietary Google, which AGENTS.md rules out.
- CameraX with ZXing core by hand: no third-party dependency, but ~200 lines of camera lifecycle,
  rotation and luminance plumbing that would prove nothing about the suite's thesis.
- Writing the encoder: Reed-Solomon, masking and version selection are not our code to own, and 0010's
  "don't roll your own" instinct applies wherever a library can be composed instead.
- Carrying ZXing types through the UI: it would put a JVM library into `commonMain` and block the
  web target.

## Consequences

About 40 lines of ours, two Apache-2 artifacts, and a camera permission that arrives with the scan
slice. The reader half is a whole Activity we do not theme, which is the price of not owning a
camera; its prompt text is customizable through `ScanOptions` when we get there.
