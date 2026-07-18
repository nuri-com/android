package com.x8bit.bitwarden.data.credentials.conformance

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.Base64

class AndroidPrfConformanceFixturesTest {

    @Test
    fun `constrained eval fixture preserves the raw first input and requires UV`() {
        val request = parseFixture("request-constrained-eval-first.json")

        assertRequestEnvelope(request = request, constrained = true, requiresUv = true)
        val prf = request.requiredPrf()
        assertFieldNames(prf, setOf("eval"), "extensions.prf")
        val eval = prf.requiredObject("eval", "extensions.prf")
        assertFieldNames(eval, setOf("first"), "extensions.prf.eval")
        assertRawInput(
            encoded = eval.requiredString("first", "extensions.prf.eval"),
            expected = NURI_FIRST_INPUT,
            path = "extensions.prf.eval.first",
        )
    }

    @Test
    fun `discoverable eval fixture preserves raw first and second inputs and requires UV`() {
        val request = parseFixture("request-discoverable-eval-first-second.json")

        assertRequestEnvelope(request = request, constrained = false, requiresUv = true)
        val prf = request.requiredPrf()
        assertFieldNames(prf, setOf("eval"), "extensions.prf")
        val eval = prf.requiredObject("eval", "extensions.prf")
        assertFieldNames(eval, setOf("first", "second"), "extensions.prf.eval")
        assertRawInput(
            encoded = eval.requiredString("first", "extensions.prf.eval"),
            expected = NURI_FIRST_INPUT,
            path = "extensions.prf.eval.first",
        )
        assertRawInput(
            encoded = eval.requiredString("second", "extensions.prf.eval"),
            expected = SECOND_INPUT,
            path = "extensions.prf.eval.second",
        )
    }

    @Test
    fun `evalByCredential fixture binds first and second inputs to the allowed credential`() {
        val request = parseFixture("request-constrained-eval-by-credential.json")

        assertRequestEnvelope(request = request, constrained = true, requiresUv = true)
        val prf = request.requiredPrf()
        assertFieldNames(prf, setOf("evalByCredential"), "extensions.prf")
        val byCredential = prf.requiredObject("evalByCredential", "extensions.prf")
        assertRedacted(
            condition = byCredential.keys == setOf(CREDENTIAL_ID),
            message = "evalByCredential must contain only the allowed credential; IDs redacted",
        )
        val inputs = byCredential[CREDENTIAL_ID] as? JsonObject
            ?: fail("evalByCredential must contain an object entry; credential ID redacted")
        assertFieldNames(inputs, setOf("first", "second"), "evalByCredential entry")
        assertRawInput(
            encoded = inputs.requiredString("first", "evalByCredential entry"),
            expected = NURI_FIRST_INPUT,
            path = "evalByCredential.first",
        )
        assertRawInput(
            encoded = inputs.requiredString("second", "evalByCredential entry"),
            expected = SECOND_INPUT,
            path = "evalByCredential.second",
        )
    }

    @Test
    fun `non UV PRF fixture is explicitly invalid`() {
        val request = parseFixture("request-non-uv-rejected.json")

        assertRequestEnvelope(request = request, constrained = false, requiresUv = false)
        assertFalse(
            request.isEffectiveUvPrfRequest(),
            "PRF requests without effective UV must remain a fail-closed fixture",
        )
    }

    @Test
    fun `first-only assertion response has the exact Android PRF JSON shape`() {
        val response = parseFixture("response-eval-first.json")

        assertResponseEnvelope(response)
        val results = response.requiredPrfResults()
        assertFieldNames(results, setOf("first"), "clientExtensionResults.prf.results")
        assertPrfOutput(
            encoded = results.requiredString("first", "prf.results"),
            expected = FIRST_OUTPUT,
            path = "clientExtensionResults.prf.results.first",
        )
    }

    @Test
    fun `two-output assertion response has the exact Android PRF JSON shape`() {
        val response = parseFixture("response-eval-first-second.json")

        assertResponseEnvelope(response)
        val results = response.requiredPrfResults()
        assertFieldNames(
            results,
            setOf("first", "second"),
            "clientExtensionResults.prf.results",
        )
        assertPrfOutput(
            encoded = results.requiredString("first", "prf.results"),
            expected = FIRST_OUTPUT,
            path = "clientExtensionResults.prf.results.first",
        )
        assertPrfOutput(
            encoded = results.requiredString("second", "prf.results"),
            expected = SECOND_OUTPUT,
            path = "clientExtensionResults.prf.results.second",
        )
    }
}

