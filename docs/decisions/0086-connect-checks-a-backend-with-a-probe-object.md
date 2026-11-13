# 0086: Connect checks a backend with a probe object before choosing it

Date: 2026-11-13. Status: accepted. Extends 0029 and 0079.

## Context

Connect in the relay and S3 forms used to validate the form and save it. A wrong region, bucket or
key showed only on the Sync status after the next poll, by when the working backend was already
replaced. Listing alone doesn't catch the common partial setups either: a read-only S3 token, a
key without delete rights (compaction needs it), or a relay whose allowlist refuses to register
the group all pass a listing and fail at the first publish.

## Decision

- Connect lists `{appId}/{groupId}/` with the new settings, writes a probe object there, and
  deletes it. Only if all three work does it save the settings and switch; otherwise the form shows
  the refusal (the same message as on the Sync status) or "can't reach", and the current backend
  stays.
- The probe name is `{appId}/{groupId}/probe_{32 lowercase hex}`, random per check (`ProbeKey` in
  `core/sync`). It never parses as a 0029 key, so sync skips it like any other foreign name. It sits
  inside the group's namespace because relay tokens and S3 key policies are scoped to it.
- The relay accepts a probe name besides the 0029 keys (`RelayApi.isValidKey`), and takes any body
  under it: there is no hash in the name to check. Registration, allowlist and limits apply as to
  any put.
- A scanned pairing code skips the check: it comes from a working device, and joining must not
  need the network.

## Rejected

- Save, then sync at once and show the result: a typo replaces a working setup before it is known
  to be wrong.
- Listing only: misses missing write and delete rights, the most common partial setup.
- An empty sealed delta under a real 0029 key as the probe: no protocol change, but a batch that
  every device ingests and that the replica's bookkeeping (compaction counts, own-object detection)
  would have to tolerate.
- Writing only on S3, listing only on the relay: leaves the relay's allowlist and quota refusals
  to the first publish.

## Consequences

A setup that Connect accepts can list, write and delete. An older relay refuses the probe name
with 400, which the form shows as "can't reach the relay", so a hosted relay must be redeployed
with this build. If the delete fails after the write, the probe object stays; sync ignores it,
but it makes the listing non-empty, so on that backend a wiped namespace no longer looks empty
(0082) until someone deletes the probe.
