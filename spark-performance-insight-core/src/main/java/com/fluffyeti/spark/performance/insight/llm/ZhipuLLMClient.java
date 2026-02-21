package com.fluffyeti.spark.performance.insight.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@ConditionalOnProperty(name = "insight.ai.provider", havingValue = "zhipu", matchIfMissing = true)
public class ZhipuLLMClient implements LLMClient {

    @Value("${spring.ai.zhipuai.api-key:}")
    private String zhipuApiKey;

    @Value("${spring.ai.zhipuai.chat.options.model:glm-4.7}")
    private String zhipuModelName;

    @Override
    public String generate(String systemPrompt, String userContext) {
        if (!StringUtils.hasText(zhipuApiKey)) {
            throw new IllegalArgumentException("Zhipu AI API Key is missing");
        }

        log.info("Using Zhipu AI Model: {}", zhipuModelName);

        ZhiPuAiApi zhipuAiApi = new ZhiPuAiApi(zhipuApiKey);
        ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(zhipuAiApi, ZhiPuAiChatOptions.builder()
                .withModel(zhipuModelName)
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
