# Android PRF Provider Contract

> Issue: [nuri-com/bitwarden-passkey-prf#11](https://github.com/nuri-com/bitwarden-passkey-prf/issues/11)
> Milestone: M0 — Contract & Harness
> Repository: `nuri-com/android` (Bitwarden Android fork)
> Locks the observable behavior the Android Credential Manager provider path must preserve so the shared PRF/HMAC evaluator (WP2) and the Android recovery flow (WP4) can be built against a fixed shape.

This contract is frozen for milestone M0. Behavioral changes require an update to this document and a new coordination issue. The contract intentionally does not specify internal storage, KDF, or authenticator implementation details; those are owned by `nuri-com/sdk-internal` and the shared evaluator contract. The Android provider must only transport bytes and preserve extension JSON without semantic transformation.

A companion document, `android-credential-manager-prf-provider.md`, exists in this directory with the same scope; the two are kept in sync. If they diverge, this file is authoritative for the filename referenced by issue #11.

## 1. Scope and non-goals

In scope:

- The AndroidX Credential Manager request and response shapes used by Bitwarden as a Credential Provider, for both attestation (creation) and assertion (authentication) ceremonies.
- The PRF (`prf`) and `hmac-secret` extension surface that travels inside those requests and responses.
- The constrained (`allowCredentials` populated) and discoverable (no credential ID hint) request variants.
- User verification (UV) semantics as observed by the provider.
- The version pins for AndroidX credentials, provider-events, Play Services provider-events backend, compile/target/min SDK, and the Bitwarden SDK artifact.

Out of scope:

- The internal FIDO2 authenticator, HMAC seed storage, and PRF evaluation algorithm. Those live in `nuri-com/sdk-internal` and are locked by the shared PRF evaluator contract, not here.
- iOS Credential Exchange import. Locked by the iOS contract.
- Browser extension, desktop, and non-Android Credential Manager (autofill-only) surfaces.
- UI, telemetry, and provider selection ranking.

## 2. Version pins

All pins are read from `gradle/libs.versions.toml` at the base SHA of this contract. Any upgrade is a contract-affecting change and must be reflected here.

### 2.1 Platform

| Property | Pinned value | Source |
| --- | --- | --- |
| `compileSdk` | `37` | `libs.versions.compileSdk` |
| `targetSdk` | `37` | `libs.versions.targetSdk` |
| `minSdk` | `29` | `libs.versions.minSdk` |
| `jvmTarget` | `21` | `libs.versions.jvmTarget` |
| Android Gradle Plugin | `9.2.1` | `libs.versions.androidGradlePlugin` |
| Kotlin | `2.4.0` | `libs.versions.kotlin` |
| `appVersionName` | `2026.6.0` | `libs.versions.appVersionName` |

`BitwardenCredentialProviderService` is gated with `@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)` (API 34, Android 14). The provider path is only reachable on Android 14+. The `minSdk = 29` floor covers the rest of the app; the provider service itself is platform-gated and the system will not bind to it below API 34.

### 2.2 AndroidX credentials

| Coordinate | Pinned version | Catalog alias | Used by |
| --- | --- | --- | --- |
| `androidx.credentials:credentials` | `1.6.0` | `libs.androidx.credentials` | `app`, `ui`, `testharness`, `cxf` |
| `androidx.credentials.providerevents:providerevents` | `1.0.0-alpha06` | `libs.androidx.credentials.providerevents` | `app`, `cxf` |
| `androidx.credentials.providerevents:providerevents-play-services` | `1.0.0-alpha06` | `libs.androidx.credentials.providerevents.play.services` | `standard` flavor of `cxf` and `app` |

The Play Services provider-events backend is a closed-source artifact and is excluded from the `fdroid` flavor. The `fdroid` flavor supplies no-op stubs. Any PRF behavior must be invariant to flavor: the provider-events backend only routes begin/get/create callbacks, it must not reinterpret extension JSON.

The catalog comment on `androidx-credentials` reads:

> `noinspection CredentialDependency - Used for Passkey support, which is not available below Android 14`

This pin is the load-bearing version for both the import (CXF) flow and the provider (assertion/attestation) flow. Bumping `androidx.credentials` without re-locking this contract is a blocker for the conformance fixtures (issue #35) and for the WP4 Android recovery proof.

### 2.3 Bitwarden SDK

| Coordinate | Pinned version | Catalog alias |
| --- | --- | --- |
| `com.bitwarden:sdk-android` | `3.0.0-7799-d2bd7a3e` | `libs.bitwarden.sdk` |

The SDK exports `com.bitwarden.fido.ClientExtensionResults`, `com.bitwarden.fido.PublicKeyCredentialAuthenticatorAssertionResponse`, `com.bitwarden.fido.PublicKeyCredentialAuthenticatorAttestationResponse`, `com.bitwarden.fido.Fido2CredentialAutofillView`, `com.bitwarden.fido.Origin`, `com.bitwarden.fido.ClientData`, `com.bitwarden.fido.UnverifiedAssetLink`, and `com.bitwarden.sdk.Fido2CredentialStore`. Any change to the SDK's `ClientExtensionResults` shape (for example adding a `prf` field) is a contract change and is owned by `nuri-com/sdk-internal`, not by this Android contract. The Android provider only forwards what the SDK returns.

### 2.4 Manifest and provider capability declaration

Provider registration lives in `app/src/main/AndroidManifest.xml`:

- Service `com.x8bit.bitwarden.Autofill.CredentialProviderService` (the name is a legacy identifier and must not be renamed) is exported, guarded by `android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE`, and filters `android.service.credentials.CredentialProviderService`.
- Meta-data `android.credentials.provider` points to `app/src/main/res/xml/provider.xml`, which declares capabilities:
  - `android.credentials.TYPE_PASSWORD_CREDENTIAL`
  - `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL`

The passkey capability is what routes PRF-bearing `GetPublicKeyCredentialOption` requests to Bitwarden. Removing or renaming either capability is a contract change.

### 2.5 CXF (Credential Exchange) module

The `cxf` module declares the same AndroidX credentials pins (see `cxf/build.gradle.kts`) plus a flavor split for the Play Services backend. It supports CXF export file `version.major` in `{0, 1}` with `version.minor = 0`. The CXF payload parser does not currently model `fido2Extensions` at the Kotlin layer; it passes account items through as an opaque `JsonArray`. This contract does not lock the CXF extension shape; that is owned by the shared encrypted credential model contract in WP1.

## 3. Provider request and response shapes

The Android Credential Manager flow is split into two phases:

1. **Begin phase** — the system calls `CredentialProviderService.onBeginGetCredentialRequest` / `onBeginCreateCredentialRequest`. The provider returns a `BeginGetCredentialResponse` / `BeginCreateCredentialResponse` containing `CredentialEntry` / `CreateEntry` candidates. No assertion or attestation is performed here. No PRF output is produced here.
2. **Provider phase** — the user picks an entry. The system delivers a `ProviderGetCredentialRequest` / `ProviderCreateCredentialRequest` to the provider `Activity` (`CredentialProviderActivity`). The provider invokes the Bitwarden SDK to perform the actual assertion or attestation and returns a JSON response.

PRF evaluation happens only in the provider phase, inside the SDK assertion call. The begin phase must not evaluate PRF and must not surface PRF inputs in any entry metadata.

### 3.1 Assertion (get) request — constrained

A constrained request carries `allowCredentials` with one or more credential descriptors. This is the shape Nuri uses when it already knows the credential ID, for example after a prior session.

The system delivers a `BeginGetCredentialRequest` to `BitwardenCredentialProviderService.onBeginGetCredentialRequest`. The request contains one or more `BeginGetPublicKeyCredentialOption`. Each option's `requestJson` is a WebAuthn `publicKeyCredentialRequestOptions` JSON object (not wrapped in `publicKey`).

Parsed by Bitwarden as `PasskeyAssertionOptions` (`app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/PasskeyAssertionOptions.kt`):

```kotlin
@Serializable
data class PasskeyAssertionOptions(
    @SerialName("challenge")         val challenge: String,
    @SerialName("allowCredentials")  val allowCredentials: List<PublicKeyCredentialDescriptor>?,
    @SerialName("rpId")              val relyingPartyId: String?,
    @SerialName("userVerification")  val userVerification: UserVerificationRequirement =
        UserVerificationRequirement.PREFERRED,
)
```

`PublicKeyCredentialDescriptor` (`PublicKeyCredentialDescriptor.kt`):

```kotlin
@Serializable
data class PublicKeyCredentialDescriptor(
    @SerialName("type")       val type: String,
    @SerialName("id")         val id: String,
    @SerialName("transports") val transports: List<String>?,
)
```

Constrained shape, required fields for PRF:

- `rpId`: must equal `nuri.com` for the Nuri wallet recovery case.
- `challenge`: base64url-encoded WebAuthn challenge.
- `allowCredentials`: non-null, non-empty. Each `id` is the base64url-encoded credential ID. Bitwarden filters discovered credentials against these IDs (`BitwardenCredentialManagerImpl.filterAllowedCredentialsIfNecessary`).
- `userVerification`: see §5.
- `extensions`: optional. When present, the PRF input travels under `extensions.prf`. See §4. The current `PasskeyAssertionOptions` model does not deserialize `extensions`; PRF support requires extending this model (or parsing the raw JSON separately) without breaking existing clients.

#### Constrained request JSON example

```json
{
  "challenge": "<base64url challenge>",
  "rpId": "nuri.com",
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "<base64url credential id>",
      "transports": ["internal"]
    }
  ],
  "userVerification": "required",
  "extensions": {
    "prf": {
      "eval": {
        "first": "bnVyaS1wcmYtc2FsdC12MQ"
      }
    }
  }
}
```

### 3.2 Assertion (get) request — discoverable

A discoverable (resident-key) request omits `allowCredentials` (or sends it as an empty list). Nuri uses this on a fresh install where no credential ID hint exists. Bitwarden must still select a credential for the given `rpId` and bind the returned assertion to the selected credential.

Discoverable shape:

- `rpId`: required, must equal `nuri.com` for the Nuri case.
- `challenge`: required.
- `allowCredentials`: null or empty. Bitwarden's `filterAllowedCredentialsIfNecessary` treats an empty allowed-credentials list as "no filtering", returning all credentials discovered for the RP.
- `userVerification`: see §5.
- `extensions`: optional, same PRF surface as the constrained case.

The provider must not silently fall back to a non-PRF assertion when `allowCredentials` is absent. The presence of `extensions.prf` in the request drives PRF evaluation, not the presence of `allowCredentials`.

#### Discoverable request JSON example

```json
{
  "challenge": "<base64url challenge>",
  "rpId": "nuri.com",
  "userVerification": "required",
  "extensions": {
    "prf": {
      "eval": {
        "first": "bnVyaS1wcmYtc2FsdC12MQ"
      }
    }
  }
}
```

### 3.3 Assertion (get) response

The SDK returns a `PublicKeyCredentialAuthenticatorAssertionResponse`. The Android layer converts it to `Fido2PublicKeyCredential` (`Fido2PublicKeyCredential.kt`) and serializes it to JSON. This JSON is the WebAuthn `PublicKeyCredential` object returned through Credential Manager.

Current response shape:

```kotlin
@Serializable
data class Fido2PublicKeyCredential(
    @SerialName("id")                     val id: String,
    @SerialName("rawId")                   val rawId: String,
    @SerialName("type")                    val type: String,
    @SerialName("authenticatorAttachment") val authenticatorAttachment: String?,
    @SerialName("response")               val response: Fido2AssertionResponse,
    @SerialName("clientExtensionResults")  val clientExtensionResults: ClientExtensionResults,
) {
    @Serializable
    data class Fido2AssertionResponse(
        @SerialName("clientDataJSON")    val clientDataJson: String?,
        @SerialName("authenticatorData") val authenticatorData: String,
        @SerialName("signature")         val signature: String,
        @SerialName("userHandle")        val userHandle: String?,
    )

    @Serializable
    data class ClientExtensionResults(
        @SerialName("credProps") val credentialProperties: CredentialProperties?,
    ) {
        @Serializable
        data class CredentialProperties(@SerialName("rk") val residentKey: Boolean?)
    }
}
```

The conversion is done in `PublicKeyCredentialAuthenticatorAssertionResponseExtensions.kt`. Today it only surfaces `credProps` (resident key). The `prf` extension results are not propagated. Locking the contract means: when PRF is evaluated by the SDK, the Android layer must surface `clientExtensionResults.prf` with the same shape the SDK produces, with no additional transformation, renaming, or encoding conversion.

Required PRF-bearing response shape (target):

```json
{
  "id": "<base64url credential id>",
  "rawId": "<base64url credential id>",
  "type": "public-key",
  "authenticatorAttachment": "platform",
  "response": {
    "clientDataJSON": "<base64url>",
    "authenticatorData": "<base64url>",
    "signature": "<base64url>",
    "userHandle": "<base64url user handle>"
  },
  "clientExtensionResults": {
    "credProps": { "rk": true },
    "prf": {
      "results": {
        "first": "<base64url 32-byte PRF output for first input>",
        "second": "<base64url 32-byte PRF output for second input, optional>"
      }
    }
  }
}
```

Encoding rules (already enforced by the existing extension functions):

- All `ByteArray` fields are base64url-encoded with `Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING`.
- No trailing `=` padding, no newlines.
- The provider must not re-encode, hex-encode, or otherwise transform PRF outputs. The 32-byte PRF result for the raw `nuri-prf-salt-v1` input must reach Nuri byte-for-byte as the SDK produced it.

### 3.4 Attestation (create) request and response

Attestation is out of scope for the Nuri MVP recovery proof (WP4 uses assertion only). The attestation request is parsed by `PasskeyAttestationOptions` and the response is converted by `PublicKeyCredentialAuthenticatorAttestationResponseExtensions.kt` to `Fido2AttestationResponse`. The attestation path currently does not model `prf` inputs either. Any future Bitwarden-hosted passkey creation that wants portable PRF must extend `PasskeyAttestationOptions` with `extensions.prf` and the attestation response with `clientExtensionResults.prf`. That work is parked behind WP2's creation behavior and is not required for the M0 contract.

## 4. PRF extension semantics

PRF (the WebAuthn `prf` extension) maps to the CTAP 2.1 `hmac-secret` extension. The provider never speaks CTAP directly; it transports the WebAuthn-level JSON. The semantics below are the contract the Android provider must preserve.

### 4.1 Request-side `extensions.prf`

The WebAuthn `prf` extension input supports two top-level fields:

- `prf.eval`: default inputs for PRF evaluation.
- `prf.evalByCredential`: per-credential inputs, keyed by credential ID (base64url). A matching entry takes precedence over `eval`; `eval` is the fallback when no entry matches.

Inside either shape:

- `first`: a base64url-encoded opaque byte string of any length. Required. Drives `prf.results.first`.
- `second`: a base64url-encoded opaque byte string of any length. Optional. When present, the provider must also return `prf.results.second`.

Per [WebAuthn Level 3 PRF client-extension processing](https://www.w3.org/TR/webauthn-3/#prf-extension), the raw client input is not the authenticator salt. For each `first` or `second` value, the WebAuthn client-processing layer derives the fixed 32-byte authenticator salt as `SHA-256(UTF8Encode("WebAuthn PRF") || 0x00 || input)`. The PRF result is also 32 bytes. These are three distinct values: arbitrary-length raw input, 32-byte domain-separated authenticator salt, and 32-byte PRF output.

For Nuri the raw input is fixed: `first = UTF8("nuri-prf-salt-v1")`, exactly 16 bytes, base64url `bnVyaS1wcmYtc2FsdC12MQ`, with no `second`. See `docs/nuri-prf-contract.md` in the coordination repo. Nuri does not currently use `evalByCredential`; it uses `eval.first` with a discoverable or constrained request.

The Android provider must:

- Preserve the `extensions.prf` JSON from `GetPublicKeyCredentialOption.requestJson` and hand it to the SDK through the request JSON envelope (`{"publicKey": <requestJson>}`). The current code in `BitwardenCredentialManagerImpl.authenticateFido2Credential` already wraps the request this way; PRF inputs inside `extensions.prf` must survive that wrap unchanged.
- Not strip, rename, re-encode, or pre-hash the raw PRF inputs. The shared evaluator performs the WebAuthn domain-separation mapping after receiving the unchanged request JSON; the Android provider must not mutate or drop that JSON.
- Not surface raw PRF inputs to the begin-phase entry list. `BeginGetPublicKeyCredentialOption.requestJson` is reachable at begin time but PRF must not be evaluated or exposed there.

### 4.2 Response-side `clientExtensionResults.prf`

The SDK returns `ClientExtensionResults` with the evaluated PRF outputs. The Android provider must serialize them under `clientExtensionResults.prf.results`:

```json
"clientExtensionResults": {
  "prf": {
    "results": {
      "first":  "<base64url 32-byte output>",
      "second": "<base64url 32-byte output, present iff request had second>"
    }
  }
}
```

Rules:

- `first` is always present when the request carried `prf.eval.first` (or an equivalent `evalByCredential` entry) and UV was satisfied. A missing `first` in that case is a hard failure, not a silent omission.
- `second` is present iff the request supplied `second`. When the request had no `second`, the response must omit `second` (not emit it as null or empty).
- When the credential has no PRF/HMAC seed state, the provider must fail closed. It must not return a `prf` object with empty results, and must not return a `prf` object with zeros. The failure surfaces as a `GetCredentialUnknownException` from `BitwardenCredentialManagerImpl.authenticateFido2Credential` or as an SDK error propagated through `Fido2CredentialAssertionResult.Error`.
- When the request did not include `prf`, the response must not include `prf` in `clientExtensionResults`. The existing `credProps` behavior is unaffected.

### 4.3 `eval`, `evalByCredential`, `first`, `second` semantics summary

| Field | Request side | Response side | Required? | Notes |
| --- | --- | --- | --- | --- |
| `prf.eval` | Object with `first`, optional `second` | — | One of `eval`/`evalByCredential` required to enable PRF | Default input shape |
| `prf.evalByCredential` | Map of credentialId → `{first, second?}` | — | One of `eval`/`evalByCredential` required | Per-credential inputs; provider selects the entry for the asserted credential |
| `prf.eval.first` / `evalByCredential[*].first` | base64url opaque input of any length | — | Required | Domain-separated and SHA-256 mapped by WebAuthn; drives `results.first` |
| `prf.eval.second` / `evalByCredential[*].second` | base64url opaque input of any length | — | Optional | Domain-separated and SHA-256 mapped by WebAuthn; drives `results.second` |
| `prf.results.first` | — | base64url 32-byte output | Required when PRF requested and effective UV succeeds | Never cached, never persisted |
| `prf.results.second` | — | base64url 32-byte output | Required iff request had `second` | Same lifetime as `first` |

## 5. User verification (UV) semantics

`UserVerificationRequirement` (`UserVerificationRequirement.kt`) is a three-valued enum:

- `DISCOURAGED` (`"discouraged"`) — UV should not be performed.
- `PREFERRED` (`"preferred"`) — UV is preferred if supported. This is the default in `PasskeyAssertionOptions` and in `PasskeyAttestationOptions.AuthenticatorSelectionCriteria`.
- `REQUIRED` (`"required"`) — UV is required; the ceremony must fail if UV cannot be performed.

For the Nuri wallet recovery case UV is **required** (see `docs/nuri-prf-contract.md`: "User verification: Required for wallet unlock"). The provider must enforce:

1. **Pre-verification flag**. `CredentialProviderViewModel` sets `bitwardenCredentialManager.isUserVerified = request.isUserPreVerified` from `Fido2CredentialAssertionRequest.isUserPreVerified`. The OS biometric prompt that the system shows when the user taps a credential entry is the source of this flag. The SDK receives `isUserVerificationSupported = true` unconditionally in `BitwardenCredentialManagerImpl.authenticateFido2Credential`. UV state is a gate, not a hint.
2. **Effective UV implies fail-closed**. A PRF request makes UV effective even when the request says `preferred` or `discouraged`. If the user is not verified (either the pre-verified flag is false or the in-app biometric prompt is cancelled), the provider must not evaluate PRF and must not return an assertion for that PRF request. The current `MAX_AUTHENTICATION_ATTEMPTS = 5` cap in `BitwardenCredentialManagerImpl` bounds retries; exceeding it must surface a hard error, not a fallback to non-UV behavior.
3. **WebAuthn PRF uses only the UV HMAC function**. CTAP `hmac-secret` has UV and non-UV functions, but WebAuthn exposes only one PRF and requires the user-verified function. The shared evaluator must therefore select `credentialWithUV`; `credentialWithoutUV` must never produce `AuthenticationExtensionsPRFOutputs.results`. The Android provider does not select the function directly; it propagates the UV state needed for the SDK to enforce this rule.
4. **Discoverable + UV required**. The discoverable case must not weaken UV. A discoverable request with `userVerification == REQUIRED` must still enforce UV before any credential is surfaced for assertion. The begin phase may return `AuthenticationAction` (unlock prompt) when the vault is locked (`CredentialProviderProcessorImpl.processGetCredentialRequest`), but that is a vault-unlock gate, not a UV gate. The UV gate is enforced in the provider phase.
5. **Preferred or discouraged PRF requests**. WebAuthn PRF overrides the requested preference when necessary: the provider must make UV effective or fail closed. It must never satisfy a PRF request with the non-UV HMAC function.

A CXF-imported passkey carries both `credentialWithUV` and `credentialWithoutUV` as portable credential state, and import must preserve both. Preservation is not WebAuthn selection: `credentialWithoutUV` remains available for CXF portability or direct CTAP use, but it must never become a WebAuthn `AuthenticationExtensionsPRFOutputs.results` value.

## 6. Constrained vs discoverable — provider behavior matrix

| Case | `allowCredentials` | `rpId` | `extensions.prf` | `userVerification` | Provider behavior |
| --- | --- | --- | --- | --- | --- |
| Nuri recovery, known credential ID | non-empty | `nuri.com` | `eval.first = "nuri-prf-salt-v1"` | `required` | Filter discovered credentials to the allowed IDs, UV-gate, evaluate PRF, return `prf.results.first`. |
| Nuri recovery, fresh install | null/empty | `nuri.com` | `eval.first = "nuri-prf-salt-v1"` | `required` | Discover all credentials for `rpId`, UV-gate, evaluate PRF for the selected credential, return `prf.results.first`. Bind the assertion to the selected credential. |
| PRF requested, preference is not required | any | any | present | `preferred`/`discouraged` | Make UV effective and evaluate only the UV PRF function, or fail closed. Never fall back to the non-UV function. |
| PRF requested, UV failed | any | any | present | `required` | Fail closed. Do not evaluate PRF. Surface `GetCredentialUnknownException` or SDK error. |
| No PRF in request | any | any | absent | any | Existing behavior. Do not add `prf` to `clientExtensionResults`. |
| Credential with no HMAC seed state | any | any | present | any | Fail closed. Do not synthesize a PRF output. |

## 7. Transport invariants

These invariants apply to both constrained and discoverable paths and to both the begin and provider phases.

1. **No PRF at begin time.** `BeginGetCredentialResponse` contains `CredentialEntry` list and optional `AuthenticationAction`. It must never contain PRF inputs, PRF outputs, or HMAC seeds. Entry metadata is visible to the system UI; PRF salts are not.
2. **Request JSON is opaque to the provider.** The provider must not parse, rewrite, or re-encode `GetPublicKeyCredentialOption.requestJson` beyond the existing `{"publicKey": ...}` wrap for the SDK call. Extension JSON travels verbatim.
3. **Response JSON is opaque to the provider.** The provider must not rewrite `clientExtensionResults` beyond what the SDK returned. Adding, removing, or renaming fields is a contract violation.
4. **Base64url is the only encoding.** All byte fields in the request and response use base64url with no padding. The existing `base64EncodeForFido2Response` helper is the canonical encoder. Hex, standard base64, and raw bytes are all invalid.
5. **No persistence of PRF outputs.** The provider must not write `prf.results.first`, `prf.results.second`, or any derived value to disk, logs, analytics, crash reports, or the vault. PRF outputs are computed on demand and discarded after the response is returned.
6. **No persistence of HMAC seeds.** The Android provider does not touch HMAC seeds directly; they live in the encrypted FIDO credential owned by the SDK. The provider must not extract, log, or forward them.
7. **Fail-closed default.** Any unrecognized PRF shape, missing seed, UV failure, or encoding error must surface as a hard error. It must not produce a partial PRF result, a zero PRF result, or a PRF-less assertion that silently downgrades Nuri's wallet root.

## 8. Source references

Load-bearing files in `nuri-com/android`:

- `gradle/libs.versions.toml` — all version pins.
- `app/src/main/AndroidManifest.xml` — provider service registration and capability declaration.
- `app/src/main/res/xml/provider.xml` — capability list.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/BitwardenCredentialProviderService.kt` — `CredentialProviderService` subclass, API 34 gate.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/processor/CredentialProviderProcessorImpl.kt` — begin-phase routing, vault-locked unlock action.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/manager/BitwardenCredentialManagerImpl.kt` — provider-phase assertion/attestation, UV flag plumbing, allowed-credentials filter, silent discovery.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/manager/BitwardenCredentialManager.kt` — `getUserVerificationRequirement` surface.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/PasskeyAssertionOptions.kt` — assertion request model (does not yet deserialize `extensions`).
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/PasskeyAttestationOptions.kt` — attestation request model (does not yet deserialize `extensions`).
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/UserVerificationRequirement.kt` — UV enum.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/PublicKeyCredentialDescriptor.kt` — `allowCredentials` element shape.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/Fido2CredentialAssertionRequest.kt` — provider-phase assertion request wrapper, carries `isUserPreVerified`.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/Fido2PublicKeyCredential.kt` — assertion response model; `clientExtensionResults` currently only has `credProps`.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/Fido2AttestationResponse.kt` — attestation response model; same `credProps`-only limitation.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/GetCredentialsRequest.kt` — begin-phase request wrapper, parses `BeginGetCredentialRequest` from a bundle.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/model/CreateCredentialRequest.kt` — create-phase request wrapper, carries `isUserPreVerified`.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/credentials/util/CredentialProviderIntentUtils.kt` — intent → `Fido2CredentialAssertionRequest` parsing, `isUserPreVerified` extraction.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/vault/datasource/sdk/model/AuthenticateFido2CredentialRequest.kt` — SDK request envelope, carries `isUserVerificationSupported`.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/vault/datasource/sdk/util/PublicKeyCredentialAuthenticatorAssertionResponseExtensions.kt` — SDK → Android response conversion; PRF results must be added here.
- `app/src/main/kotlin/com/x8bit/bitwarden/data/vault/datasource/sdk/util/PublicKeyCredentialAuthenticatorAttestationResponseExtensions.kt` — SDK → Android attestation conversion.
- `app/src/main/kotlin/com/x8bit/bitwarden/CredentialProviderViewModel.kt` — UV flag plumbing from request to credential manager.
- `cxf/build.gradle.kts` — CXF module pins, flavor split for Play Services backend.
- `cxf/src/main/kotlin/com/bitwarden/cxf/parser/CredentialExchangePayloadParserImpl.kt` — CXF version support (`0.0`, `1.0`), opaque account items.
- `cxf/src/main/kotlin/com/bitwarden/cxf/model/CredentialExchangeExportResponse.kt` — CXF export response shape, items as `JsonArray`.
- `cxf/src/standard/kotlin/com/bitwarden/cxf/importer/CredentialExchangeImporterImpl.kt` — uses `KnownExtensions.KNOWN_EXTENSION_SHARED` for the importer capability advertisement.

## 9. Contract gaps and required follow-ups

The contract documents the **target** behavior. The current code does not yet implement all of it. The gaps below are the work items this contract unblocks. They are owned by later milestones, not by M0.

1. `PasskeyAssertionOptions` does not deserialize `extensions`. PRF inputs are dropped today. Fix: extend the model with an optional `extensions` field (or parse PRF separately) without breaking existing clients. → WP2 / WP4.
2. `Fido2PublicKeyCredential.ClientExtensionResults` only has `credProps`. PRF outputs from the SDK are dropped today. Fix: add a `prf` field that mirrors the SDK's `ClientExtensionResults.prf`. → WP2.
3. `Fido2AttestationResponse.ClientExtensionResults` has the same `credProps`-only limitation for the creation path. Fix: mirror the assertion-side change when attestation PRF is enabled. → WP2.
4. `PublicKeyCredentialAuthenticatorAssertionResponseExtensions.toAndroidFido2PublicKeyCredential` only reads `credProps`. Fix: also read `prf` and surface it. → WP2.
5. The CXF import path (`CredentialExchangePayloadParserImpl`, `CredentialExchangeImporter`) does not model `fido2Extensions`. The `items` array is opaque. Fix: owned by the shared encrypted credential model contract (WP1) and the CXF mapping contract, not by this Android contract.
6. There is no conformance fixture for the constrained and discoverable PRF request/response shapes. → unblocked by this contract, delivered by issue #35.

This contract is the input to issue #35 (conformance fixtures) and to the WP4 Android recovery implementation. Any change to the version pins, the manifest capability list, the request/response models, or the UV plumbing must update this document first.
