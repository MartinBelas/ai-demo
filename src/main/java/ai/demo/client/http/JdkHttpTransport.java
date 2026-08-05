package ai.demo.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JdkHttpTransport implements HttpTransport {

  private final HttpClient httpClient;

  public JdkHttpTransport(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Override
  public HttpResponse<InputStream> sendStreaming(HttpRequest request)
      throws IOException, InterruptedException {

    return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
  }
}
