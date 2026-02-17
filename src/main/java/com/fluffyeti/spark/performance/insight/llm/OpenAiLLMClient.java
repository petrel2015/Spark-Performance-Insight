package com.fluffyeti.spark.performance.insight.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@ConditionalOnProperty(name = "insight.ai.provider", havingValue = "openai")
public class OpenAiLLMClient implements LLMClient {

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:gpt-4-turbo}")
    private String openAiModelName;

    @Override
    public String generate(String systemPrompt, String userContext) {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalArgumentException("OpenAI API Key is missing");
        }

        log.info("Using OpenAI Model: {}", openAiModelName);

        OpenAiApi openAiApi = new OpenAiApi(openAiBaseUrl, openAiApiKey);
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, OpenAiChatOptions.builder()
                .withModel(openAiModelName)
                .withTemperature(0.1f)
                .build());

        ChatClient chatClient = ChatClient.builder(chatModel)
                .build();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userContext)
                .call()
                .content();
    }
}
