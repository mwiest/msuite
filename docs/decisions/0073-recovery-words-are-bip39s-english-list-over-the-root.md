# 0073: Recovery words are BIP39's English list, embedded, over the root

Date: 2026-10-18. Status: accepted. Builds the words of decision 0011.

## Context

0011 offers the root as words (BIP39), QR or file, and v1 step 7 builds the words. That leaves
three choices: which wordlist, whether to depend on a library, and whether to implement BIP39
whole. BIP39's second half (PBKDF2 over the words, 2048 rounds of HMAC-SHA512, to a 512-bit wallet
seed) exists to turn a mnemonic into keys; a passphrase protects that derivation. We carry a root
secret, not a wallet seed, so the first half — entropy to words and back — is the whole job.

## Decision

- Only BIP39's entropy encoding. The root is 256 bits, so the words are 24 (BIP39's `ENT=256,
  CS=8, MS=24`), the checksum is the first 8 bits of SHA-256 over the root, and the same root always
  gives the same words. No PBKDF2 step, no passphrase prompt: nothing to derive, and 0011 already
  rejected a passphrase.
- The English wordlist from `bip-0039/english.txt` (MIT, like the BIP), embedded verbatim as a
  Kotlin constant, with a test pinning the SHA-256 of the joined list (2f5eed53…) so an accidental
  edit fails loudly. BIP39's own words: "the vast majority of BIP39 wallets supports only the
  English wordlist" and non-English lists are "strongly discouraged".
- Decoding is case- and space-insensitive but demands all 24 words, so a half-remembered or
  truncated key says so rather than quietly becoming a shorter secret.

## Rejected

- A BIP39 library: a dependency to vet for web-capable and F-Droid terms (0020) for about 40 lines
  we would still have to test against the official vectors.
- A non-English or multi-language list: BIP39 discourages it, and a restore would have to try each
  list or guess from the words, so a German user's words stay English — an encoding, not prose, so
  0027 is untouched.
- Deriving a wallet seed from the words: it would replace the group root with a value derived from
  it, and break every batch already written under the root.
- A wordlist of our own: it would not be a BIP39 recovery key, so no other tool could read it.

## Consequences

A recovery key is 24 English words that can be written down, typed back or read aloud, and the tests
are the official vectors rather than invented ones. Like the QR, the words are the root: whoever
reads them has full access, for good (0008's accepted gap). The words carry no `keyGen`, unlike the
pairing QR (0072): v1 has one root generation, and rotation (0008) will have to decide how a paper
key says which generation it is. BIP39's list is frozen, so the pin should never need to change; a
different word count would follow from a different entropy size, and so from a new decision.
