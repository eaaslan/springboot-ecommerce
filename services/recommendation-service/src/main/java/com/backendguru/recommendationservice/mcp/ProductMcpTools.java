package com.backendguru.recommendationservice.mcp;

import com.backendguru.recommendationservice.client.dto.ProductSummary;
import com.backendguru.recommendationservice.recommendation.RecommendationService;
import com.backendguru.recommendationservice.recommendation.dto.RecommendationItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Methods exposed as MCP tools at {@code /mcp} via Spring AI's MCP server starter. AI agents
 * (Claude Desktop, Cursor, Claude Code) can list and invoke these tools as if they were native
 * functions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductMcpTools {

  private final RecommendationService service;

  @Tool(
      description =
          "Find products similar to a given product id (content-based scoring on category, name tokens, price proximity)")
  public List<RecommendationItem> similarProducts(
      @ToolParam(description = "Source product id") long productId,
      @ToolParam(description = "Max results, default 5") Integer k) {
    log.info("MCP tool similarProducts called: productId={}, k={}", productId, k);
    return service.similarProducts(productId, k == null ? 5 : k);
  }

  @Tool(
      description =
          "Recommend top-k products for a user (popularity-by-stock proxy until order history is wired in Phase 10)")
  public List<RecommendationItem> recommendForUser(
      @ToolParam(description = "User id") long userId,
      @ToolParam(description = "Max results, default 5") Integer k) {
    log.info("MCP tool recommendForUser called: userId={}, k={}", userId, k);
    return service.recommendForUser(userId, k == null ? 5 : k);
  }

  @Tool(description = "Free-text search across enabled product names")
  public List<ProductSummary> searchProducts(
      @ToolParam(description = "Search query, e.g. 'wireless headphones'") String query,
      @ToolParam(description = "Max results, default 10") Integer limit) {
    log.info("MCP tool searchProducts called: query={}, limit={}", query, limit);
    return service.searchProducts(query, limit == null ? 10 : limit);
  }
}
