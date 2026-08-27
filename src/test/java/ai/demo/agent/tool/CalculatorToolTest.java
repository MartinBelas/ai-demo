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

  @Test
  void shouldReturnACompleteFinalResult() {
    assertTrue(calculator.resultIsFinal());
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

  @ParameterizedTest(name = "{0} resolves to {1}")
  @CsvSource({
    "'3+3', '3+3'",
    "' (2 + 3) * 4 ', '(2 + 3) * 4'",
    "'dva plus sedm', '2 + 7'",
    "'dva a dva', '2 + 2'",
    "'TŘI krát čtyři', '3 * 4'",
    "'deset děleno dvě', '10 / 2'",
    "'seven plus five', '7 + 5'",
    "'two and two', '2 + 2'",
    "'twenty one divided by three', '21 / 3'",
    "'sieben plus fünf', '7 + 5'",
    "'zwei und drei', '2 + 3'",
    "'sedem plus päť', '7 + 5'",
    "'dva a tri', '2 + 3'",
    "'siedem plus pięć', '7 + 5'",
    "'dwa i trzy', '2 + 3'",
    "'seven plu five', '7 + 5'"
  })
  void shouldResolveUnambiguousCalculatorRequests(String request, String expectedInput) {
    String resolved =
        calculator
            .resolveInput(request)
            .orElseThrow(() -> new AssertionError("Request was not resolved: " + request));
    assertEquals(expectedInput, resolved);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "What is Spring?",
        "3 apples + 2 apples",
        "seven apples plus five",
        "one spring two"
      })
  void shouldNotResolveRequestsThatAreNotSupportedExpressions(String request) {
    assertTrue(calculator.resolveInput(request).isEmpty());
  }
}
