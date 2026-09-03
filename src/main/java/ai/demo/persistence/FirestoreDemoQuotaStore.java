package ai.demo.persistence;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.DemoLimitException;
import ai.demo.exception.PersistenceException;
import ai.demo.model.app.AppMetrics;
import ai.demo.model.app.AppOutcome;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.SetOptions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Firestore-backed atomic request and token counters for Cloud Run. */
public final class FirestoreDemoQuotaStore implements DemoQuotaStore {

  private static final String REQUESTS_FIELD = "requests";
  private static final String UPDATED_AT_FIELD = "updatedAt";
  private static final String TOKENS_FIELD = "tokens";
  private static final String DAILY_USAGE_COLLECTION = "demoDailyUsage";

  private final DemoLimitsConfig limits;
  private final Firestore firestore;
  private final Clock clock;
  private final AtomicInteger activeStreams = new AtomicInteger();

  public FirestoreDemoQuotaStore(DemoLimitsConfig limits) {
    this(limits, createFirestore(limits), Clock.systemUTC());
  }

  FirestoreDemoQuotaStore(DemoLimitsConfig limits, Firestore firestore, Clock clock) {
    this.limits = limits;
    this.firestore = firestore;
    this.clock = clock;
  }

  @Override
  public Reservation reserve(String clientHash, boolean streaming) {
    if (streaming && activeStreams.incrementAndGet() > limits.concurrentStreams()) {
      activeStreams.decrementAndGet();
      throw new DemoLimitException();
    }
    String day = LocalDate.now(clock).format(DateTimeFormatter.ISO_DATE);
    String hour =
        day + "T" + String.format("%02d", clock.instant().atZone(ZoneOffset.UTC).getHour());
    DocumentReference daily = firestore.collection(DAILY_USAGE_COLLECTION).document(day);
    DocumentReference client =
        firestore.collection("demoHourlyClients").document(hour + "_" + clientHash);
    try {
      Boolean accepted =
          await(
              firestore.runTransaction(
                  transaction -> {
                    DocumentSnapshot dailySnapshot = transaction.get(daily).get();
                    DocumentSnapshot clientSnapshot = transaction.get(client).get();
                    long dailyRequests = count(dailySnapshot, REQUESTS_FIELD);
                    long clientRequests = count(clientSnapshot, REQUESTS_FIELD);
                    if (dailyRequests >= limits.dailyRequests()
                        || clientRequests >= limits.hourlyRequestsPerIp()) return false;
                    transaction.set(
                        daily,
                        Map.of(
                            REQUESTS_FIELD,
                            dailyRequests + 1,
                            UPDATED_AT_FIELD,
                            clock.instant().toString()),
                        SetOptions.merge());
                    transaction.set(
                        client,
                        Map.of(
                            REQUESTS_FIELD,
                            clientRequests + 1,
                            UPDATED_AT_FIELD,
                            clock.instant().toString()),
                        SetOptions.merge());
                    return true;
                  }));
      if (!Boolean.TRUE.equals(accepted)) throw new DemoLimitException();
      return new Reservation(day, clientHash, streaming);
    } catch (DemoLimitException e) {
      releaseStream(streaming);
      throw e;
    } catch (RuntimeException e) {
      releaseStream(streaming);
      throw new DemoLimitException(true, e);
    }
  }

  @Override
  public void recordUsage(Reservation reservation, int totalTokens) {
    DocumentReference daily =
        firestore.collection(DAILY_USAGE_COLLECTION).document(reservation.period());
    try {
      await(
          firestore.runTransaction(
              transaction -> {
                DocumentSnapshot snapshot = transaction.get(daily).get();
                long tokens = count(snapshot, TOKENS_FIELD);
                transaction.set(
                    daily,
                    Map.of(
                        TOKENS_FIELD,
                        tokens + Math.max(0, totalTokens),
                        UPDATED_AT_FIELD,
                        clock.instant().toString()),
                    SetOptions.merge());
                return null;
              }));
    } catch (RuntimeException e) {
      throw new DemoLimitException(true, e);
    }
  }

  @Override
  public void release(Reservation reservation) {
    releaseStream(reservation.streaming());
  }

  @Override
  public void recordOutcome(Reservation reservation, AppOutcome outcome, long durationMs) {
    String field =
        switch (outcome) {
          case COMPLETED -> "completed";
          case FAILED -> "failed";
          case DISCONNECTED -> "disconnected";
        };
    try {
      Map<String, Object> updates = new java.util.HashMap<>();
      updates.put(field, FieldValue.increment(1));
      if (outcome == AppOutcome.COMPLETED) {
        updates.put("completedDurationMs", FieldValue.increment(Math.max(0, durationMs)));
      }
      await(
          firestore
              .collection(DAILY_USAGE_COLLECTION)
              .document(reservation.period())
              .set(updates, SetOptions.merge()));
    } catch (RuntimeException e) {
      throw new PersistenceException("Unable to record demo metrics", e);
    }
  }

  @Override
  public AppMetrics snapshot(String period) {
    try {
      DocumentSnapshot snapshot =
          await(firestore.collection(DAILY_USAGE_COLLECTION).document(period).get());
      return new AppMetrics(
          count(snapshot, REQUESTS_FIELD),
          count(snapshot, TOKENS_FIELD),
          count(snapshot, "completed"),
          count(snapshot, "failed"),
          count(snapshot, "disconnected"),
          count(snapshot, "completedDurationMs"));
    } catch (RuntimeException e) {
      throw new PersistenceException("Unable to read demo metrics", e);
    }
  }

  @Override
  public int activeStreams() {
    return activeStreams.get();
  }

  @Override
  public void close() {
    try {
      firestore.close();
    } catch (Exception e) {
      throw new DemoLimitException(true, e);
    }
  }

  private void releaseStream(boolean streaming) {
    if (streaming) activeStreams.updateAndGet(value -> Math.max(0, value - 1));
  }

  private static long count(DocumentSnapshot snapshot, String field) {
    Long value = snapshot.getLong(field);
    return value == null ? 0 : value;
  }

  private static <T> T await(ApiFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DemoLimitException(true, e);
    } catch (ExecutionException e) {
      throw new DemoLimitException(true, e.getCause());
    }
  }

  private static Firestore createFirestore(DemoLimitsConfig limits) {
    FirestoreOptions.Builder builder = FirestoreOptions.newBuilder();
    if (limits.firestoreProjectId() != null && !limits.firestoreProjectId().isBlank()) {
      builder.setProjectId(limits.firestoreProjectId());
    }
    if (!"(default)".equals(limits.firestoreDatabaseId()))
      builder.setDatabaseId(limits.firestoreDatabaseId());
    return builder.build().getService();
  }
}
