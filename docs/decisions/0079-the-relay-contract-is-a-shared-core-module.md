# 0079: The relay contract is a KMP core module over an injected HTTP engine

Date: 2026-10-31. Status: accepted. Extends 0017 and 0030.

## Context

The relay and the clients must agree on paths, status codes, headers and the bearer token; if each
side owns its copy, they drift. `server/` is a Kotlin/JVM application, so it can depend on a KMP
module's `jvm` target (decision 0017). Ktor must not leak into `core/sync`, because the folder plug
does not use HTTP. TLS termination is also a choice: terminating it in the relay means certificate
management in every self-hosted deployment.

## Decision

- A new KMP module `core/relay` holds the wire contract (`RelayApi`, `RelayStatus`, `relayToken`)
  and, from v1 step 9 slice 4, `RelayPlug` over a Ktor `HttpClient`. `server/` depends on its `jvm`
  target, so both sides compile against one contract.
- `RelayPlug` takes an `HttpClientEngine`; the Android shell injects OkHttp, and tests use Ktor's
  in-process test client. `core/relay` never picks a platform engine itself.
- The relay speaks plain HTTP. A reverse proxy or the hosting platform terminates TLS. The app
  accepts only `https://` relay URLs; debug builds also accept `http://` for LAN and emulator
  tests.
- Names are validated with the shared `ObjectKey` grammar rather than a second rule, so a name
  that is not a 0029 key is a `400` and `..` or a slash never reaches the filesystem.

## Rejected

- The server owning the contract and the client duplicating it: cheap now, guarantees drift.
- Ktor in `core/sync`: pulls an HTTP stack and a client engine into the folder path and the
  simulator.
- The relay terminating TLS itself: certificates and renewal in every deployment, and the host's
  proxy already does it better.
- Accepting `http://` in release builds: the bearer token would travel in clear text.

## Consequences

One contract compiles into both sides, and the relay's `jvm` target keeps the client code out of
the server jar's runtime. A reverse proxy becomes a documented deployment requirement. The
contract can still gain server-only fields without the client knowing, as long as the shared
types stay compatible.
