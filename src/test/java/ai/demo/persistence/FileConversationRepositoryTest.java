package ai.demo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.demo.exception.PersistenceException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConversationRepositoryTest {

  @Test
  void shouldSaveAndLoadConversation(@TempDir Path tempDirectory) {
    Path tempFile = tempDirectory.resolve("conversation.json");

    ObjectMapper mapper = new ObjectMapper();
    FileConversationRepository repo = new FileConversationRepository(tempFile, mapper);

    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));
    conversation.add(ChatMessage.assistant("Hi there"));

    repo.save(conversation);

    Conversation loaded = repo.load();

    assertNotNull(loaded);
    assertEquals(2, loaded.messages().size());
    assertEquals("Hello", loaded.messages().get(0).content());
    assertEquals("Hi there", loaded.messages().get(1).content());
  }

  @Test
  void shouldWrapSaveFailureInPersistenceException(@TempDir Path tempDirectory) {
    Path fileInMissingDirectory = tempDirectory.resolve("missing").resolve("conversation.json");
    FileConversationRepository repository =
        new FileConversationRepository(fileInMissingDirectory, new ObjectMapper());
    Conversation conversation = new Conversation();

    PersistenceException exception =
        assertThrows(PersistenceException.class, () -> repository.save(conversation));

    assertEquals(
        "Failed to save conversation to " + fileInMissingDirectory, exception.getMessage());
  }

  @Test
  void shouldWrapLoadFailureInPersistenceException(@TempDir Path tempDirectory) throws IOException {
    Path malformedFile = tempDirectory.resolve("conversation.json");
    Files.writeString(malformedFile, "not-json");
    FileConversationRepository repository =
        new FileConversationRepository(malformedFile, new ObjectMapper());

    PersistenceException exception = assertThrows(PersistenceException.class, repository::load);

    assertEquals("Failed to load conversation from " + malformedFile, exception.getMessage());
  }
}
