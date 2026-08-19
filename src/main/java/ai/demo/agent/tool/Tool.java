package ai.demo.agent.tool;

public interface Tool {

  String name();

  String description();

  ToolResult execute(String input);
}
