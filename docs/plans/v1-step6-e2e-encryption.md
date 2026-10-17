# Plan: v1 step 6, the encrypted batch envelope

Status: **not started.** Started 2026-10-17. A slice of
[`v1-todo.md`](v1-todo.md) step 6.

## Goal

Batches travel as ciphertext: the manual export file, and every object a future plug writes, is
AES-256-GCM over the encoded batch under a per-batch HKDF key, so a backend — folder, bucket,
relay — sees only opaque bytes (decisions 0010, 0028). The root exists on the device, generated on
first use and wrapped at rest by the Android KeyStore (0039), so the manual export stops being a
plaintext proof of concept. Simulator Layer 3 proves encrypted batches still converge and a
renamed batch fails to decrypt (invariant 17).

Out of scope, and why it matters here: pairing and recovery are step 7, so a snapshot file only
imports back on the device that exported it, and a reinstall starts empty with no way back. The
step 5 two-device check landed on 2026-10-17, before slice 3 encrypts the file: from then on, two
devices without a shared root can no longer exchange it.

## Decisions

- The data-model version moves from the batch body to the envelope header, and the body carries
  cells only (decides the open question in `v1-todo.md`). The header is cleartext and
  authenticated as AAD, so `TooNew` (0067) is decidable before decrypting, and 0045's "one place
  only" holds. Record 0070 with the slice that lands it; it supersedes that part of 0045.
- The envelope is the sketch in `architecture.md` → Batch envelope: header = 256-bit random
  `salt`, data-model version, `keyGen` id (0008); `K = HKDF-SHA256(root, salt,
  "msuite-batch-v1")`; AES-256-GCM with a fixed zero nonce — a fresh key per salt makes nonce
  reuse impossible (0010); the content-address `hash` is over the ciphertext (0003, 0029).
- The AAD is the header plus a context: the whole object key except `hash` for plug objects
  (0029), and a constant label for the snapshot file, which has no object key — so a plug
  object's ciphertext cannot be replayed as a manual file or the other way round. Record 0071
  with the slice that lands it.
- Envelope decode order: parse the header; `TooNew` and an unknown `keyGen` are reported without
  decrypting and never count toward the corrupt flag (0069 counts bytes that should decode for
  us); then decrypt, where a torn, tampered, renamed or foreign-root batch all end in a failed
  GCM open, wrapped as `MalformedBatch` — which `decodeOrNull` already swallows (0068). A rename
  also counts toward the corrupt flag: the bytes are unreadable at that key forever (invariant 17
  with 0069).
- The ciphertext is computed once and cached per batch, so retries resend identical bytes and the
  content address stays stable (`architecture.md` → Batch envelope). Layer 3's store caches it;
  the production push loop gets its cache with the folder plug (step 8).
- The key grammar builder (0029) lives in `core/sync` — keys are the plug caller's naming — over
  `core/crypto`'s HKDF (`groupId`) and SHA-256 digest.
- At rest (0039): the root is wrapped by a non-exportable AES-256-GCM Android KeyStore key, the
  wrapped root in a private file; plain JCA in the shell behind the app-layer seam, so
  `core/crypto` never sees the KeyStore. The root is generated on first use, 256 bits from the
  library's RNG, behind `IdentityProvider` (`architecture.md` → Identity). Backup is already off
  (0060).
- Layers 1 and 2 keep a plaintext transport; its body drops the version with the envelope's, so
  there is one body layout and the version's one place is the header (decided 2026-10-17).

## Slices

Each slice is one commit, builds, and leaves the build green.

1. [ ] **Crypto primitives.** Wire cryptography-kotlin (the pinned `cryptography-core`, plus the
   provider artifacts it needs for `jvm` and `android`; web stays buildable, 0012): SHA-256,
   HKDF-SHA256, AES-256-GCM, the RNG. No hand-rolled primitives (0010). Checked by jvmTest:
   seal/open round trip, a tampered ciphertext or a wrong AAD fails, the same salt gives identical
   bytes, fresh salts give distinct ciphertexts.
2. [ ] **The envelope, and the version moves to the header.** `core/crypto` encodes and opens the
   envelope; the batch proto's field 1 becomes reserved and the plaintext transport drops it with
   the envelope; `Batch.dataModelVersion` is filled by the decoder — from the header for the
   envelope, the current constant for the plaintext transport — and `ingest` keeps its check
   (0067). The pinned-bytes tests move with the format (it never shipped). Decision 0070.
3. [ ] **The encrypted manual plug.** `ManualSync` takes the encoder and goes through the
   envelope: export encrypts with the manual context label, import maps `TooNew` and an unknown
   `keyGen` from the header, and a torn, tampered or foreign-root file fails as `MalformedBatch`
   (Failed in the UI). The `sync_manual_hint` strings and `product.md` now say the file is
   encrypted and only imports back on this device until pairing (step 7). Decision 0071.
   Emulator check: the exported file holds no plaintext.
4. [ ] **The root at rest.** `IdentityProvider`: the root generated on first use, the wrap behind
   the app-layer seam (0039); the shell wraps with a non-exportable KeyStore AES-256-GCM key into
   a private file, plain JCA; `TodoApplication` wires the provider into the encrypted `ManualSync`.
   JVM tests use a plain root. Emulator check: the root survives a restart.
5. [ ] **The object-key grammar, in `core/sync`.** Build and parse
   `{appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}`
   (0029): uppercase base32hex, unpadded, fixed widths, `groupId` = the first 128 bits of
   `HKDF-SHA256(root, "msuite-group-v1")`, the AAD context (key minus hash). Property tests:
   charset, widths, round trip, lexicographic sort equals value sort.
6. [ ] **Simulator Layer 3.** `PlugStore` through the envelope with real keys: the hash over the
   ciphertext, the AAD from the key, the ciphertext cached per batch so a re-publish is the same
   object. The convergence invariants again, plus 17 (a renamed key fails to decrypt, is not
   merged, and after the bounded retries is flagged corrupt, 0069); named scenarios: a TooNew
   header held back, an unknown `keyGen` skipped without the corrupt flag, a foreign root's object
   corrupt after the bounded retries. `testing.md` Layer 3 row and `architecture.md` → Batch
   envelope become code.

## Open questions

None left here: the version's new home and the grammar's module are decided above. The pairing-QR
question (does it carry the backend config behind?) is step 7's, in `v1-todo.md`.

## How to verify

`./gradlew allTests` green, Layer 3 included. On an emulator: export a snapshot and confirm the
file holds no plaintext; import it back on the same device after a restart (the root unwraps from
the KeyStore); import it on another device and see the honest failure — different roots, pairing
is step 7. A reinstall starts empty; recovery arrives with step 7.
