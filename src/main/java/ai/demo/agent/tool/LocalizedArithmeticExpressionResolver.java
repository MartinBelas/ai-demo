package ai.demo.agent.tool;

import com.ibm.icu.text.RuleBasedNumberFormat;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocalizedArithmeticExpressionResolver {

  private static final List<LanguageProfile> LANGUAGES =
      List.of(
          profile(
              "cs",
              operator("+", "a", "plus"),
              operator("-", "minus", "mínus"),
              operator("*", "krát"),
              operator("/", "děleno")),
          profile(
              "en",
              operator("+", "and", "plus"),
              operator("-", "minus"),
              operator("*", "times"),
              operator("/", "divided by")),
          profile(
              "de",
              operator("+", "und", "plus"),
              operator("-", "minus"),
              operator("*", "mal"),
              operator("/", "geteilt durch")),
          profile(
              "sk",
              operator("+", "a", "plus"),
              operator("-", "mínus"),
              operator("*", "krát"),
              operator("/", "delené")),
          profile(
              "pl",
              operator("+", "i", "plus"),
              operator("-", "minus"),
              operator("*", "razy"),
              operator("/", "podzielone przez")));

  Optional<String> resolve(String request) {
    String normalized = request.toLowerCase(Locale.ROOT).strip();
    for (LanguageProfile language : LANGUAGES) {
      Optional<String> expression = language.resolveExact(normalized);
      if (expression.isPresent()) {
        return expression;
      }
    }
    for (LanguageProfile language : LANGUAGES) {
      Optional<String> expression = language.resolveWithOperatorTypo(normalized);
      if (expression.isPresent()) {
        return expression;
      }
    }
    return Optional.empty();
  }

  private static LanguageProfile profile(String languageTag, OperatorAliases... operators) {
    return new LanguageProfile(Locale.forLanguageTag(languageTag), operators);
  }

  private static OperatorAliases operator(String symbol, String... aliases) {
    return new OperatorAliases(symbol, List.of(aliases));
  }

  private record LanguageProfile(
      RuleBasedNumberFormat numberFormat, List<LocalizedOperator> operators) {

    private LanguageProfile(Locale locale, OperatorAliases... operators) {
      this(
          new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT),
          Arrays.stream(operators)
              .flatMap(
                  operator ->
                      operator.aliases().stream()
                          .map(alias -> LocalizedOperator.create(alias, operator.symbol())))
              .toList());
      numberFormat.setLenientParseMode(true);
    }

    private Optional<String> resolveExact(String request) {
      for (LocalizedOperator operator : operators) {
        Optional<String> expression = resolveExact(request, operator);
        if (expression.isPresent()) {
          return expression;
        }
      }
      return Optional.empty();
    }

    private Optional<String> resolveWithOperatorTypo(String request) {
      for (LocalizedOperator operator : operators) {
        Optional<String> expression = resolveWithOperatorTypo(request, operator);
        if (expression.isPresent()) {
          return expression;
        }
      }
      return Optional.empty();
    }

    private Optional<String> resolveExact(String request, LocalizedOperator operator) {
      Matcher matcher = operator.pattern().matcher(request);
      if (!matcher.matches()) {
        return Optional.empty();
      }
      return createExpression(matcher.group(1), matcher.group(2), operator.symbol());
    }

    private Optional<String> resolveWithOperatorTypo(String request, LocalizedOperator operator) {
      if (operator.name().contains(" ")) {
        return Optional.empty();
      }
      String[] words = request.split("\\s+");
      for (int index = 1; index < words.length - 1; index++) {
        if (isSingleCharacterTypo(words[index], operator.name())) {
          String left = String.join(" ", Arrays.copyOfRange(words, 0, index));
          String right = String.join(" ", Arrays.copyOfRange(words, index + 1, words.length));
          Optional<String> expression = createExpression(left, right, operator.symbol());
          if (expression.isPresent()) {
            return expression;
          }
        }
      }
      return Optional.empty();
    }

    private Optional<String> createExpression(String leftValue, String rightValue, String symbol) {
      Optional<BigDecimal> left = parseNumber(leftValue);
      Optional<BigDecimal> right = parseNumber(rightValue);
      if (left.isEmpty() || right.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          format(left.orElseThrow()) + " " + symbol + " " + format(right.orElseThrow()));
    }

    private Optional<BigDecimal> parseNumber(String value) {
      String candidate = value.strip();
      ParsePosition position = new ParsePosition(0);
      Number parsed = numberFormat.parse(candidate, position);
      if (parsed == null || position.getIndex() != candidate.length()) {
        return Optional.empty();
      }
      return Optional.of(new BigDecimal(parsed.toString()));
    }

    private String format(BigDecimal value) {
      return value.stripTrailingZeros().toPlainString();
    }

    private static boolean isSingleCharacterTypo(String candidate, String expected) {
      if (candidate.equals(expected) || Math.abs(candidate.length() - expected.length()) > 1) {
        return false;
      }
      int candidateIndex = 0;
      int expectedIndex = 0;
      int differences = 0;
      while (candidateIndex < candidate.length() && expectedIndex < expected.length()) {
        if (candidate.charAt(candidateIndex) == expected.charAt(expectedIndex)) {
          candidateIndex++;
          expectedIndex++;
          continue;
        }
        differences++;
        if (differences > 1) {
          return false;
        }
        if (candidate.length() > expected.length()) {
          candidateIndex++;
        } else if (candidate.length() < expected.length()) {
          expectedIndex++;
        } else {
          candidateIndex++;
          expectedIndex++;
        }
      }
      return differences + candidate.length() - candidateIndex + expected.length() - expectedIndex
          <= 1;
    }
  }

  private record OperatorAliases(String symbol, List<String> aliases) {}

  private record LocalizedOperator(String name, Pattern pattern, String symbol) {

    private static LocalizedOperator create(String name, String symbol) {
      Pattern pattern = Pattern.compile("(.+?)\\s+" + Pattern.quote(name) + "\\s+(.+)");
      return new LocalizedOperator(name, pattern, symbol);
    }
  }
}
