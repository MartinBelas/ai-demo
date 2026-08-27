package ai.demo.api;

record SseToolEvent(String name, String status) {

  static SseToolEvent running(String name) {
    return new SseToolEvent(name, "RUNNING");
  }

  static SseToolEvent completed(String name) {
    return new SseToolEvent(name, "COMPLETED");
  }
}
