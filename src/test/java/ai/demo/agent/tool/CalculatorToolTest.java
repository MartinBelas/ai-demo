package ai.demo.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CalculatorToolTest {

  private final CalculatorTool calculator = new CalculatorTool();

  @Test
  void shouldHaveCorrectName() {
    assertEquals("calculator", calculator.name());
  }

  @Test
  void shouldHaveDescription() {
    assertEquals("Calculates mathematical expressions.", calculator.description());
  }

  @ParameterizedTest
  @CsvSource({
    "'2+2', '2 + 2 = 4'",
    "'10 - 3', '10 - 3 = 7'",
    "'125*37', '125 * 37 = 4625'",
    "'20 / 4', '20 / 4 = 5'",
    "'2.5*4', '2.5 * 4 = 10'",
    "'2+3*4', '2 + 3 * 4 = 14'",
    "'(2+3)*4', '(2 + 3) * 4 = 20'"
  })
  void shouldCalculateExpression(String expression, String expected) {
    ToolResult result = calculator.execute(expression);

    assertTrue(result.success());
    assertEquals(expected, result.content());
  }

  @ParameterizedTest
  @ValueSource(strings = {"hello", "10 / 0", " "})
  void shouldRejectInvalidExpression(String expression) {
    ToolResult result = calculator.execute(expression);

    assertFalse(result.success());
  }
}
