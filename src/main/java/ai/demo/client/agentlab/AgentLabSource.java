package ai.demo.client.agentlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A web page the agent found with its web search tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentLabSource(String title, String url) {}
