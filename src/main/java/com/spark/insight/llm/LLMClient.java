package com.spark.insight.llm;

public interface LLMClient {
    /**
     * Generate a response from the LLM based on system prompt and user context.
     *
     * @param systemPrompt The system prompt defining the role and task.
     * @param userContext  The specific context or data for the LLM to process.
     * @return The generated response string.
     */
    String generate(String systemPrompt, String userContext);
}
