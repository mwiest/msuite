# 0084: S3 requests are signed by our own SigV4

Date: 2026-11-10. Status: accepted.

## Context

The S3 plug (plan `v1-s3-plug.md`) needs four calls: `PUT`, `GET`, `DELETE` and `ListObjectsV2`.
Every S3-compatible store (AWS, R2, B2, MinIO, Garage) authenticates them with AWS Signature
Version 4. The plug must stay KMP and web-capable (decision 0012) and lean on dependencies (0020).

## Decision

`core/s3` signs requests itself (`SigV4`). It uses SHA-256 and HMAC-SHA256 from cryptography-kotlin
through `core/crypto` (decision 0010), and the requests go out through the Ktor client. It
implements the S3 form only: the path is percent-encoded once and never normalized, and every
header given is signed. AWS's SigV4 test-suite vectors (as botocore ships them) are its tests.
SigV4 is a request-signing protocol over vetted primitives, not new cryptography, so the "don't
roll your own crypto" rule holds.

## Rejected

- aws-sdk-kotlin: JVM-only today, large, and pulls in its own HTTP and credential stack for four
  calls.
- minio-java: JVM-only, and brings OkHttp, Jackson and Guava.
- A third-party KMP SigV4 library: none is maintained widely enough to trust with credentials.

## Consequences

No new dependency, and the plug works wherever Ktor and cryptography-kotlin do, the web target
included. We own about 130 lines of protocol code; the vectors pin it, and the device check against
a real bucket covers the S3-specific parts (the payload-hash header and single path encoding). Revisit if
the plug needs more of S3 (multipart uploads, presigned URLs, STS session tokens).
