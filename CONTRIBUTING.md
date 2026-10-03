# Contributing to hermod

Thank you for your interest in contributing.

## Getting Started

```bash
git clone https://github.com/Wyrdsekai/hermod.git
cd hermod
./gradlew build
```

You need JDK 21 or later. The Gradle wrapper fetches Gradle itself. Running a
node also needs a NATS server.

## Project Structure

- `hermod-core/` — the protocol. **No dependencies**, and the build enforces it.
- `hermod-nats/` — the reference transport: gossip and task RPC over NATS
- `hermod-node/` — `hermod-node`, a compute-only mesh member
- `PROTOCOL.md` — the wire format and the rules

## The zero-dependency core

`hermod-core` must resolve to an empty runtime classpath. `checkNoDependencies`
runs as part of `check` and fails the build otherwise.

This is not tidiness. Platforms that cannot take a JVM dependency vendor the
core as a directory copy, and a dependency slipping in would end that without
anyone noticing. If you are about to add a line to `hermod-core`'s
`dependencies`, the class you are writing probably belongs in `hermod-nats`.

## Changing the protocol

`PROTOCOL.md` and the classes in `org.wyrdsekai.hermod` describe the same
thing. If you change one, change the other in the same commit — a protocol
document that has drifted from the code is worse than none, because people
trust it.

Wire-format changes need a version bump and a note in the changelog. Adding an
optional field is usually compatible; changing the meaning of an existing one
is not.

## Tests

```bash
./gradlew test
```

Tests are named for the behaviour they pin, not the method they call —
`ARefusedDoorSendsTheErrandOnwardTest` rather than `RouterTest`. A test whose
name does not say what breaks if it fails is hard to act on when it goes red
two years from now.

New behaviour needs a test. A bug fix needs a test that fails before the fix.

## Pull Requests

- One change per PR
- Tests for new behaviour
- `./gradlew build` green, which includes the zero-dependency check
- Update `PROTOCOL.md` if the wire format or the rules changed

## Code Style

Standard Java conventions. Prefer records for data, interfaces for seams, and
comments that explain why rather than what.

## Releasing

Publishing goes from the **public tree**, not this one — what ships should be
built from the source people can read. The build refuses otherwise.

```bash
./scripts/export-oss.sh            # then commit and push ../hermod-oss
cd ../hermod-oss
./gradlew centralBundle            # signs, stages, zips -> build/central/hermod-<version>.zip
```

Upload the bundle to the Central Publisher Portal. `TOKEN` is the base64 of
`username:password` from the Portal's token page:

```bash
curl -sS --fail-with-body \
  -H "Authorization: Bearer $TOKEN" \
  -F bundle=@build/central/hermod-0.2.0.zip \
  "https://central.sonatype.com/api/v1/publisher/upload?name=hermod-0.2.0"
```

It returns a deployment id. The default is `USER_MANAGED`, so the deployment
waits in the Portal for you to review and publish it — add
`&publishingType=AUTOMATIC` only when you want it to go out on validation alone.

Signing uses the gpg agent. If `GPG_TTY` is not set, gpg has nowhere to draw
its prompt and the build fails with `Inappropriate ioctl for device` — which
reads like a broken key but is only a missing environment variable:

```bash
export GPG_TTY=$(tty)
echo test | gpg --clearsign > /dev/null    # cache the passphrase first
./gradlew centralBundle
```

The agent caches for 10 minutes by default. For a longer run, set
`default-cache-ttl 3600` in `~/.gnupg/gpg-agent.conf` and
`gpgconf --reload gpg-agent`.
