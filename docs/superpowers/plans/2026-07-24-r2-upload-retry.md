# R2 Upload Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make TurboFlow translated-image uploads retry transient R2 failures without creating database records for missing objects or allowing retries to exceed the HTTP request budget.

**Architecture:** Add a byte-array upload operation with bounded retries at the `IS3Service` boundary, because a byte array can be replayed safely for each PUT attempt. Use the existing Resilience4j Retry dependency with exponential backoff, retry only transient failures, and throw a typed exception after exhaustion. `MultimediaFileService` will persist metadata only after the upload succeeds; the existing TurboFlow failure callback will then requeue the subtask if all object-storage attempts fail.

**Tech Stack:** Java 21, Spring Boot 3, AWS SDK for Java v2 S3 client, Resilience4j Retry 2.2, JUnit 5, Mockito, Gradle.

---

## File map

- Modify `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/properties/S3Property.java`: bind retry and timeout settings with safe defaults.
- Modify `v7-shop-services/v7-shop-entrance/src/main/resources/application.yml.example`: document environment overrides.
- Modify `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IS3Service.java`: expose a replayable, fail-fast translated-image upload operation.
- Create `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/ObjectStorageUploadException.java`: typed terminal exception for an exhausted upload.
- Modify `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/S3Service.java`: configure SDK timeouts, classify retryable failures, retry bounded PUT attempts, and stop swallowing errors.
- Modify `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/MultimediaFileService.java`: call the retrying byte-array operation and persist only after success.
- Create `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/S3ServiceTest.java`: cover recovery, exhaustion, and non-retryable errors.
- Create `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/MultimediaFileServiceTranslatedImageTest.java`: prove failed uploads never create metadata.
- Modify `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/task/provider/TurboFlowBridgeProviderTest.java`: prove exhausted upload failures enter the existing retryable subtask failure path.

### Task 1: Add bounded retry configuration

**Files:**
- Modify: `v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/properties/S3Property.java`
- Modify: `v7-shop-services/v7-shop-entrance/src/main/resources/application.yml.example:212-217`

- [ ] **Step 1: Add configuration-binding defaults**

Add these fields to `S3Property`:

```java
private int uploadMaxAttempts = 3;
private long uploadInitialBackoffMillis = 300;
private long uploadMaxBackoffMillis = 1500;
private long uploadAttemptTimeoutSeconds = 15;
private long uploadTotalTimeoutSeconds = 40;
```

`uploadMaxAttempts` includes the initial attempt, so the default performs at most two retries. The worst-case SDK call budget remains below typical 60-second proxy timeouts.

- [ ] **Step 2: Document environment overrides**

Add under `application.s3`:

```yaml
    upload-max-attempts: ${S3_UPLOAD_MAX_ATTEMPTS:3}
    upload-initial-backoff-millis: ${S3_UPLOAD_INITIAL_BACKOFF_MILLIS:300}
    upload-max-backoff-millis: ${S3_UPLOAD_MAX_BACKOFF_MILLIS:1500}
    upload-attempt-timeout-seconds: ${S3_UPLOAD_ATTEMPT_TIMEOUT_SECONDS:15}
    upload-total-timeout-seconds: ${S3_UPLOAD_TOTAL_TIMEOUT_SECONDS:40}
```

- [ ] **Step 3: Compile the bound property**

Run:

```powershell
.\gradlew.bat :v7-shop-dao:compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add v7-shop-services/v7-shop-dao/src/main/java/cn/v7soft/dao/properties/S3Property.java v7-shop-services/v7-shop-entrance/src/main/resources/application.yml.example
git commit -m "config: add object storage upload retry limits"
```

### Task 2: Define fail-fast upload semantics

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IS3Service.java`
- Create: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/ObjectStorageUploadException.java`

- [ ] **Step 1: Add the replayable interface operation**

Add this method to `IS3Service` without changing the legacy multipart-upload method:

```java
/**
 * Upload replayable data, retrying transient object-storage failures.
 * Throws when the object was not durably stored.
 */
void uploadWithRetry(byte[] data, String key, String contentType);
```

Keeping the existing `boolean upload(InputStream, ...)` limits this change to translated images and avoids changing unrelated upload behavior in the same patch.

- [ ] **Step 2: Create the typed terminal exception**

Create:

```java
package cn.v7soft.admin.service.impl;

public class ObjectStorageUploadException extends RuntimeException {

    public ObjectStorageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: Compile the interface**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:compileJava
```

Expected: compilation fails because `S3Service` does not yet implement `uploadWithRetry`; this is the intentional red state.

### Task 3: Implement retry classification and bounded PUT attempts

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/S3Service.java`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/S3ServiceTest.java`

