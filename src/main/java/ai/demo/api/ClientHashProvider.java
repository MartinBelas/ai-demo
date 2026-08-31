package ai.demo.api;

import ai.demo.exception.ConfigurationException;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ClientHashProvider {

  private final byte[] salt;

  ClientHashProvider(String salt) {
    if (salt == null || salt.isBlank()) {
      throw new ConfigurationException("Demo IP hash salt is required when limits are enabled");
    }
    this.salt = salt.getBytes(StandardCharsets.UTF_8);
  }

  String hash(Context context) {
    String forwarded = context.header("X-Forwarded-For");
    String address =
        forwarded == null || forwarded.isBlank() ? context.ip() : forwarded.split(",", 2)[0].trim();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(salt);
      return HexFormat.of().formatHex(digest.digest(address.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new ConfigurationException("SHA-256 is unavailable", e);
    }
  }
}
