package com.backendguru.recommendationservice.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

  /**
   * Registers @Tool-annotated methods on the given bean as MCP tools. Spring AI's MCP server
   * starter picks this up and exposes them at the configured transport (HTTP/SSE).
   */
  @Bean
  ToolCallbackProvider productToolCallbackProvider(ProductMcpTools tools) {
    return MethodToolCallbackProvider.builder().toolObjects(tools).build();
  }
}
