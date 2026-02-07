package com.spark.insight;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LLMConnectionTest {

    @Test
    public void testZhipuConnection() {
        // 智谱 AI 配置
        String apiKey = "b49b90f8876c4c61a4e4c13714b030fe.6N6muPxHuHAbTgvK";
        
        ZhiPuAiApi zhipuAiApi = new ZhiPuAiApi(apiKey);
        ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(zhipuAiApi);

        try {
            System.out.println(">>> Sending test request to Zhipu AI (Native SDK 1.0.0-M1)...");
            ChatResponse response = chatModel.call(
                    new Prompt(
                            "Hello, please reply with 'Confirmed' if you receive this.",
                            ZhiPuAiChatOptions.builder()
                                    .withModel("glm-4-flash")
                                    .withTemperature(0.5f)
                                    .build()
                    ));

            String content = response.getResult().getOutput().getContent();
            System.out.println(">>> AI Response: " + content);
            assertNotNull(content);
        } catch (Exception e) {
            System.err.println(">>> Connection failed!");
            e.printStackTrace();
            throw e;
        }
    }
}
