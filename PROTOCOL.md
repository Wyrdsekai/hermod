# The hermod protocol

Version 2. The Java classes in `org.wyrdsekai.hermod` are normative; this
document describes what they do. Version 2 adds the device key to the
advertisement (§1) and defines the envelope's origin signature, which doors
now verify first (§4).

## §0 Invariants

1. **No center.** Every device holds its own capability table and
   routes its own requests. There is no coordinator to elect, lease, or
   lose.
2. **The executor owns admission.** Placement is a proposal. The
   receiving device re-checks eligibility, task type, data-domain
   residency, expiry, and its own state, and refuses freely. Refusal is
   the concurrency mechanism, not an error.
3. **Consent travels with the task.** A `SignedGrant` rides inside the
   envelope and is verified where the data lives. Deny by default: no
   authority key, no grant, no expired grant, no re-scoped grant.
4. **Presence is never routed.** This protocol moves work. Who is
   present, who is speaking, who is bonded — none of that belongs here,
   and transports MUST NOT multiplex it onto these subjects.
5. **A door knows who knocked.** Every envelope is signed by the device
   that sent it, with the key that device advertises. A door verifies
   that before it reads anything else in the envelope; a device that
   advertises no key cannot have an envelope admitted.

## §1 Advertisement (gossip)

Each device periodically publishes a `Capability`:

- `deviceId`, `scopeId` — identity and mesh scope.
- `capabilityClass` — what kind of work (`llm.local-gpu`,
  `llm.local-cpu`, `llm.phone`, or your own classes).
- `models` — optional model names offered.
- `residentDataDomains` — which consented data domains live here.
- `charging`, `idle`, `loadFactor` — **real** signals (normalized 1-min
  OS load; a mains-powered node is always "charging"; idle below half
  load). Lying here only hurts the liar: placement prefers idle,
  charging, lightly-loaded devices.
- `at` — the advertisement instant.
- `publicKey` — the device's Ed25519 public key, X.509 SPKI bytes
  (base64 in JSON). What the device's envelopes are verified against
  (§4). Absent in a version 1 advertisement; such a device's envelopes
  are refused.

Tables are last-write-wins **per device** with a TTL (reference: 90s).
A stale ad never regresses a fresher one (pinned by
`AStaleAdvertisementNeverRegressesTheTable`). A device that vanishes
simply ages out.

NATS mapping (reference transport): publish/subscribe on
`hh.<scope>.hermod.capability`, JSON via jackson with ISO-8601 instants.

## §2 Routing

`DefaultRouter.candidates(envelope)`:

1. `capabilityClass` must match (mandatory).
2. If the task `requiresGrant()`, the device must have the task's
   `dataDomain` resident — compute travels to the data, never the
   reverse.
3. Order by `idle` desc, then `charging` desc, then `loadFactor` asc.

`place()` proposes the first remaining candidate. On refusal the caller
asks for the next.

When benchmarking, warm the table first. A table that has heard only one
advertisement measures how fast gossip arrived, not how placement chose;
wait until every expected provider is visible before drawing conclusions.

## §3 The door (task RPC)

Single-offer protocol: one request carries the envelope; one reply
carries admission *and* result. There is no separate accept/complete
handshake — a door that cannot serve answers `Declined` immediately,
and a dead door times out into `Declined`. Either way the task moves
to the next candidate; it is never lost.

NATS mapping: request/reply on `hh.<scope>.hermod.door.<deviceId>`.

## §4 The envelope

`TaskEnvelope`: `envelopeId`, `originScope`, `originDevice`,
`taskType`, `dataDomain` (`"none"` when the task carries no
consent-bound data), `capabilityClass`, `params` (small string map —
this is a control plane, not a data plane), `maxTokens`, `issuedAt`,
`expiresAt`, optional `SignedGrant`, origin signature bytes.

The **origin signature** is the origin device's Ed25519 signature over the
envelope's stable fields (`EnvelopeSigning.signingBytes`): `envelopeId`,
`originScope`, `originDevice`, `taskType`, `dataDomain`, `capabilityClass`
each as `len‖bytes` (UTF-8, a null as empty); then the param count and the
params as `len‖key len‖value` in key order; then `maxTokens`, `issuedAt`
and `expiresAt` as 8-byte big-endian (the instants as epoch milliseconds);
then the grant id (empty when there is no grant) as `len‖bytes`. Lengths
and the count are 4-byte big-endian. The receiving door looks the origin up in its own
capability table and verifies the signature against the key in that
advertisement **before** expiry, budget or grant: an unknown origin, an
origin with no key, a signature that does not verify, or an envelope whose
scope differs from the origin's are refused with that reason
(`AnEnvelopeIsSignedByItsSender`). A grant, when present, must name the
envelope's scope and the origin's device class.

Reference task type: `inference.chat` with params `model`, `prompt`,
optional `system` → output is the completion text. Platforms may define
richer types (wyrdsekai adds `inference.chat.full` carrying a
serialized tool-bearing request); a node only accepts types its
executor `handles()`.

## §5 Grants

`GrantAuthority` signs `(grantId, domain, capabilityClass, expiry)`
with Ed25519 over **canonical length-prefixed bytes** — every field is
written as `len‖bytes`, so no concatenation of a different field split
can produce the same signing bytes: a grant for
(`journal`, `llm.local-gpu`) can never be replayed as one for
(`journal.llm`, `local-gpu`). Verification happens at admission on the
device holding the domain (`OnlyTheHouseholdsHandOpensTheDomain`).
Revocation is deletion + tombstone on the household side; a copy
already carried by a device stays valid until its own expiry — expiry
windows are the real revocation latency, size them accordingly.

## §6 Conformance

Run `./gradlew test`. The five core suites and five wire suites are the
compatibility contract. If you write a new transport, make
`TheAdvertisementSurvivesTheWire` and `AKnockAndItsAnswerSurviveTheWire`
pass against it before anything else.
