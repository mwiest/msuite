# 0090: Each backend type remembers its last destination, the S3 secret included

Date: 2026-11-15. Status: accepted. Replaces 0087's "deleted when another backend is chosen" (the
rest of 0087 stands). Builds on 0089.

## Context

Choosing another sync method threw away the previous one's settings: Off cleared the S3 fields,
and leaving S3 deleted its secret (0087). A user who tries Dropbox while S3 already works, and
prefers S3 after all, had to type the whole bucket configuration again.

## Decision

`TodoSettings` keeps one destination per backend type (folder URI, relay URL, S3 configuration),
chosen or not; choosing a type again replaces only that type's. Settings starts each form from it.
The S3 secret stays wrapped under the KeyStore while another backend is chosen. There is no
"forget" action: a destination is forgotten when another one of the same type replaces it.

## Rejected

- Delete the secret, keep the rest: no credential for an unused backend, but switching back means
  typing the secret again, which defeats the point.
- A list of past destinations per type: more UI and state than a proof of concept needs.

## Consequences

A bucket secret can sit on the device unused, with the same protection as while in use. The
per-plug bookkeeping of a remembered destination stays useful, so switching back resumes where it
left off; 0089 makes sure cells that arrived meanwhile still reach it.
