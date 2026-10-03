# hermod

A compute mesh for machines you already own. Devices advertise what they can
run, route their own requests, and execute work for each other. No central
scheduler, no cloud.

It is used as the compute fabric in [wyrdsekai](https://wyrdsekai.org), and
works on its own.

## Run a node

```bash
./gradlew installDist
hermod-node/build/install/hermod-node/bin/hermod-node \
    nats://127.0.0.1:4222 home llm.local-gpu http://127.0.0.1:8200 default
```

Arguments are: the NATS URL, the mesh scope, this node's capability class, an
OpenAI-compatible endpoint to run work against, and a model name.

The node advertises itself, accepts or refuses incoming tasks, and runs the
ones it accepts against its local endpoint. It leaves the mesh by TTL when you
stop it. Identity is created on first run at `~/.hermod/node-identity.json`.

## Use it as a library

```java
// A capability table, kept current by the gossip transport. Entries
// older than the TTL are dropped, which is how a node leaves the mesh.
var table = new CapabilityTable(Duration.ofMinutes(2));
var doors = new NatsDoors(nats, scope);

// A router over the local view, and a way to reach each peer.
var mesh = new Mesh(new DefaultRouter(table, Clock.systemUTC()),
                    (envelope, cap) -> doors.doorTo(cap.deviceId()));

var envelope = new TaskEnvelope(
    "job-1", scope, deviceId,
    "inference.chat",      // task type
    "none",                // data domain; anything else needs a grant
    "llm.local-gpu",       // capability class to match
    Map.of("model", "default", "prompt", "hello"),
    256,                   // token budget
    Instant.now(), Instant.now().plusSeconds(180),
    Optional.empty(),      // SignedGrant, when the data domain needs one
    new byte[]{1});

var result = mesh.submit(envelope);
```

`submit` walks the candidates the router returns and gives back the first
accepted result. If every candidate declines, it tells you that.

## What it does

| Step | What happens |
|---|---|
| Advertise | Each device publishes a `Capability`: its class, current load, which data domains it holds, and its public key. Spread by gossip; there is no registry. |
| Route | The sender picks a target from its own local table: capability class first, then data residency, then idle before charging before least-loaded. |
| Admit | The receiving device decides for itself. It first verifies the envelope's signature against the sender's advertised key, then re-checks eligibility, task type, data residency and expiry, and may refuse. A refusal is normal, not an error. |
| Execute | The accepting device runs the task against its own endpoint and returns the result. |

If a target refuses, the sender tries the next candidate. That is how
concurrency is handled: there is no queue and no lock.

## Consent

A task carries a `SignedGrant`, and the device holding the data verifies it
before running anything. Signing bytes are canonical and length-prefixed, so a
grant cannot be widened in transit. Deny by default: no authority key, no
grant, an expired grant, or a re-scoped grant all mean refuse.

## Architecture

```
Device A                                  Device B
  Capability ──gossip──────────────────▶  CapabilityTable
  Router (local view)
    │  TaskEnvelope + SignedGrant
    └──────────────────────────────────▶  AdmissionGate
                                            │ accept / decline
                                            ▼
                                          execute → Result
```

Three modules:

| Module | Contents | Dependencies |
|---|---|---|
| `hermod-core` | The protocol: `Capability`, `CapabilityTable`, `DefaultRouter`, `Mesh`, `TaskEnvelope`, `AdmissionGate`, `SignedGrant`, `GrantAuthority` | **none** |
| `hermod-nats` | Reference transport: gossip and task RPC over NATS, `HermodService`, a bench probe | `hermod-core`, jnats, jackson, slf4j |
| `hermod-node` | `hermod-node`, a compute-only member that runs `inference.chat` against any OpenAI-compatible endpoint | the above |

`hermod-core` has no dependencies, and the build fails if that stops being
true — `checkNoDependencies` resolves its runtime classpath and refuses a
non-empty one. That matters because platforms which cannot take a JVM
dependency vendor the core as a directory copy, and a dependency slipping in
would end that quietly.

```kotlin
dependencies {
    implementation("org.wyrdsekai:hermod-core:0.2.0")   // protocol only
    implementation("org.wyrdsekai:hermod-nats:0.2.0")   // add the transport
}
```

[PROTOCOL.md](PROTOCOL.md) describes the wire format and the rules in detail.

## What hermod is not

- Not a job queue. Pending work is not persisted. If a peer goes away you get
  a `Declined`, not a lost task.
- Not a scheduler. No device has a global view, by design.
- Not a data plane. Tasks carry small parameters. Models and files move by
  other means.
- Not a presence system. It carries work, not information about who is online.

## Requirements

JDK 21 or later, and a NATS server for the reference transport.

## License

Apache-2.0. See [LICENSE](LICENSE).
