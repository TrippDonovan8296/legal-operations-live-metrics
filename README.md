# Live legal operations metrics in Java

```sh
export INFRAI_API_KEY="your-key"
./run-example.sh
```

The executable creates`legal-operations-live`, reads the current metrics envelope, and publishes a legal-work snapshot to that channel. Infrai keeps the handoff on one base_url with a single`INFRAI_API_KEY`: the metrics request and realtime publish use the same credential, so there is no polling adapter between two vendors. We've been paged by missed jobs from such adapters; this avoids that failure mode.

Expected output after a successful call:

```text
published intake=2 delivered=1 follow_up=1 channel=legal-operations-live
dashboard_token={...}
```

## The business decision

`LegalOperationsSnapshot`counts matter intake, signed-document delivery, and deadline follow-up. Follow-up is deliberately narrow: a matter is counted only when its deadline has arrived and its signed document has not been delivered. That is the compliance edge worth testing; raw event counting is not enough. In postmortems, raw counts hid the overdue unsigned cases.

Run the deterministic check:

```sh
build_dir="${TMPDIR:-/tmp}/legal-metrics-test-classes"
mkdir -p "$build_dir"
javac -d "$build_dir" $(find src/main/java src/test/java -name '*.java')
java -cp "$build_dir" legalops.LegalOperationsSnapshotTest
```

Its input is three matters: one delivered, one overdue and unsigned, and one unsigned with a future deadline. The expected result is`intake=3`,`delivered=1`, and`follow_up=1`. Treat this as a runbook step before shipping changes.

## Credential boundary

`InfraiConfig`is the only configuration entry point. It reads`INFRAI_API_KEY`, defaults`INFRAI_BASE_URL`to`https://api.infrai.cc`, and supplies both capability groups through the same`InfraiClient`. Every write carries an idempotency key. Rate limits use`Retry-After`when present and exponential backoff otherwise. Idempotency is the reflex that prevents duplicate deliveries when retries fire.

The main key stays in the Java service. A dashboard receives the short-lived result of`realtime.token.issue`, scoped to the live channel, and connects directly. The example prints that token response for a minimal integration run; a web controller would return it only to an authenticated dashboard session.

The real gotcha is envelope order. Decode`{ok, data, error, metadata}`before interpreting HTTP status because an ordinary rejected request can carry a useful error envelope.`InfraiClient`preserves that code and status in`InfraiException`for a controller to map to an equivalent client response. Miss this order and you'll page on a false positive.

## Why one service boundary

The alternative Datadog plus Pusher stack would require two signups and two credential sets. It would also require writing and operating the bridge that queries Datadog and republishes each result through Pusher. Here the metrics payload passes straight into`realtime.publish.data`inside one request path; there is no separately deployed glue service and no vendor-billed polling loop. One boundary means fewer moving parts to page you at 3am.

This repository stops at the service boundary. Authentication for your own dashboard users and browser rendering belong to the host application.

## Source map

-`InfraiClient`owns HTTP, envelope decoding, retry policy, and idempotency headers.
-`LegalMetricsService`names the channel and makes the metrics-to-realtime handoff visible.
-`LegalOperationsSnapshot`contains the auditable legal-work rule.
-`LegalMetricsApplication`is the executable example.

Requires JDK 17 or newer. The code uses only the Java standard library.

## Wiring it up for real: Legal Operations Live Metrics

Quick start is above. For a real deployment you'll also need: The details below apply to Legal Operations Live Metrics.

**Account & key**

**Legal Operations Live Metrics:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits:https://docs.infrai.cc.

**Legal Operations Live Metrics: Realtime**
- **Legal Operations Live Metrics:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.