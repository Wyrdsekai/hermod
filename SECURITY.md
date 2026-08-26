# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in hermod, please report it responsibly.

**Do NOT open a public issue.**

Email: wyrd@wyrdsekai.org

We will acknowledge receipt within **3 business days** and give you an initial
assessment within **14 days**. If a fix is warranted we aim to release it and
publish an advisory within **90 days** of the report — the usual disclosure
window — and sooner when an issue is being actively exploited. If we need
longer, we will tell you why rather than let the deadline pass in silence.

## Credit

Unless you ask us not to, we will name you in the advisory and the release
notes for the fix. Tell us how you would like to be credited (name, handle,
affiliation, a link); if you say nothing we will use the name you reported
under. We do not run a bug bounty and cannot offer payment — credit and a
straight answer are what we have.

We will not pursue or support legal action against anyone who reports in good
faith, stays within the scope below, avoids privacy violations and service
disruption, and gives us reasonable time to respond before disclosing.

## Scope

hermod moves work between machines and carries consent with it. Security
concerns include:

- **Grant forgery or widening.** A `SignedGrant` that verifies when it should
  not, or that can be re-scoped in transit. Signing bytes are canonical and
  length-prefixed for this reason.
- **Admission bypass.** Anything that causes a device to execute a task it
  should have declined — wrong data domain, expired envelope, missing grant.
- **Transport-level attacks.** Forged or replayed advertisements that poison a
  capability table, or gossip that lets one device impersonate another.
- **Information disclosure.** Task parameters, model names or data-domain
  residency leaking to devices that should not see them.

## Design Principles

- **Deny by default.** No authority key, no grant, an expired grant or a
  re-scoped grant all mean refuse.
- **The executor decides.** Placement is a proposal; the receiving device
  re-checks eligibility itself and may always refuse.
- **Consent is verified at the data**, not at the sender.
- **No presence.** hermod carries work. It is not a channel for who is online,
  and transports must not multiplex that onto its subjects.

## What is not in scope

hermod does not authenticate the transport itself. Securing the NATS
connection — TLS, credentials, subject permissions — belongs to the deployment.
