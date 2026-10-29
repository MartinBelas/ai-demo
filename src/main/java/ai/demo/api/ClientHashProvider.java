package ai.demo.api;

import ai.demo.exception.ConfigurationException;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identifies a client by a salted hash of its address. The address comes from the entry of {@code
 * X-Forwarded-For} that the outermost trusted proxy appended: proxies append to the header, so
 * entries to the left of it are whatever the client sent and must not be trusted.
 */
final class ClientHashProvider {

  private static final Logger log = LoggerFactory.getLogger(ClientHashProvider.class);
  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private final byte[] salt;
  private final int trustedProxyHops;
  private final AtomicBoolean shortHeaderLogged = new AtomicBoolean();
  private final AtomicBoolean ignoredHeaderLogged = new AtomicBoolean();

  ClientHashProvider(String salt, int trustedProxyHops) {
    if (salt == null || salt.isBlank()) {
      throw new ConfigurationException("Demo IP hash salt is required when limits are enabled");
    }
    if (trustedProxyHops < 0) {
      throw new ConfigurationException("Trusted proxy hops must not be negative");
    }
    this.salt = salt.getBytes(StandardCharsets.UTF_8);
    this.trustedProxyHops = trustedProxyHops;
  }

  String hash(Context context) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(salt);
      return HexFormat.of()
          .formatHex(digest.digest(clientAddress(context).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new ConfigurationException("SHA-256 is unavailable", e);
    }
  }

  /**
   * With {@code n} trusted proxies the client address is the {@code n}-th entry from the right.
   * Repeated header lines count as one comma-separated list. Without the header, without trusted
   * proxies, or with fewer entries than trusted proxies (the request did not pass all of them), the
   * address of the direct peer is used.
   */
  String clientAddress(Context context) {
    if (trustedProxyHops == 0) {
      warnOnceIfHeaderIsIgnored(context);
      return context.ip();
    }
    String forwarded = String.join(",", Collections.list(context.req().getHeaders(FORWARDED_FOR)));
    if (forwarded.isBlank()) {
      return context.ip();
    }
    String[] entries = forwarded.split(",", -1);
    int index = entries.length - trustedProxyHops;
    String address = index < 0 ? "" : entries[index].trim();
    if (address.isEmpty()) {
      warnOnce();
      return context.ip();
    }
    return address;
  }

  /**
   * Behind a proxy, ignoring the header puts every client into one shared limit, typically because
   * the deployment did not set the hop count. Checked only until the warning was written once.
   */
  private void warnOnceIfHeaderIsIgnored(Context context) {
    if (!ignoredHeaderLogged.get()
        && context.header(FORWARDED_FOR) != null
        && ignoredHeaderLogged.compareAndSet(false, true)) {
      log.warn(
          "X-Forwarded-For is present but ignored because demo.limits.trusted-proxy-hops is 0;"
              + " clients behind a proxy then share one hourly limit");
    }
  }

  private void warnOnce() {
    if (shortHeaderLogged.compareAndSet(false, true)) {
      log.warn(
          "X-Forwarded-For has no usable entry for {} trusted proxy hops; using the peer address."
              + " Check demo.limits.trusted-proxy-hops",
          trustedProxyHops);
    }
  }
}
