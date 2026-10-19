package ai.demo.client.agentlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Response body of the agent lab's {@code POST /api/chat} (contract v1).
 *
 * <p>The agent lab is our own service, but its sources come from the web, so they are checked again
 * here: only absolute http(s) URLs are kept, at most {@value #MAX_SOURCES} of them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentLabResponse(String reply, List<AgentLabSource> sources, AgentLabUsage usage) {

  static final int MAX_SOURCES = 10;

  public AgentLabResponse {
    if (reply == null || reply.isBlank()) {
      throw new IllegalArgumentException("Agent lab reply must not be blank");
    }
    if (usage == null) {
      throw new IllegalArgumentException("Agent lab usage must be present");
    }
    sources = safeSources(sources);
  }

  private static List<AgentLabSource> safeSources(List<AgentLabSource> sources) {
    if (sources == null) {
      return List.of();
    }
    return sources.stream()
        .filter(Objects::nonNull)
        .filter(source -> isWebUrl(source.url()))
        .map(source -> new AgentLabSource(titleOrUrl(source), source.url()))
        .limit(MAX_SOURCES)
        .toList();
  }

  private static boolean isWebUrl(String url) {
    if (url == null) {
      return false;
    }
    try {
      URI uri = URI.create(url);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getHost() != null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static String titleOrUrl(AgentLabSource source) {
    return source.title() == null || source.title().isBlank() ? source.url() : source.title();
  }
}
