package ai.demo.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.demo.config.DemoLimitsConfig;
import ai.demo.exception.DemoLimitException;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FirestoreDemoQuotaStoreTest {

  private final DemoLimitsConfig limits =
      new DemoLimitsConfig(
          true, true, "project", "(default)", "DEMO_IP_HASH_SALT", 2, 1, 1, 100, 10, 5, 50);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void shouldReserveRequestInOneFirestoreTransaction() {
    Firestore firestore = mock(Firestore.class);
    Transaction transaction = mock(Transaction.class);
    DocumentReference daily = document(firestore, "demoDailyUsage");
    DocumentReference client = document(firestore, "demoHourlyClients");
    DocumentSnapshot dailySnapshot = snapshotWithCount(0);
    DocumentSnapshot clientSnapshot = snapshotWithCount(0);
    when(transaction.get(daily)).thenReturn(ApiFutures.immediateFuture(dailySnapshot));
    when(transaction.get(client)).thenReturn(ApiFutures.immediateFuture(clientSnapshot));
    executeTransactions(firestore, transaction);

    FirestoreDemoQuotaStore store = new FirestoreDemoQuotaStore(limits, firestore, clock);
    store.reserve("client-hash", false);

    verify(transaction, times(2)).set(any(DocumentReference.class), any(), any());
  }

  @Test
  void shouldFailClosedWhenFirestoreIsUnavailable() {
    Firestore firestore = mock(Firestore.class);
    document(firestore, "demoDailyUsage");
    document(firestore, "demoHourlyClients");
    when(firestore.runTransaction(any()))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("offline")));
    FirestoreDemoQuotaStore store = new FirestoreDemoQuotaStore(limits, firestore, clock);

    DemoLimitException exception =
        assertThrows(DemoLimitException.class, () -> store.reserve("client-hash", false));

    org.junit.jupiter.api.Assertions.assertTrue(exception.unavailable());
  }

  private DocumentReference document(Firestore firestore, String collectionName) {
    CollectionReference collection = mock(CollectionReference.class);
    DocumentReference document = mock(DocumentReference.class);
    when(firestore.collection(collectionName)).thenReturn(collection);
    when(collection.document(any())).thenReturn(document);
    return document;
  }

  private DocumentSnapshot snapshotWithCount(long count) {
    DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
    when(snapshot.getLong("requests")).thenReturn(count);
    return snapshot;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void executeTransactions(Firestore firestore, Transaction transaction) {
    doAnswer(
            invocation -> {
              Transaction.Function function = invocation.getArgument(0);
              return ApiFutures.immediateFuture(function.updateCallback(transaction));
            })
        .when(firestore)
        .runTransaction(any());
  }
}
