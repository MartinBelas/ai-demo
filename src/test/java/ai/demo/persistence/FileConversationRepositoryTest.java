package ai.demo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileConversationRepositoryTest {

  @Test
  void shouldSaveAndLoadConversation() throws Exception {

    // Create temporary file
    Path tempFile = Files.createTempFile("conversation", ".json");

    ObjectMapper mapper = new ObjectMapper();
    FileConversationRepository repo = new FileConversationRepository(tempFile, mapper);

    // Create conversation
    Conversation conversation = new Conversation();
    conversation.add(ChatMessage.user("Hello"));
    conversation.add(ChatMessage.assistant("Hi there"));

    // Save conversation
    repo.save(conversation);

    // Load conversation
    Conversation loaded = repo.load();

    assertNotNull(loaded);
    assertEquals(2, loaded.messages().size());
    assertEquals("Hello", loaded.messages().get(0).content());
    assertEquals("Hi there", loaded.messages().get(1).content());
  }
}
