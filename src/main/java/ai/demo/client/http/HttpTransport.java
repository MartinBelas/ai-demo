package ai.demo.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface HttpTransport {

  HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;

  HttpResponse<InputStream> sendStreaming(HttpRequest request)
      throws IOException, InterruptedException;
}
