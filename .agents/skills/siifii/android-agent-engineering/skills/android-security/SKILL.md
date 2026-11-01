---
name: android-security
description: Use when writing or reviewing security-sensitive Android code - Android Keystore, token and secure local storage, sensitive data in logs/screenshots, certificate and network security config, intent/deep-link validation, exported components, WebView security, API credential handling, root/debug-environment awareness. Never hard-code organization secrets or credentials, including as examples.
metadata:
  version: "0.1.0"
---

# Android Security

Defaults for security-sensitive Android code. This skill's examples use
obviously-fake placeholder values only — never paste a real credential,
hostname, or key into this repository (see `SECURITY.md`).

## Secure local storage and the Keystore

- Encryption keys are generated in and never leave the Android Keystore
  (`AndroidKeyStore` provider); never derive/store a raw symmetric key in
  app code, `SharedPreferences`, or a file.
- Tokens/credentials that must persist go in `EncryptedSharedPreferences`
  or a Keystore-backed equivalent, not plain `SharedPreferences`, a plain
  file, or (worst case) a hardcoded constant.
- A short-lived, in-memory-only token (current session token used only
  while the process is alive) doesn't need Keystore-backed persistence at
  all — persisting more than the app actually needs to survive process
  death is itself a smaller-but-real exposure to avoid.
- Biometric-gated access (`BiometricPrompt` tied to a Keystore key with
  `setUserAuthenticationRequired`) for anything the product requires
  step-up authentication for — don't build a custom "ask for biometric,
  then separately read an ungated secret" flow, since that separates the
  gate from the thing it's supposed to protect.

## Logs and screenshots

- Never log a token, password, full PII field (national ID, full card
  number, precise location), or full request/response body of an
  authenticated endpoint, even at debug/verbose level — a debug build
  leaking to a device log, or a verbose log level accidentally shipped,
  is a real exposure path, not a hypothetical.
- Strip or gate verbose/network-body logging behind a build-type check
  (`if (BuildConfig.DEBUG)`) that cannot be flipped on in a release build,
  not a runtime flag that could be enabled in production.
- Use `FLAG_SECURE` on windows/activities showing sensitive content
  (payment details, documents, OTP entry) to prevent screenshots and
  appearance in the recent-apps screenshot/thumbnail.

## Certificate and network security

- Ship a `network_security_config.xml` that pins to expected certificate
  authorities where the threat model calls for certificate pinning
  (typically for the primary API host handling authentication/payment),
  with a documented, tested pin-rotation plan — an expired pin with no
  rotation plan turns into a self-inflicted outage.
- `cleartextTrafficPermitted="false"` by default; a documented,
  narrowly-scoped exception (a specific dev/staging domain, not a
  wildcard) if genuinely needed for non-production builds only.
- Never disable certificate validation (a custom `TrustManager` that
  accepts everything, `HostnameVerifier` returning `true`
  unconditionally) even "temporarily" for debugging — this is a
  Critical-severity finding in any review, debug build or not, because
  such code has a well-documented history of shipping to production by
  accident.

## Intent and deep-link validation

- Validate and sanitize every value extracted from an incoming `Intent`
  (deep link, share target, `onNewIntent`) before using it — treat it as
  untrusted input from outside the app, exactly like a network response.
  A deep link parameter used directly to build a URL to load, a file path
  to open, or a SQL fragment is an injection risk.
- An implicit intent filter (deep link, custom scheme) should use App
  Links (verified `https://` deep links) over a custom URI scheme where
  possible — a custom scheme can be claimed by another installed app,
  enabling intent spoofing/hijacking.
- Check the calling package/signature (`getCallingPackage()`, a signature
  allowlist) before returning sensitive data to a caller via
  `startActivityForResult`/`ActivityResult` API for a component that isn't
  meant to be called by arbitrary third-party apps.

## Exported components

- `android:exported` should be explicit (required by the platform for
  components with intent filters since API 31, but state it deliberately
  everywhere, not just where required) and `true` only for a component
  genuinely meant to be invoked by other apps.
- An exported `ContentProvider`, `Service`, `BroadcastReceiver`, or
  `Activity` that handles sensitive data or performs a sensitive action
  needs its own permission check or signature-level `<permission>`
  declaration — don't rely on obscurity (an undocumented action string)
  as the only protection.
- A `ContentProvider` should restrict its exposed URIs/columns to exactly
  what's needed by legitimate external callers; a provider that exposes
  an entire internal database table because it was convenient is a
  finding.

## WebView security

- `setJavaScriptEnabled(true)` only when the loaded content genuinely
  needs it, and only for content from a trusted, allowlisted origin — not
  for arbitrary user-supplied or deep-link-supplied URLs.
- Avoid `addJavascriptInterface` bridging app code to web content unless
  the loaded page is fully trusted and origin-locked; a bridge exposed to
  an untrusted or externally-navigable page is a direct code-execution
  risk into the app's Java/Kotlin layer.
- Restrict navigation with `shouldOverrideUrlLoading`/a `WebViewClient` to
  an expected domain allowlist rather than letting the WebView follow any
  redirect or link, and disable file-system access
  (`setAllowFileAccess(false)`, `setAllowContentAccess(false)`) unless the
  feature specifically requires it.

## API credentials

- No API key, client secret, or signing credential in source under
  version control — including in `local.properties`-adjacent files that
  get accidentally committed, `BuildConfig` fields populated from a
  hardcoded string, or a "temporary" constant. Load them from a
  non-committed local file, a secrets manager, or CI-injected environment
  variables at build time.
- A client-embedded API key (any key shipped inside the APK) is
  inherently extractable from the binary — treat it as a rate-limiting/
  identification key, never as the sole gate in front of a sensitive
  operation; the sensitive operation's real authorization check belongs
  server-side.
- Rotate a credential that has ever been committed to version control,
  even after removal — removing it from the latest commit doesn't remove
  it from git history.

## Root and debug-environment awareness

- For an app with a real threat model around tampering (payments,
  government ID, DRM-adjacent content), add root/tamper detection as one
  defense-in-depth layer feeding a product decision (warn, restrict a
  specific sensitive flow), not as the only control — root detection is
  bypassable and should never be the sole protection for a sensitive
  operation.
- Ensure debuggable/logging/mock-data code paths are compiled out of
  release builds (`android:debuggable="false"` in the release manifest,
  debug-only source sets) rather than gated only by a runtime flag that a
  reverse-engineer could flip.

## What to check in a review

1. Any hardcoded secret, key, or credential — Critical, always.
2. Any disabled/weakened certificate or hostname validation — Critical,
   always, regardless of build type.
3. Sensitive data (token, PII, full response body) in a log statement.
4. An exported component handling sensitive data/actions with no
   permission or signature check.
5. Unvalidated data from an `Intent`/deep link used in a sensitive
   operation (file path, URL to load, query construction).
6. `FLAG_SECURE` missing on a screen showing payment/document/OTP content.
7. A WebView with JS + `addJavascriptInterface` loading non-allowlisted
   content.

See `references/threat-checklist.md` for the extended checklist.
