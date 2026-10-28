# 0076. Apps declare document providers in the manifest, so SAF folders survive

**Date**: 2026-10-28. Status: accepted.

## Context

The synced-folder plug's SAF store depends on a document provider picked by the user
(e.g. Dropbox). On Android 11+ package visibility, the persisted tree-URI grant is only one
access path — the provider package must also be *visible* for `resolveContentProvider` and the
bind to succeed. Grant-derived visibility of the provider package decays on real devices: after
a `adb install -r` and an app restart on the OnePlus Nord (Android 16), `com.dropbox.android`
became invisible while prefs and grants stayed intact — `resolveContentProvider` returned null,
`ActivityThread` logged `Failed to find provider info` on every poll, and every SAF query
silently returned an empty folder (`DocumentFile` swallows the exceptions). That made a healthy
folder look empty and fed the `ingested_batch` pruning (plan `v1-step8b-dropbox-conflicts`,
finding F6 tier 1).

## Decision

Every app that uses the folder plug declares document providers statically in its
`AndroidManifest.xml`:

```xml
<queries>
    <intent>
        <action android:name="android.content.action.DOCUMENTS_PROVIDER" />
    </intent>
</queries>
```

This keeps every installed document provider statically queryable, independent of grants.
Note the action: documents providers serve `android.content.action.DOCUMENTS_PROVIDER` (the
`DocumentsContract` contract intent). The `ACTION_OPEN_DOCUMENT_TREE` intent is served by the
system picker and declared as a queries intent it matches nothing — verified the picker-intent
form did not restore visibility, the provider intent did.

## Rejected

- **Relying on grant-derived visibility**: this is exactly what decayed silently
  on-device; SAF queries then fail by returning empty lists, which the sync reads as
  "folder emptied" and prunes ingested state.
- **Naming the specific provider package** (`com.dropbox.android`, …) per `<package>`: brittle
  against future backends (Nextcloud, Syncthing) and duplicates what the contract intent
  already covers.

## Consequences

Provider discovery no longer silently dies. A genuinely missing tree grant (provider visible,
but `SecurityException: requires that you obtain access using ACTION_OPEN_DOCUMENT`) remains a
separate failure tier the app must still handle explicitly (plan F6 tier 2) — `<queries>` does
not fix that. Useful diagnostics for telling the tiers apart log the tree root's display name
and the persisted grants.
