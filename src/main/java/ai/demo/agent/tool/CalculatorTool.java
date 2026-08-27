package ai.demo.agent.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;

public class CalculatorTool implements Tool {

  private static final Pattern OPERATOR = Pattern.compile("\\s*([+\\-*/])\\s*");
  private static final Pattern NUMERIC_EXPRESSION = Pattern.compile("[0-9.()+\\-*/\\s]+");

  private final LocalizedArithmeticExpressionResolver localizedResolver =
      new LocalizedArithmeticExpressionResolver();

  @Override
  public String name() {
    return "calculator";
  }

  @Override
  public String description() {
    return "Calculates mathematical expressions.";
  }

  @Override
  public Optional<String> resolveInput(String request) {
    if (request == null || request.isBlank()) {
      return Optional.empty();
    }

    String candidate = request.strip();
    if (NUMERIC_EXPRESSION.matcher(candidate).matches()) {
      return Optional.of(candidate);
    }

    return localizedResolver.resolve(candidate);
  }

  @Override
  public boolean resultIsFinal() {
    return true;
  }

  @Override
  public ToolResult execute(String input) {

    if (input == null || input.isBlank()) {
      return ToolResult.failure("Expression must not be empty.");
    }

    try {
      BigDecimal result = new Parser(input).parse();

      return ToolResult.success(formatExpression(input) + " = " + format(result));

    } catch (IllegalArgumentException | ArithmeticException e) {
      return ToolResult.failure(e.getMessage());
    }
  }

  private String format(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private String formatExpression(String expression) {
    return OPERATOR.matcher(expression.strip()).replaceAll(" $1 ");
  }

  private static class Parser {

    private final String input;
    private int position;

    private Parser(String input) {
      this.input = input;
    }

    private BigDecimal parse() {
      BigDecimal result = parseExpression();

      skipWhitespace();

      if (position != input.length()) {
        throw new IllegalArgumentException("Invalid expression.");
      }

      return result;
    }

    private BigDecimal parseExpression() {
      BigDecimal result = parseTerm();

      while (true) {
        skipWhitespace();

        if (match('+')) {
          result = result.add(parseTerm());
        } else if (match('-')) {
          result = result.subtract(parseTerm());
        } else {
          return result;
        }
      }
    }

    private BigDecimal parseTerm() {
      BigDecimal result = parseFactor();

      while (true) {
        skipWhitespace();

        if (match('*')) {
          result = result.multiply(parseFactor());
        } else if (match('/')) {
          BigDecimal divisor = parseFactor();

          if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero.");
          }

          result = result.divide(divisor, 10, RoundingMode.HALF_UP);
        } else {
          return result;
        }
      }
    }

    private BigDecimal parseFactor() {
      skipWhitespace();

      if (match('(')) {
        BigDecimal result = parseExpression();

        skipWhitespace();

        if (!match(')')) {
          throw new IllegalArgumentException("Missing closing parenthesis.");
        }

        return result;
      }

      return parseNumber();
    }

    private BigDecimal parseNumber() {
      skipWhitespace();

      int start = position;

      while (position < input.length()) {
        char c = input.charAt(position);

        if (!Character.isDigit(c) && c != '.') {
          break;
        }

        position++;
      }

      if (start == position) {
        throw new IllegalArgumentException("Expected number.");
      }

      try {
        return new BigDecimal(input.substring(start, position));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid number.", e);
      }
    }

    private boolean match(char expected) {
      if (position < input.length() && input.charAt(position) == expected) {
        position++;
        return true;
      }

      return false;
    }

    private void skipWhitespace() {
      while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
        position++;
      }
    }
  }
}
