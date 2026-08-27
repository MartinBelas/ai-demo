package ai.demo.agent.tool;

import java.util.Optional;

public interface Tool {

  String name();

  String description();

  default Optional<String> resolveInput(String request) {
    return Optional.empty();
  }

  default boolean resultIsFinal() {
    return false;
  }

  ToolResult execute(String input);
}
