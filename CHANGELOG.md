# Changelog

## 0.2.0 (2026-10-03)

Protocol version 2: a door knows who sent an envelope.

### Changed
- **Envelopes are signed by the sending device.** `EnvelopeSigning` defines
  the bytes (every stable field, length-prefixed, params in key order) and
  signs them with the device's Ed25519 key. In 0.1.0 the signature field was
  carried but never defined or checked.
- **Advertisements carry the device's public key** (`publicKey`, X.509 SPKI).
  The table can look a device up by id (`CapabilityTable.find`).
- **The door verifies the sender before anything else.** `LocalAdmissionGate`
  built with an origin lookup refuses an unknown device, a device that
  advertises no key, a bad signature, or an envelope naming another scope.
  Then expiry, budget and the grant as before.
- **A grant is checked against the sender:** it must be for the envelope's
  scope and for the class of the device that sent the task.
- `HermodService` advertises its key and both of its doors verify;
  `HermodProbe` advertises an ephemeral key and signs with it.

### Compatibility
- An old advertisement decodes (no key). A 0.1.0 device's envelopes are
  refused by a 0.2.0 door with "advertises no key"; a 0.1.0 door still admits
  a 0.2.0 device's envelopes, since it never looked. Update the devices that
  send tasks first.
- The four-argument `LocalAdmissionGate` constructor remains, for tests of the
  other checks: it does not verify origins (`verifiesOrigin()` is false).

## 0.1.0 (2026-08-26)

Initial release.

### Features
- **Capability gossip**: devices advertise their class, load, and resident data
  domains; last-write-wins, with entries expiring by TTL. No registry.
- **Local routing**: each device routes from its own view — capability class,
  then data residency, then idle before charging before least-loaded.
- **Executor-owned admission**: placement is a proposal; the receiving device
  re-checks and may decline. Refusal drives concurrency instead of a queue.
- **Consent that travels**: a `SignedGrant` rides in the envelope and is
  verified where the data lives, with canonical length-prefixed signing bytes
  so a grant cannot be widened in transit. Deny by default.
- **Single-offer door protocol**: one request carries the envelope, one reply
  carries admission and result. A dead door times out into a decline.
- **NATS reference transport**: gossip and task RPC, with heartbeats that
  report real OS load.
- **`hermod-node`**: a compute-only mesh member that runs `inference.chat`
  against any OpenAI-compatible endpoint.
- **Zero-dependency core**: `hermod-core` resolves to an empty runtime
  classpath, enforced by the build, so it can be vendored as a directory copy.
