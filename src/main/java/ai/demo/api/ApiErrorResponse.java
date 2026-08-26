package ai.demo.api;

record ApiErrorResponse(String code, String message) {

  static ApiErrorResponse of(String code, String message) {
    return new ApiErrorResponse(code, message);
  }
}