private fun assertRequestEnvelope(
    request: JsonObject,
    constrained: Boolean,
    requiresUv: Boolean,
) {
    val expectedFields = setOf("challenge", "rpId", "userVerification", "extensions")
        .let { if (constrained) it + "allowCredentials" else it }
    assertFieldNames(request, expectedFields, "request")
    assertRedactedEquals(
        expected = CHALLENGE,
        actual = request.requiredString("challenge", "request"),
        path = "request.challenge",
    )
    assertBase64Url(request.requiredString("challenge", "request"), "request.challenge", 32)
    assertRedactedEquals(
        expected = "nuri.com",
        actual = request.requiredString("rpId", "request"),
        path = "request.rpId",
    )
    assertRedactedEquals(
        expected = if (requiresUv) "required" else "discouraged",
        actual = request.requiredString("userVerification", "request"),
        path = "request.userVerification",
    )
    if (constrained) {
        assertAllowedCredential(request.requiredArray("allowCredentials", "request"))
    } else {
        assertFalse(
            request.containsKey("allowCredentials"),
            "discoverable request must omit allowCredentials",
        )
    }
}

private fun assertAllowedCredential(allowCredentials: JsonArray) {
    assertEquals(1, allowCredentials.size, "constrained request must contain one credential")
    val descriptor = allowCredentials.firstOrNull() as? JsonObject
        ?: fail("allowCredentials entry must be an object; values redacted")
    assertFieldNames(descriptor, setOf("type", "id", "transports"), "allowCredentials entry")
    assertRedactedEquals(
        expected = "public-key",
        actual = descriptor.requiredString("type", "allowCredentials entry"),
        path = "allowCredentials.type",
    )
    val id = descriptor.requiredString("id", "allowCredentials entry")
    assertRedactedEquals(CREDENTIAL_ID, id, "allowCredentials.id")
    assertBase64Url(id, "allowCredentials.id", 32)
    val transports = descriptor.requiredArray("transports", "allowCredentials entry")
    assertEquals(1, transports.size, "credential must contain one transport")
    val transport = (transports.firstOrNull() as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?: fail("credential transport must be a string; value redacted")
    assertRedactedEquals("internal", transport, "allowCredentials.transports[0]")
}

private fun assertResponseEnvelope(response: JsonObject) {
    assertFieldNames(
        response,
        setOf(
            "id",
            "rawId",
            "type",
            "authenticatorAttachment",
            "response",
            "clientExtensionResults",
        ),
        "response",
    )
    val id = response.requiredString("id", "response")
    val rawId = response.requiredString("rawId", "response")
    assertRedactedEquals(CREDENTIAL_ID, id, "response.id")
    assertRedactedEquals(id, rawId, "response.rawId")
    assertBase64Url(rawId, "response.rawId", 32)
    assertRedactedEquals("public-key", response.requiredString("type", "response"), "response.type")
    assertRedactedEquals(
        "platform",
        response.requiredString("authenticatorAttachment", "response"),
        "response.authenticatorAttachment",
    )

    val assertion = response.requiredObject("response", "response")
    assertFieldNames(
        assertion,
        setOf("clientDataJSON", "authenticatorData", "signature", "userHandle"),
        "response.response",
    )
    listOf("clientDataJSON", "authenticatorData", "signature", "userHandle")
        .forEach { field ->
            assertBase64Url(
                encoded = assertion.requiredString(field, "response.response"),
                path = "response.response.$field",
            )
        }
    assertRedactedEquals(
        expected = USER_HANDLE,
        actual = assertion.requiredString("userHandle", "response.response"),
        path = "response.response.userHandle",
    )

    val extensions = response.requiredObject("clientExtensionResults", "response")
    assertFieldNames(extensions, setOf("credProps", "prf"), "clientExtensionResults")
    val credProps = extensions.requiredObject("credProps", "clientExtensionResults")
    assertFieldNames(credProps, setOf("rk"), "clientExtensionResults.credProps")
    assertTrue(
        credProps.requiredBoolean("rk", "clientExtensionResults.credProps"),
        "credential must remain discoverable",
    )
    val prf = extensions.requiredObject("prf", "clientExtensionResults")
    assertFieldNames(prf, setOf("results"), "clientExtensionResults.prf")
}

private fun assertRawInput(encoded: String, expected: ByteArray, path: String) {
    val decoded = assertBase64Url(encoded = encoded, path = path)
    assertRedacted(
        condition = expected.contentEquals(decoded),
        message = "$path must preserve the frozen raw WebAuthn input; values redacted",
    )
}

private fun assertPrfOutput(encoded: String, expected: String, path: String) {
    assertRedactedEquals(expected = expected, actual = encoded, path = path)
    assertBase64Url(encoded = encoded, path = path, expectedByteCount = 32)
}

private fun assertBase64Url(
    encoded: String,
    path: String,
    expectedByteCount: Int? = null,
): ByteArray {
    assertRedacted(
        condition = BASE64URL_PATTERN.matches(encoded),
        message = "$path must be unpadded base64url; value redacted",
    )
    val decoded = try {
        Base64.getUrlDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
        fail("$path must decode as base64url; value redacted")
    }
    assertRedactedEquals(
        expected = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded),
        actual = encoded,
        path = "$path canonical base64url",
    )
    expectedByteCount?.let {
        assertEquals(it, decoded.size, "$path decoded byte count; value redacted")
    }
    return decoded
}

