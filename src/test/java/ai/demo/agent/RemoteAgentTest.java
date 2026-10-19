package ai.demo.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.demo.client.TokenUsage;
import ai.demo.client.agentlab.AgentLabClient;
import ai.demo.client.agentlab.AgentLabMessage;
import ai.demo.client.agentlab.AgentLabRequest;
import ai.demo.client.agentlab.AgentLabResponse;
import ai.demo.client.agentlab.AgentLabSource;
import ai.demo.client.agentlab.AgentLabUsage;
import ai.demo.exception.AgentServiceException;
import ai.demo.model.chat.ChatMessage;
import ai.demo.model.chat.Conversation;
import ai.demo.model.chat.WebSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RemoteAgentTest {

  private static final AgentLabResponse REPLY =
      new AgentLabResponse(
          "1 EUR is 24.30 CZK.",
          List.of(new AgentLabSource("CNB rates", "https://www.cnb.cz/rates")),
          new AgentLabUsage(2, 1, 1, 900, 120));

  private AgentLabClient client;
  private RemoteAgent agent;

  @BeforeEach
  void setUp() {
    client = mock(AgentLabClient.class);
    agent = new RemoteAgent(client);
  }

  @Test
  void shouldSendLatestMessageWithEarlierTurnsAsHistory() {
    when(client.chat(any())).thenReturn(REPLY);

    agent.execute(
        conversation(
            ChatMessage.user("EUR to CZK?"),
            ChatMessage.assistant("24.30"),
            ChatMessage.user("And today?")));

    AgentLabRequest request = sentRequest();
    assertEquals("And today?", request.message());
    assertEquals(
        List.of(AgentLabMessage.user("EUR to CZK?"), AgentLabMessage.assistant("24.30")),
        request.history());
  }

  @Test
  void shouldMapReplyUsageAndWebSources() {
    when(client.chat(any())).thenReturn(REPLY);

    AgentResult result = agent.execute(conversation(ChatMessage.user("EUR to CZK?")));

    assertEquals("1 EUR is 24.30 CZK.", result.answer());
    assertEquals(RemoteAgent.MODEL, result.model());
    assertEquals(new TokenUsage(900, 120), result.tokenUsage());
    assertEquals(
        List.of(new WebSource("CNB rates", "https://www.cnb.cz/rates")), result.webSources());
  }

  @Test
  void shouldEmitTheAnswerAsOneContentEvent() {
    when(client.chat(any())).thenReturn(REPLY);
    List<AgentEvent> events = new ArrayList<>();

    agent.execute(conversation(ChatMessage.user("EUR to CZK?")), events::add);

    assertEquals(List.of(new ContentEvent("1 EUR is 24.30 CZK.")), events);
  }

  @Test
  void shouldPropagateAgentServiceFailure() {
    Conversation conversation = conversation(ChatMessage.user("Hi"));
    when(client.chat(any())).thenThrow(new AgentServiceException("Agent lab returned HTTP 502"));

    assertThrows(AgentServiceException.class, () -> agent.execute(conversation));
  }

  @Test
  void shouldRejectTooLongLatestMessageWithoutCallingTheService() {
    Conversation conversation =
        conversation(ChatMessage.user("x".repeat(RemoteAgent.MAX_MESSAGE_CHARACTERS + 1)));

    assertThrows(IllegalArgumentException.class, () -> agent.execute(conversation));
    verifyNoInteractions(client);
  }

  @Test
  void shouldKeepOnlyTheNewestHistoryMessages() {
    List<ChatMessage> earlier = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      earlier.add(i % 2 == 0 ? ChatMessage.user("q" + i) : ChatMessage.assistant("a" + i));
    }

    List<AgentLabMessage> history = RemoteAgent.history(earlier);

    assertEquals(RemoteAgent.MAX_HISTORY_MESSAGES, history.size());
    assertEquals("q10", history.getFirst().content());
    assertEquals("a29", history.getLast().content());
  }

  @Test
  void shouldShortenLongHistoryMessages() {
    List<AgentLabMessage> history =
        RemoteAgent.history(List.of(ChatMessage.assistant("y".repeat(6000))));

    assertEquals(RemoteAgent.MAX_MESSAGE_CHARACTERS, history.getFirst().content().length());
  }

  @Test
  void shouldNotSplitSurrogatePairWhenShortening() {
    String content = "y".repeat(RemoteAgent.MAX_MESSAGE_CHARACTERS - 1) + "😀tail";

    String shortened =
        RemoteAgent.history(List.of(ChatMessage.assistant(content))).getFirst().content();

    assertEquals(RemoteAgent.MAX_MESSAGE_CHARACTERS - 1, shortened.length());
  }

  @Test
  void shouldKeepHistoryWithinTotalCharacterLimit() {
    List<ChatMessage> earlier = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      earlier.add(ChatMessage.user(i + "z".repeat(3999)));
    }

    List<AgentLabMessage> history = RemoteAgent.history(earlier);

    int characters = history.stream().mapToInt(message -> message.content().length()).sum();
    assertEquals(4, history.size());
    assertTrue(characters <= RemoteAgent.MAX_HISTORY_CHARACTERS);
    assertTrue(history.getLast().content().startsWith("5"));
  }

  private AgentLabRequest sentRequest() {
    ArgumentCaptor<AgentLabRequest> captor = ArgumentCaptor.forClass(AgentLabRequest.class);
    verify(client).chat(captor.capture());
    return captor.getValue();
  }

  private static Conversation conversation(ChatMessage... messages) {
    return new Conversation(List.of(messages));
  }
}
