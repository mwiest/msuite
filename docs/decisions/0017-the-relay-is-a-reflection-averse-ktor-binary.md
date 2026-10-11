# 0017: The relay is one reflection-averse Ktor CIO binary, in this repo

Date: 2026-07-05. Status: accepted; access control added by 0030; its v1 step moved by 0042.

## Context

Users who want live sync without a folder sync tool or a bucket need a server they can run
themselves or deploy cheaply. Its wire format must never drift from the clients'.

## Decision

`server/` is a single Kotlin/JVM Ktor binary, the same artifact for self-hosting and cloud
deployment, living in the monorepo and reusing the name grammar and contract types from `core`.
It stores opaque blobs only (0003) and is written to avoid runtime reflection, so a GraalVM native
image stays a configuration change:

- Ktor **CIO** engine with a programmatic `embeddedServer`.
- Filesystem blob storage.
- Manual wiring, no DI framework.
- kotlinx.serialization (compile-time code generation).

Residual reflection would be covered by GraalVM reachability metadata and a tracing-agent run in
CI. The native image itself is deferred: a plain JVM jar validates v1 step 8.

## Rejected

- Netty engine and HOCON module loading by class name: reflection.
- JDBC or native SQLite storage: reflection and native code.
- A relay on Cloudflare Workers: a JVM binary can't run there; Cloudflare users point the
  object-storage plug at R2 instead.

## Consequences

Cheap hosts work (Oracle Always-Free, PikaPods, Railway, Koyeb, fly.io). Some Ktor conveniences
that rely on reflection are off the table.
