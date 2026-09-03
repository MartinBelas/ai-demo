import { useEffect, useState } from "preact/hooks";
import { fetchStatus, formatDuration, formatMetric, formatUptime } from "../status";
import type { AppStatus } from "../status";

export function StatusPage() {
  const [refresh, setRefresh] = useState(0);
  const [data, setData] = useState<AppStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updated, setUpdated] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    void fetchStatus(controller.signal).then(result => {
      if (controller.signal.aborted) return;
      setData(result);
      setUpdated(new Date().toLocaleTimeString("en-GB"));
    }).catch(() => {
      if (!controller.signal.aborted) setError("Application statistics are temporarily unavailable. Please try again.");
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [refresh]);

  let refreshLabel = "Refresh statistics";
  if (loading) refreshLabel = "Loading…";
  else if (error) refreshLabel = "Try again";

  let freshnessMessage: string;
  if (loading) freshnessMessage = "Loading the latest statistics…";
  else if (error) freshnessMessage = data ? "Refresh failed. The figures below are from the previous update." : "No statistics available.";
  else freshnessMessage = `Last checked at ${updated} · Figures may be cached for up to 10 seconds.`;

  return <main id="main" class="status-page">
    <header class="status-introduction"><div><p class="eyebrow">Status / public demo</p><h1>A day in the demo.</h1><p>Chat activity, response times and usage.<br />An aggregate view of how the demo is running.</p></div><a class="source-link" href="/#main">Open the workbench <span aria-hidden="true">↗</span></a></header>
    <div class="status-toolbar"><p>{data ? <>Reporting day <strong>{data.period}</strong> / UTC</> : "Daily activity / UTC"}</p><button class="text-button" disabled={loading} onClick={() => setRefresh(value => value + 1)}>{refreshLabel}</button></div>
    <output class="status-freshness">{freshnessMessage}</output>
    {error && <p class="stream-error" role="alert">{error}</p>}
    {data && <StatusMetrics data={data} />}
  </main>;
}

export function StatusMetrics({ data }: { data: AppStatus }) {
  const daily = (value: number | null) => formatMetric(data.trackingEnabled ? value : null);
  return <>
    {!data.trackingEnabled ? <p class="status-notice">Daily tracking is disabled. Daily activity and active streams are unavailable; service uptime remains visible below.</p> : <p class="status-notice">{data.persistent ? "Daily totals are stored across restarts." : "Temporary statistics: daily totals reset when the service restarts."}{data.requests === 0 && " No chat requests have been accepted in this reporting period yet."}</p>}
    <section aria-label="Daily usage"><dl class="status-totals">
      <Metric label="Accepted requests" value={daily(data.requests)} note="Includes accepted requests that later fail or are interrupted." />
      <Metric label="Tokens used" value={daily(data.tokens)} note="Recorded daily usage may omit failed or interrupted requests. This is not a billing total." />
      <Metric label="Requests remaining" value={daily(data.requestsRemaining)} note={data.trackingEnabled && data.dailyRequestLimit !== null ? `Daily request limit: ${formatMetric(data.dailyRequestLimit)}` : "No daily request allowance is available."} />
    </dl></section>
    <section class="status-outcomes" aria-labelledby="outcome-title"><div class="status-section-heading"><p class="eyebrow">01 / Response outcomes</p><h2 id="outcome-title">How requests finish.</h2><p>Outcomes are recorded from the introduction of these metrics. Earlier requests may have no recorded outcome, so totals can differ.</p></div><dl class="status-details">
      <Metric label="Completed" value={daily(data.completed)} note="Requests completed successfully." />
      <Metric label="Failed" value={daily(data.failed)} note="Requests ending with an error." />
      <Metric label="Disconnected" value={daily(data.disconnected)} note="Streams interrupted before completion." />
      <Metric label="Average response time" value={formatDuration(data.trackingEnabled ? data.averageDurationMs : null)} note="Completed requests only. Unavailable until one completes." />
    </dl></section>
    <section class="status-instance" aria-labelledby="instance-title"><div class="status-section-heading"><p class="eyebrow">02 / Current instance</p><h2 id="instance-title">Running right now.</h2><p>These figures describe the server instance answering this request and reset on restart.</p></div><dl class="status-details">
      <Metric label="Active streams" value={formatMetric(data.trackingEnabled ? data.activeStreams : null)} note="Includes streams started before midnight UTC. Unavailable when tracking is disabled." />
      <Metric label="Service uptime" value={formatUptime(data.uptimeSeconds)} note="Time since this instance started." />
    </dl></section>
    <footer class="status-footnote">These are aggregate usage statistics. Requests are not a count of visitors or people.</footer>
  </>;
}

function Metric({ label, value, note }: { label: string; value: string; note: string }) {
  return <div class="status-metric"><dt>{label}</dt><dd class={value === "Unavailable" ? "is-unavailable" : ""}>{value}</dd><dd class="status-metric-note">{note}</dd></div>;
}

