package ai.demo.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration record.
 * Contains all configuration parameters for the AI Demo application.
 */
public record AppConfig(
        String baseUrl,
        String model,
        double temperature,
        int numPredict,
        int numCtx
) {
    public AppConfig {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (numPredict < 1) {
            throw new IllegalArgumentException("numPredict must be positive");
        }
        if (numCtx < 1) {
            throw new IllegalArgumentException("numCtx must be positive");
        }
    }

    /**
     * Loads configuration from application.properties file.
     *
     * @return the loaded AppConfig
     * @throws IOException if the properties file cannot be read
     * @throws IllegalStateException if required properties are missing or invalid
     */
    public static AppConfig load() throws IOException {

        Properties properties = new Properties();

        try (InputStream is =
                     AppConfig.class.getClassLoader()
                             .getResourceAsStream("application.properties")) {

            if (is == null) {
                throw new IllegalStateException("application.properties not found");
            }

            properties.load(is);
        }

        final String baseUrl = getRequiredProperty(properties, "ollama.baseUrl");
        final String model = getRequiredProperty(properties, "ollama.model");
        final double temperature = parseDouble(properties, "ollama.temperature");
        final int numPredict = parseInt(properties, "ollama.numPredict");
        final int numCtx = parseInt(properties, "ollama.numCtx");

        return new AppConfig(baseUrl, model, temperature, numPredict, numCtx);
    }

    private static String getRequiredProperty(Properties properties, String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required property '" + key + "' is missing or empty");
        }
        return value;
    }

    private static double parseDouble(Properties properties, String key) {
        final String value = getRequiredProperty(properties, key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Property '" + key + "' must be a valid number, got: " + value);
        }
    }

    private static int parseInt(Properties properties, String key) {
        final String value = getRequiredProperty(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Property '" + key + "' must be a valid integer, got: " + value);
        }
    }
}