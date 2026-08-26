package ai.demo.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

record ApiErrorResponse(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ApiErrorDetail> details) {

  ApiErrorResponse {
    details = details == null ? List.of() : List.copyOf(details);
  }

  static ApiErrorResponse of(String code, String message) {
    return new ApiErrorResponse(code, message, List.of());
  }

  static ApiErrorResponse withDetail(
      String code, String message, String field, String detailMessage) {
    return new ApiErrorResponse(code, message, List.of(new ApiErrorDetail(field, detailMessage)));
  }
}