private fun assertFieldNames(actual: JsonObject, expected: Set<String>, path: String) {
    assertEquals(expected, actual.keys, "$path field names")
}

private fun assertRedactedEquals(expected: String, actual: String, path: String) {
    assertRedacted(
        condition = expected == actual,
        message = "$path must match the frozen fixture; values redacted",
    )
}

private fun assertRedacted(condition: Boolean, message: String) {
    assertTrue(condition, message)
}

private fun JsonObject.requiredPrf() =
    requiredObject("extensions", "request")
        .requiredObject("prf", "request.extensions")

private fun JsonObject.requiredPrfResults() =
    requiredObject("clientExtensionResults", "response")
        .requiredObject("prf", "clientExtensionResults")
        .requiredObject("results", "clientExtensionResults.prf")

private fun JsonObject.isEffectiveUvPrfRequest() =
    requiredString("userVerification", "request") == "required" &&
        requiredObject("extensions", "request").containsKey("prf")

private fun JsonObject.requiredObject(field: String, path: String): JsonObject =
    this[field] as? JsonObject
        ?: fail("$path.$field must be an object; value redacted")

private fun JsonObject.requiredArray(field: String, path: String): JsonArray =
    this[field] as? JsonArray
        ?: fail("$path.$field must be an array; value redacted")

private fun JsonObject.requiredString(field: String, path: String): String =
    (this[field] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?: fail("$path.$field must be a string; value redacted")

private fun JsonObject.requiredBoolean(field: String, path: String): Boolean =
    (this[field] as? JsonPrimitive)
        ?.booleanOrNull
        ?: fail("$path.$field must be a boolean; value redacted")

private fun parseFixture(name: String): JsonObject {
    val resource = requireNotNull(
        AndroidPrfConformanceFixturesTest::class.java.getResource("/prf-conformance/$name"),
    ) { "Missing PRF conformance fixture: $name" }
    return try {
        Json.parseToJsonElement(resource.readText()) as? JsonObject
            ?: fail("Fixture $name must contain a JSON object; values redacted")
    } catch (_: SerializationException) {
        fail("Fixture $name must contain valid JSON; values redacted")
    }
}

private val NURI_FIRST_INPUT = "nuri-prf-salt-v1".encodeToByteArray()
private val SECOND_INPUT = "test-salt-2".encodeToByteArray()
private val BASE64URL_PATTERN = Regex("^[A-Za-z0-9_-]+$")
private const val CREDENTIAL_ID = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
private const val USER_HANDLE = "ICEiIyQlJicoKSorLC0uLw"
private const val CHALLENGE = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8"
private const val FIRST_OUTPUT = "d8i94nf9OSyRco1LXP3wnUrRte5rCmguP3cURrj06go"
private const val SECOND_OUTPUT = "wxVdw-_rf1oJwrVQn-r6p4nqgf_dJgsfBDutwJNqdEY"
