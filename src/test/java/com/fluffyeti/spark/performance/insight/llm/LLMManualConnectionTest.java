package com.fluffyeti.spark.performance.insight.llm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual Connectivity Test for LLM Clients.
 * 
 * PURPOSE:
 * This test is designed for new users to verify their LLM API keys and connectivity 
 * without starting the full Spring Boot application.
 * 
 * USAGE:
 * 1. Fill in your API Key and other details in the variables below.
 * 2. Temporarily remove @Disabled or run manually from your IDE.
 */
class LLMManualConnectionTest {

    @Test
    @Disabled("Manual test: requires valid Zhipu AI API Key")
    @DisplayName("Verify Zhipu AI Connectivity")
    void testZhipuConnection() {
        // --- CONFIGURATION ---
        String apiKey = "YOUR_ZHIPU_API_KEY_HERE";
        String modelName = "glm-4.7";
        // ---------------------

        ZhipuLLMClient client = new ZhipuLLMClient();
        ReflectionTestUtils.setField(client, "zhipuApiKey", apiKey);
        ReflectionTestUtils.setField(client, "zhipuModelName", modelName);

        String response = client.generate("You are a helpful assistant.", "Reply with 'Success' if you can read this.");
        
        System.out.println("Zhipu AI Response: " + response);
        assertThat(response).isNotBlank();
    }

    @Test
    @Disabled("Manual test: requires valid OpenAI API Key")
    @DisplayName("Verify OpenAI Connectivity")
    void testOpenAiConnection() {
        // --- CONFIGURATION ---
        String apiKey = "YOUR_OPENAI_API_KEY_HERE";
        String baseUrl = "https://api.openai.com";
        String modelName = "gpt-4-turbo";
        // ---------------------

        OpenAiLLMClient client = new OpenAiLLMClient();
        ReflectionTestUtils.setField(client, "openAiApiKey", apiKey);
        ReflectionTestUtils.setField(client, "openAiBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "openAiModelName", modelName);

        String response = client.generate("You are a helpful assistant.", "Reply with 'Success' if you can read this.");
        
        System.out.println("OpenAI Response: " + response);
        assertThat(response).isNotBlank();
    }
}