- [ ] **Step 1: Write failing retry tests**

Refactor `S3Service` to support a package-private test constructor accepting `S3Client`; then create tests with a mocked client:

```java
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    private S3Property property;

    @BeforeEach
    void setUp() {
        property = new S3Property();
        property.setBucketName("bucket");
        property.setUploadMaxAttempts(3);
        property.setUploadInitialBackoffMillis(1);
        property.setUploadMaxBackoffMillis(1);
    }

    @Test
    void retriesTransientFailureAndThenSucceeds() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection reset"))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());

        new S3Service(property, s3Client)
                .uploadWithRetry(new byte[] {1, 2, 3}, "translated/a.png", "image/png");

        verify(s3Client, times(2))
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void throwsAfterTransientAttemptsAreExhausted() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("timeout"));

        assertThrows(ObjectStorageUploadException.class,
                () -> new S3Service(property, s3Client)
                        .uploadWithRetry(new byte[] {1}, "translated/a.png", "image/png"));

        verify(s3Client, times(3))
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void doesNotRetryPermanentR2Rejection() {
        S3Exception forbidden = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .message("signature mismatch")
                .build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(forbidden);

        assertThrows(ObjectStorageUploadException.class,
                () -> new S3Service(property, s3Client)
                        .uploadWithRetry(new byte[] {1}, "translated/a.png", "image/png"));

        verify(s3Client, times(1))
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
```

- [ ] **Step 2: Run the focused tests and verify red**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.S3ServiceTest"
```

Expected: FAIL because the constructor and retry method are not implemented.

- [ ] **Step 3: Configure SDK request deadlines**

In the production constructor, add:

```java
.overrideConfiguration(builder -> builder
        .apiCallAttemptTimeout(Duration.ofSeconds(
                s3Property.getUploadAttemptTimeoutSeconds()))
        .apiCallTimeout(Duration.ofSeconds(
                s3Property.getUploadTotalTimeoutSeconds())))
```

Keep SDK retry disabled for this client operation or configure it to a single attempt so the application retry count is the only retry budget. This prevents an accidental `3 application attempts × 3 SDK attempts`.

- [ ] **Step 4: Implement transient-failure classification**

Use this predicate:

```java
private boolean isRetryableUploadFailure(Throwable throwable) {
    if (throwable instanceof S3Exception s3Exception) {
        int status = s3Exception.statusCode();
        return status == 408 || status == 429 || status >= 500;
    }
    return throwable instanceof SdkClientException;
}
```

Do not retry 400, 401, 403, or 404 because credentials, signatures, endpoints, and bucket configuration do not recover through immediate retries.

- [ ] **Step 5: Implement one idempotent PUT with Resilience4j**

Build a Retry using:

```java
IntervalFunction intervalFunction = IntervalFunction.ofExponentialBackoff(
        s3Property.getUploadInitialBackoffMillis(),
        2.0,
        s3Property.getUploadMaxBackoffMillis());

RetryConfig retryConfig = RetryConfig.custom()
        .maxAttempts(s3Property.getUploadMaxAttempts())
        .intervalFunction(intervalFunction)
        .retryOnException(this::isRetryableUploadFailure)
        .build();
```

`uploadWithRetry` must construct the `PutObjectRequest` once, use `RequestBody.fromBytes(data)` for every attempt, log retry number/key/exception class without logging credentials, and wrap the final exception:

```java
try {
    Retry.decorateRunnable(retry, () ->
            s3Client.putObject(request, RequestBody.fromBytes(data))).run();
} catch (RuntimeException ex) {
    throw new ObjectStorageUploadException(
            "object storage upload failed after "
                    + s3Property.getUploadMaxAttempts()
                    + " attempts: key=" + key,
            ex);
}
```

- [ ] **Step 6: Run the focused tests**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.S3ServiceTest"
```

Expected: all three tests PASS.

- [ ] **Step 7: Commit**

```powershell
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/IS3Service.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/ObjectStorageUploadException.java v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/S3Service.java v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/S3ServiceTest.java
git commit -m "fix: retry transient R2 upload failures"
```

### Task 4: Prevent metadata from being saved after upload failure

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/MultimediaFileService.java:164-184`
- Create: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/MultimediaFileServiceTranslatedImageTest.java`

- [ ] **Step 1: Write the failing persistence-order test**

Mock `IS3Service` and the multimedia repository/service dependencies. Configure:

```java
doThrow(new ObjectStorageUploadException(
        "object storage upload failed", new IOException("timeout")))
        .when(s3Service)
        .uploadWithRetry(any(byte[].class), anyString(), eq("image/png"));
```

Assert:

```java
assertThrows(ObjectStorageUploadException.class,
        () -> service.saveTranslatedImage(validPngBytes, "png", owner));

verify(multimediaFilePersistence, never()).saveAndFlush(any());
```

Use a valid 1×1 PNG fixture so the test exercises the actual translated-image path.

- [ ] **Step 2: Run the focused test and verify red**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.MultimediaFileServiceTranslatedImageTest"
```

Expected: FAIL because production code still calls the legacy swallowing upload method.

- [ ] **Step 3: Replace the translated-image upload call**

Change:

```java
s3Service.upload(new ByteArrayInputStream(imageBytes), relativePath, mimeType);
```

to:

```java
s3Service.uploadWithRetry(imageBytes, relativePath, mimeType);
```

Leave image parsing and `saveAndFlush` after this call. A thrown terminal upload exception therefore prevents database persistence.

- [ ] **Step 4: Run the focused test**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.service.impl.MultimediaFileServiceTranslatedImageTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add v7-shop-services/v7-shop-admin/src/main/java/cn/v7soft/admin/service/impl/MultimediaFileService.java v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/service/impl/MultimediaFileServiceTranslatedImageTest.java
git commit -m "fix: persist translated image only after R2 upload"
```

### Task 5: Lock down TurboFlow failure and requeue behavior

**Files:**
- Modify: `v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/task/provider/TurboFlowBridgeProviderTest.java`

- [ ] **Step 1: Add a provider-level regression test**

Arrange a normal image completion and make:

```java
when(multimediaFileService.saveTranslatedImage(any(), eq("png"), any()))
        .thenThrow(new ObjectStorageUploadException(
                "object storage upload failed",
                SdkClientException.create("R2 timeout")));
```

Assert:

```java
assertThrows(IllegalStateException.class,
        () -> provider.completeTask("token", request));

verify(callback).onSubTaskFailed(
        eq(subTask),
        contains("object storage upload failed"),
        eq(true),
        eq(null),
        eq("COMPLETE_PROCESSING_FAILED"));
verify(callback, never()).onSubTaskCompleted(any(), any());
```

This captures the existing contract: after the in-process R2 attempts are exhausted, the adapter receives a retryable subtask failure and may rerun the task.

- [ ] **Step 2: Run the provider test**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:test --tests "cn.v7soft.admin.task.provider.TurboFlowBridgeProviderTest"
```

Expected: PASS.

- [ ] **Step 3: Run the module test suite**

Run:

```powershell
.\gradlew.bat :v7-shop-admin:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add v7-shop-services/v7-shop-admin/src/test/java/cn/v7soft/admin/task/provider/TurboFlowBridgeProviderTest.java
git commit -m "test: cover TurboFlow retry after R2 exhaustion"
```

### Task 6: Production verification and observability

**Files:**
- No source changes unless verification exposes a missing metric or log field.

- [ ] **Step 1: Validate configuration before deployment**

Set or confirm:

```bash
S3_UPLOAD_MAX_ATTEMPTS=3
S3_UPLOAD_INITIAL_BACKOFF_MILLIS=300
S3_UPLOAD_MAX_BACKOFF_MILLIS=1500
S3_UPLOAD_ATTEMPT_TIMEOUT_SECONDS=15
S3_UPLOAD_TOTAL_TIMEOUT_SECONDS=40
```

Keep the total below the effective Cloudflare/Nginx request timeout.

- [ ] **Step 2: Deploy and run one controlled translated-image task**

Expected logs:

```text
R2 upload attempt 1 failed ... retrying
translated image saved from bridge result ...
```

Expected Nginx access result:

```text
POST /turboflow-bridge/tasks/complete HTTP/2.0" 200
```

- [ ] **Step 3: Exercise a transient failure**

In a staging environment only, temporarily point the R2 endpoint at a TCP endpoint that accepts and closes connections. Submit one task and verify:

- exactly three PUT attempts occur;
- attempts finish within 40 seconds;
- no `MultimediaFile` row is created;
- `onSubTaskFailed(... retryable=true ...)` is called;
- the HTTP request returns an application error rather than hanging until an HTTP/2 reset.

- [ ] **Step 4: Exercise a permanent failure**

In staging, use invalid R2 credentials and verify:

- only one PUT attempt occurs;
- no metadata row is created;
- logs identify status 403 without printing secrets.

- [ ] **Step 5: Verify the original browser scenario**

Run the original Chrome-extension flow with a roughly 2 MB PNG. Confirm:

- `/turboflow-bridge/tasks/complete` returns HTTP 200 after a recoverable first PUT failure;
- Chrome no longer reports `ERR_HTTP2_PROTOCOL_ERROR`;
- the object exists in R2 and is readable;
- only one multimedia database record exists for the translated result.

