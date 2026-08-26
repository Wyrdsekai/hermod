# Changelog

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
