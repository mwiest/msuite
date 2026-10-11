# 0030: Relay access uses a token derived from the root

Date: 2026-10-11. Status: accepted. Extends 0017.

## Context

The relay design had no access control. Anyone who could reach it could fill its disk with `put`,
read every namespace's metadata with `list`, and later use `delete`. Accounts are ruled out.

## Decision

- Each namespace `{appId}/{groupId}` (0029) has an access token
  `HKDF-SHA256(root, info = "msuite-relay-auth-v1:" + appId)`, sent as a bearer token over TLS.
- The relay stores only `SHA-256(token)` per namespace. The first `put` to an unknown namespace
  registers it (trust on first use); afterwards every request to that namespace must present the
  matching token.
- The operator can restrict who may register namespaces (open, or an allowlist in the relay's
  configuration) and set per-namespace size limits.

## Rejected

- Accounts or passwords: against the principles.
- A shared server-wide password: every user could read every other user's metadata.
- Signing requests with the root: needs asymmetric crypto or more protocol for no real gain over a
  bearer token under TLS.

## Consequences

Devices of a group need nothing beyond the root. The relay can't derive the root from the token.
Anyone who knows a namespace and its token has full access to its ciphertext, which equals knowing
the root. On an open relay, strangers can still register new namespaces; limits are the operator's
tool against that.
