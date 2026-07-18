package com.x8bit.bitwarden.data.vault.datasource.sdk.util

import android.util.Base64
import com.bitwarden.fido.AuthenticatorAssertionResponse
import com.bitwarden.fido.ClientExtensionResults
import com.bitwarden.fido.ClientPrfOutput
import com.bitwarden.fido.CredPropsResult
import com.bitwarden.fido.PrfOutputValues
import com.bitwarden.fido.PublicKeyCredentialAuthenticatorAssertionResponse
import com.bitwarden.fido.SelectedCredential
import com.x8bit.bitwarden.data.credentials.model.Fido2PublicKeyCredential
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockCipherView
import com.x8bit.bitwarden.data.vault.datasource.sdk.model.createMockFido2CredentialView
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PublicKeyCredentialAuthenticatorAssertionResponseExtensionsTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            when {
                firstArg<ByteArray>().contentEquals(PRF_FIRST_OUTPUT) -> ENCODED_PRF_FIRST_OUTPUT
                firstArg<ByteArray>().contentEquals(PRF_SECOND_OUTPUT) -> ENCODED_PRF_SECOND_OUTPUT
                else -> ""
            }
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `authenticatorAttachment should be null when SDK value is null`() {
        val mockSdkResponse = createMockSdkAssertionResponse(number = 1)
        val result = mockSdkResponse.toAndroidFido2PublicKeyCredential()
        assertNull(result.authenticatorAttachment)
    }

    @Test
    fun `authenticatorAttachment should be populated when SDK value is non-null`() {
        val mockSdkResponse = createMockSdkAssertionResponse(
            number = 1,
            authenticatorAttachment = "mockAuthenticatorAttachment",
        )
        val result = mockSdkResponse.toAndroidFido2PublicKeyCredential()
        assertNotNull(result.authenticatorAttachment)
    }

    @Test
    fun `credentialProperties should be null when SDK value is null`() {
        val mockSdkResponse = createMockSdkAssertionResponse(number = 1)
        val result = mockSdkResponse.toAndroidFido2PublicKeyCredential()
        assertNull(result.clientExtensionResults.credentialProperties)
    }

    @Test
    fun `credentialProperties should be populated when SDK value is non-null`() {
        val mockSdkResponse = createMockSdkAssertionResponse(
            number = 1,
            credProps = CredPropsResult(
                rk = true,
            ),
        )
        val result = mockSdkResponse.toAndroidFido2PublicKeyCredential()
        assertNotNull(result.clientExtensionResults.credentialProperties)
    }

    @Test
    fun `residentKey defaults to true when SDK value is null`() {
        val mockSdkResponse = createMockSdkAssertionResponse(
            number = 1,
            credProps = CredPropsResult(
                rk = null,
            ),
        )
        val result = mockSdkResponse.toAndroidFido2PublicKeyCredential()
        assertTrue(result.clientExtensionResults.credentialProperties?.residentKey!!)
    }

    @Test
    fun `prf should be omitted when SDK result is absent`() {
        val result = createMockSdkAssertionResponse(
            number = 1,
            prf = ClientPrfOutput(enabled = null, results = null),
        ).toAndroidFido2PublicKeyCredential()

        assertNull(result.clientExtensionResults.prf)
        assertFalse(result.serializedClientExtensionResults().containsKey("prf"))
    }

    @Test
    fun `prf should serialize the first result as unpadded base64url`() {
        val result = createMockSdkAssertionResponse(
            number = 1,
            prf = ClientPrfOutput(
                enabled = null,
                results = PrfOutputValues(first = PRF_FIRST_OUTPUT, second = null),
            ),
        ).toAndroidFido2PublicKeyCredential()

        val results = result.serializedPrfResults()
        assertEquals(setOf("first"), results.keys)
        assertEquals(ENCODED_PRF_FIRST_OUTPUT, results.getValue("first").jsonPrimitive.content)
        verify(exactly = 1) {
            Base64.encodeToString(
                match { it.contentEquals(PRF_FIRST_OUTPUT) },
                FIDO2_BASE64_FLAGS,
            )
        }
    }

    @Test
    fun `prf should serialize both results as unpadded base64url`() {
        val result = createMockSdkAssertionResponse(
            number = 1,
            prf = ClientPrfOutput(
                enabled = null,
                results = PrfOutputValues(
                    first = PRF_FIRST_OUTPUT,
                    second = PRF_SECOND_OUTPUT,
                ),
            ),
        ).toAndroidFido2PublicKeyCredential()

        val results = result.serializedPrfResults()
        assertEquals(setOf("first", "second"), results.keys)
        assertEquals(ENCODED_PRF_FIRST_OUTPUT, results.getValue("first").jsonPrimitive.content)
        assertEquals(ENCODED_PRF_SECOND_OUTPUT, results.getValue("second").jsonPrimitive.content)
        verify(exactly = 1) {
            Base64.encodeToString(
                match { it.contentEquals(PRF_SECOND_OUTPUT) },
                FIDO2_BASE64_FLAGS,
            )
        }
    }
}

private fun createMockSdkAssertionResponse(
    number: Int,
    authenticatorAttachment: String? = null,
    credProps: CredPropsResult? = null,
    prf: ClientPrfOutput? = null,
) = PublicKeyCredentialAuthenticatorAssertionResponse(
    id = "mockId-$number",
    rawId = byteArrayOf(0),
    ty = "mockTy-$number",
    authenticatorAttachment = authenticatorAttachment,
    clientExtensionResults = ClientExtensionResults(
        credProps = credProps,
        prf = prf,
    ),
    response = AuthenticatorAssertionResponse(
        clientDataJson = byteArrayOf(0),
        authenticatorData = byteArrayOf(0),
        signature = byteArrayOf(0),
        userHandle = byteArrayOf(0),
    ),
    selectedCredential = SelectedCredential(
        cipher = createMockCipherView(number = 1),
        credential = createMockFido2CredentialView(number = 1),
    ),
)

private fun Fido2PublicKeyCredential.serializedClientExtensionResults() = RESPONSE_JSON
    .parseToJsonElement(RESPONSE_JSON.encodeToString(this))
    .jsonObject
    .getValue("clientExtensionResults")
    .jsonObject

private fun Fido2PublicKeyCredential.serializedPrfResults() = serializedClientExtensionResults()
    .getValue("prf")
    .jsonObject
    .getValue("results")
    .jsonObject

private val RESPONSE_JSON = Json { explicitNulls = false }
private val PRF_FIRST_OUTPUT = byteArrayOf(1, 2)
private val PRF_SECOND_OUTPUT = byteArrayOf(3, 4)
private const val ENCODED_PRF_FIRST_OUTPUT = "AQI"
private const val ENCODED_PRF_SECOND_OUTPUT = "AwQ"
private const val FIDO2_BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
