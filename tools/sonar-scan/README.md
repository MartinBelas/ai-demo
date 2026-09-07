# Local SonarQube scan

Runs a real SonarQube Community Edition analysis against `ai-demo` from the command line,
without any external account. Used because this machine's Java trust store can't validate
Maven Central's TLS certificate (Avast's local HTTPS scanning root CA isn't in the JDK
`cacerts`, only in the Windows trust store) — see `local-truststore.jks` below.

## One-time setup (already done, kept for reference)

```bash
# Export Avast's local scanning root CA from the Windows trust store and import it into a
# private copy of the JDK's cacerts, used only for this tool via -Djavax.net.ssl.trustStore.
# (PowerShell) Export-Certificate -Cert <Avast root in Cert:\LocalMachine\Root> -FilePath avast-root.cer
cp "$JAVA_HOME/lib/security/cacerts" local-truststore.jks
keytool -importcert -noprompt -trustcacerts -alias avast-mitm \
  -file avast-root.cer -keystore local-truststore.jks -storepass changeit
```

`avast-root.cer` and `local-truststore.jks` are machine-specific and gitignored — regenerate
them with the commands above on a new machine if Maven/Java again fails downloads with
`PKIX path building failed` while `curl` to the same URL works fine.

## Start SonarQube (once per Docker session)

```bash
docker start sonarqube 2>/dev/null || docker run -d --name sonarqube -p 9000:9000 sonarqube:community
# wait for it to report {"status":"UP"} — takes ~1-2 minutes on a cold start
curl -s http://localhost:9000/api/system/status
```

Login: `admin` / password set on first setup (change it via `/api/users/change_password` if
lost, or `docker rm -f sonarqube` and start a fresh container). Generate a token once via:

```bash
curl -s -u admin:<password> -X POST "http://localhost:9000/api/user_tokens/generate" \
  --data-urlencode "name=ai-demo-cli"
```

## Run the scan

From the **project root** (not this `tools/` directory):

```bash
export MAVEN_OPTS="-Djavax.net.ssl.trustStore=$(pwd)/tools/sonar-scan/local-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
export SONAR_TOKEN=<token>
rm -rf target/sonar   # avoids a stale-temp-folder failure from a previous run
mvn -q org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.projectKey=ai-demo -Dsonar.projectName=ai-demo
```

Then read results either via the dashboard (http://localhost:9000/dashboard?id=ai-demo) or the API:

```bash
curl -s -u admin:<password> \
  "http://localhost:9000/api/issues/search?componentKeys=ai-demo&ps=100&statuses=OPEN" \
  -o open_issues.json
node list_issues.js open_issues.json
```

`statuses=OPEN` matters — without it, already-fixed issues from earlier scans still show up
too (marked `CLOSED`).

## Known accepted findings (suppressed or intentionally left)

- `ConsoleChat` — `@SuppressWarnings("java:S106")`: it's a console UI, `System.out` is its job.
- `App` — `@SuppressWarnings("java:S6539")`: composition root, wiring many collaborators is expected.
- `LoggingLlmClient` — S2245 (pseudorandom request-id, not security-sensitive) and S2139 x4
  (logs then rethrows) left as-is: the duplicate log line this produces alongside the API
  endpoint's own log was a deliberate trade-off for keeping per-request timing/correlation-id
  diagnostics, decided on 2026-09-07.
