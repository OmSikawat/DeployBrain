package com.deploybrain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookVerificationServiceTest {

    private WebhookVerificationService verificationService;
    private static final String TEST_SECRET = "test-webhook-secret-12345";
    private static final String TEST_BODY = "{\"action\":\"completed\",\"workflow_run\":{\"id\":123}}";

    @BeforeEach
    void setUp() {
        verificationService = new WebhookVerificationService();
        // webhookSecret is @Value-injected in the real bean; set it
        // directly via reflection since we're not spinning up a full
        // Spring context for this unit test.
        ReflectionTestUtils.setField(verificationService, "webhookSecret", TEST_SECRET);
    }

    @Test
    void validSignature_shouldPassVerification() throws Exception {
        String validSignature = computeRealHmac(TEST_BODY, TEST_SECRET);

        boolean result = verificationService.isValidSignature(TEST_BODY, validSignature);

        assertTrue(result, "A correctly computed HMAC signature should pass verification");
    }

    @Test
    void tamperedBody_shouldFailVerification() throws Exception {
        // signature computed over the ORIGINAL body...
        String signatureForOriginalBody = computeRealHmac(TEST_BODY, TEST_SECRET);
        String tamperedBody = TEST_BODY.replace("completed", "cancelled");

        // ...but verification attempted against a DIFFERENT (tampered) body
        boolean result = verificationService.isValidSignature(tamperedBody, signatureForOriginalBody);

        assertFalse(result, "A signature computed over different content must fail verification");
    }

    @Test
    void missingSignatureHeader_shouldFailVerification() {
        boolean result = verificationService.isValidSignature(TEST_BODY, null);

        assertFalse(result, "A null signature header must be rejected, not throw an exception");
    }

    @Test
    void blankSignatureHeader_shouldFailVerification() {
        boolean result = verificationService.isValidSignature(TEST_BODY, "");

        assertFalse(result, "An empty signature header must be rejected");
    }

    @Test
    void signatureWithWrongSecret_shouldFailVerification() throws Exception {
        String signatureWithWrongSecret = computeRealHmac(TEST_BODY, "a-completely-different-secret");

        boolean result = verificationService.isValidSignature(TEST_BODY, signatureWithWrongSecret);

        assertFalse(result, "A signature computed with the wrong secret must fail verification");
    }

    @Test
    void signatureMissingShaPrefix_shouldFailVerification() throws Exception {
        String rawHmacWithoutPrefix = computeRawHmacHex(TEST_BODY, TEST_SECRET);
        // deliberately missing the required "sha256=" prefix GitHub always sends

        boolean result = verificationService.isValidSignature(TEST_BODY, rawHmacWithoutPrefix);

        assertFalse(result, "A signature missing the 'sha256=' prefix must be rejected");
    }

    /**
     * Computes a real HMAC-SHA256 signature in the exact format GitHub
     * sends it ("sha256=<hex>"), independently of the production code,
     * so this test genuinely validates the algorithm rather than
     * trivially matching a hardcoded string.
     */
    private String computeRealHmac(String body, String secret) throws Exception {
        return "sha256=" + computeRawHmacHex(body, secret);
    }

    private String computeRawHmacHex(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : hmacBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}