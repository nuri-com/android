package com.x8bit.bitwarden.data.credentials.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the authenticator's response to a client’s request for the creation of a new public
 * key credential.
 *
 * Refer to https://w3c.github.io/webauthn/#iface-authenticatorattestationresponse for details.
 */
@Serializable
data class Fido2AttestationResponse(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
    @SerialName("rawId")
    val rawId: String,
    @SerialName("response")
    val response: RegistrationResponse,
    @SerialName("clientExtensionResults")
    val clientExtensionResults: ClientExtensionResults,
    @SerialName("authenticatorAttachment")
    val authenticatorAttachment: String?,
) {
    /**
     * Represents the registration result data expected from a FIDO2 credential registration
     * request.
     */
    @Serializable
    data class RegistrationResponse(
        @SerialName("clientDataJSON")
        val clientDataJson: String,
        @SerialName("attestationObject")
        val attestationObject: String,
        @SerialName("transports")
        val transports: List<String>?,
        @SerialName("publicKeyAlgorithm")
        val publicKeyAlgorithm: Long,
        @SerialName("publicKey")
        val publicKey: String?,
        @SerialName("authenticatorData")
        val authenticatorData: String?,
    )

    /**
     * Represents an extension processing result produced by the client.
     */
    @Serializable
    data class ClientExtensionResults(
        @SerialName("credProps")
        val credentialProperties: CredentialProperties? = null,
        @SerialName("prf")
        val prf: Prf? = null,
    ) {
        /**
         * Represents properties for newly created credential.
         */
        @Serializable
        data class CredentialProperties(
            @SerialName("rk")
            val residentKey: Boolean,
        )

        /**
         * Models WebAuthn PRF extension output for a registration.
         */
        @Serializable
        data class Prf(
            @SerialName("enabled")
            val enabled: Boolean?,
            @SerialName("results")
            val results: Results? = null,
        ) {
            /**
             * Models the first required and optional second PRF outputs.
             */
            @Serializable
            data class Results(
                @SerialName("first")
                val first: String,
                @SerialName("second")
                val second: String?,
            )
        }
    }
}
