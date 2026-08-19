package ai.demo.agent.tool;

import java.util.List;

public class ToolDescriptionFormatter {

  public String format(List<Tool> tools) {
    return tools.stream()
        .map(tool -> tool.name() + ": " + tool.description())
        .reduce((first, second) -> first + "\n" + second)
        .orElse("No tools available.");
  }
}
