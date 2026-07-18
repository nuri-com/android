package com.x8bit.bitwarden.data.credentials.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models the request options for a passkey request, based off the spec found at:
 * https://www.w3.org/TR/webauthn-2/#dictionary-assertion-options
 */
@Serializable
data class PasskeyAssertionOptions(
    @SerialName("challenge")
    val challenge: String,
    @SerialName("allowCredentials")
    val allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
    @SerialName("rpId")
    val relyingPartyId: String?,
    @SerialName("userVerification")
    val userVerification: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
    @SerialName("extensions")
    val extensions: AuthenticationExtensionsClientInputs? = null,
) {

    /** Whether this request asks the authenticator to evaluate the WebAuthn PRF extension. */
    val evaluatesPrf: Boolean
        get() = extensions
            ?.prf
            ?.let { it.eval != null || !it.evalByCredential.isNullOrEmpty() }
            ?: false

    /** Client extension inputs used during an assertion ceremony. */
    @Serializable
    data class AuthenticationExtensionsClientInputs(
        @SerialName("prf")
        val prf: PrfInputs? = null,
    )

    /** WebAuthn PRF inputs. The SDK selects evalByCredential before the eval fallback. */
    @Serializable
    data class PrfInputs(
        @SerialName("eval")
        val eval: PrfValues? = null,
        @SerialName("evalByCredential")
        val evalByCredential: Map<String, PrfValues>? = null,
    )

    /** Opaque, base64url-encoded raw WebAuthn PRF inputs. */
    @Serializable
    data class PrfValues(
        @SerialName("first")
        val first: String,
        @SerialName("second")
        val second: String? = null,
    )
}
