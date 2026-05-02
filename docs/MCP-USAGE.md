# Using the MCP Server

The `recommendation-service` (port 8088) exposes 3 product-recommendation tools to AI agents via the [Model Context Protocol](https://modelcontextprotocol.io). Once registered, Claude / Cursor / any MCP-compatible client can call them as native tools.

## Tools exposed

| Tool | Calls | Returns |
|---|---|---|
| `similarProducts(productId, k)` | `GET /api/recommendations/products/{id}/similar` | top-k similar products |
| `recommendForUser(userId, k)` | `GET /api/recommendations/users/{id}` | top-k popularity items (Phase 10 will use order history) |
| `searchProducts(query, limit)` | string match across product names | matching products |

All defined in [`ProductMcpTools.java`](../services/recommendation-service/src/main/java/com/backendguru/recommendationservice/mcp/ProductMcpTools.java) with `@Tool` annotations from Spring AI.

## Endpoint

Spring AI MCP server starter uses **`/sse`** (NOT `/mcp`) for the SSE handshake. The handshake response tells the client where to POST RPC messages (`/mcp/message`).

```bash
curl -N http://localhost:8088/sse
# id:abc-123
# event:endpoint
# data:/mcp/message
```

## Setup

### Claude Code CLI (one command)

```bash
claude mcp add --transport sse ecommerce http://localhost:8088/sse
claude mcp list
# → ecommerce: http://localhost:8088/sse (SSE) - ✓ Connected
```

After this, **start a new Claude Code session** in this project. The model picks up the MCP tools at session start. Try:

> "Search products for 'wireless'"
> "Show me products similar to product 1"

### Claude Desktop (macOS app)

Edit `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "ecommerce": {
      "url": "http://localhost:8088/sse",
      "transport": "sse"
    }
  }
}
```

Quit and reopen Claude. The 🔌 icon shows the connected server. Same prompts work.

### Cursor IDE

Edit `~/.cursor/mcp.json` (or via `Settings → MCP`):

```json
{
  "mcpServers": {
    "ecommerce": {
      "url": "http://localhost:8088/sse"
    }
  }
}
```

## How it works under the hood

```
You ──prompt──▶ Claude/Cursor
                    │
                    ├─ tools/list       → SSE → recommendation-service
                    │                     returns 3 tool schemas
                    │
                    └─ tools/call(args) → POST /mcp/message
                                          → Spring AI dispatches to ProductMcpTools method
                                          → method calls Feign → product-service
                                          → JSON result back to model
                                          → model uses it in its answer
```

**No model API keys needed.** This is server-side: any MCP-compatible model can connect.

## Demo prompts to try

After registering:

1. "Find products similar to product 1"
2. "Search products for keyword 'wireless'"
3. "Recommend 3 products for user 1"
4. "What's the cheapest product in the catalog similar to the wireless headphones?" *(model will chain tool calls)*

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `claude mcp list` shows ✗ Failed to connect | recommendation-service not running on 8088 |
| `404 Not Found` on `/mcp` | wrong endpoint — use `/sse` |
| Tools don't appear in current Claude Code session | restart the session — MCP tools load at session start |
| Model can't call tools / says "no tool" | check `claude mcp list` shows ✓ Connected |
