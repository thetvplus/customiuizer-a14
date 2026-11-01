# Extended Android security review checklist

Load this for a dedicated security review pass; `SKILL.md`'s "What to
check" section is enough for routine review.

## Data at rest

- [ ] No secret in `SharedPreferences` (plain), a plain file, a hardcoded
      constant, or a Room column without field-level encryption for
      genuinely sensitive columns.
- [ ] Keystore-backed key generation, never a derived/stored raw key.
- [ ] Only what the product actually needs persisted is persisted — no
      "just in case" caching of a full sensitive payload.
- [ ] Backups: sensitive data either excluded from Auto Backup
      (`android:allowBackup="false"` or a backup rules file excluding
      specific paths) or backed by an encrypted, key-derived scheme that
      survives restore-to-a-different-device correctly.

## Data in transit

- [ ] TLS only; `cleartextTrafficPermitted` false by default.
- [ ] Certificate pinning present and has a documented rotation plan, for
      any host in the actual threat model.
- [ ] No custom `TrustManager`/`HostnameVerifier` that weakens validation,
      in any build type.
- [ ] No sensitive query parameter or path segment logged by an HTTP
      logging interceptor at a level shippable to production.

## Components

- [ ] Every component's `exported` state is explicit and justified.
- [ ] Exported components handling sensitive actions have a permission or
      signature check.
- [ ] `ContentProvider` URI/column exposure is scoped to what's needed.
- [ ] Deep-link/intent-extracted values are validated before use in a
      file path, URL, or query.

## WebView

- [ ] JS enabled only for allowlisted, trusted content.
- [ ] No `addJavascriptInterface` bridge exposed to untrusted/externally
      navigable content.
- [ ] Navigation restricted to an expected domain allowlist.
- [ ] File/content access disabled unless specifically required.

## Credentials and secrets

- [ ] No credential in version control, including build config generated
      from a hardcoded string.
- [ ] Client-embedded keys are treated as extractable, not as a sole
      authorization gate.
- [ ] CI secrets are injected at build time from a secrets manager/CI
      environment, not committed.

## Logging and screenshots

- [ ] No token/PII/full authenticated response body logged at any level
      reachable in a release build.
- [ ] `FLAG_SECURE` present on payment/document/OTP screens.
- [ ] Crash/analytics reporting is checked for accidental PII capture in
      breadcrumbs or custom event properties.

## Root/tamper and debug builds

- [ ] Debug-only code paths compiled out of release, not just
      runtime-gated.
- [ ] `debuggable="false"` in the release manifest.
- [ ] Root/tamper detection (if present) feeds a product decision, isn't
      the sole protection for a sensitive operation.
