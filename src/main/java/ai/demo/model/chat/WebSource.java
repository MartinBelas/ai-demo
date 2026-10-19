package ai.demo.model.chat;

/** A web page found by the agent service, shown to the user as a link (title and URL). */
public record WebSource(String title, String url) {

  public WebSource {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("url must not be blank");
    }
  }
}
