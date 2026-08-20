package ai.demo.persistence;

import ai.demo.exception.PersistenceException;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileConversationRepository implements ConversationRepository {

  private final Path file;
  private final ObjectMapper objectMapper;

  public FileConversationRepository(Path file, ObjectMapper objectMapper) {
    this.file = file;

    // Ensure mapper ignores unknown fields
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    this.objectMapper = objectMapper;
  }

  @Override
  public void save(Conversation conversation) {
    try {
      objectMapper.writeValue(file.toFile(), conversation);
    } catch (IOException e) {
      throw new PersistenceException("Failed to save conversation to " + file, e);
    }
  }

  @Override
  public Conversation load() {
    try {
      if (!Files.exists(file)) {
        return new Conversation();
      }

      String json = Files.readString(file).trim();

      if (json.isEmpty() || json.equals("{}")) {
        return new Conversation();
      }

      Conversation loaded = objectMapper.readValue(json, Conversation.class);

      return loaded;

    } catch (IOException e) {
      throw new PersistenceException("Failed to load conversation from " + file, e);
    }
  }
}
