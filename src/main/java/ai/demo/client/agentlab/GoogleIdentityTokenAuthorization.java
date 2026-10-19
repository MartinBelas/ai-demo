package ai.demo.client.agentlab;

import ai.demo.client.http.HttpTransport;
import ai.demo.exception.AgentServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Authenticates calls to a private Cloud Run agent service with a Google-signed identity token of
 * the runtime service account.
 *
 * <p>The token is fetched from the Cloud Run metadata server for the agent service's URL as the
 * audience, cached, and refreshed shortly before it expires. Cloud Run itself verifies the token
 * and the invoker permission, so the agent service needs no code for it. The token is never logged.
 */
public final class GoogleIdentityTokenAuthorization implements AgentLabAuthorization {

  // Fixed address of the metadata server on Google Cloud, not a deployment setting.
  @SuppressWarnings("java:S1075")
  private static final String METADATA_IDENTITY_URL =
      "http://metadata.google.internal/computeMetadata/v1"
          + "/instance/service-accounts/default/identity";

  private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
  private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(5);

  private final HttpTransport transport;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final URI tokenUri;
  private String token;
  private Instant expiresAt = Instant.MIN;

  public GoogleIdentityTokenAuthorization(
      String agentServiceUrl, HttpTransport transport, ObjectMapper mapper) {
    this(agentServiceUrl, transport, mapper, Clock.systemUTC());
  }

  GoogleIdentityTokenAuthorization(
      String agentServiceUrl, HttpTransport transport, ObjectMapper mapper, Clock clock) {
    this.transport = transport;
    this.mapper = mapper;
    this.clock = clock;
    this.tokenUri =
        URI.create(
            METADATA_IDENTITY_URL
                + "?audience="
                + URLEncoder.encode(audience(agentServiceUrl), StandardCharsets.UTF_8));
  }

  @Override
  public synchronized Optional<String> header() {
    if (token == null || !clock.instant().isBefore(expiresAt.minus(REFRESH_BEFORE_EXPIRY))) {
      token = fetchToken();
      expiresAt = expiry(token);
    }
    return Optional.of("Bearer " + token);
  }

  /** Cloud Run expects the service URL without any path as the audience. */
  static String audience(String agentServiceUrl) {
    URI uri = URI.create(agentServiceUrl);
    return uri.getScheme() + "://" + uri.getRawAuthority();
  }

  private String fetchToken() {
    HttpRequest request =
        HttpRequest.newBuilder(tokenUri)
            .timeout(METADATA_TIMEOUT)
            .header("Metadata-Flavor", "Google")
            .GET()
            .build();
    try {
      HttpResponse<String> response = transport.send(request);
      if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
        throw new AgentServiceException(
            "Identity token request returned HTTP " + response.statusCode());
      }
      return response.body().strip();
    } catch (IOException e) {
      throw new AgentServiceException("Unable to obtain an identity token for the agent lab", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AgentServiceException("Identity token request was interrupted", e);
    }
  }

  /** Reads the {@code exp} claim; the signature is verified by Cloud Run, not here. */
  private Instant expiry(String jwt) {
    String[] parts = jwt.split("\\.");
    if (parts.length != 3) {
      throw new AgentServiceException("Identity token has an unexpected format");
    }
    try {
      JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
      JsonNode exp = claims.path("exp");
      if (!exp.canConvertToLong()) {
        throw new AgentServiceException("Identity token has no expiry");
      }
      return Instant.ofEpochSecond(exp.asLong());
    } catch (IOException | IllegalArgumentException e) {
      throw new AgentServiceException("Identity token has an unexpected format", e);
    }
  }
}
