import { useCallback, useEffect, useMemo, useState } from "preact/hooks";
import { fetchProviders } from "../api";
import type { LlmProvider } from "../types";

export function useProviders() {
  const [providers, setProviders] = useState<LlmProvider[]>([]);
  const [providerId, setProviderId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const available = await fetchProviders();
      setProviders(available);
      setProviderId((current) => selectAvailable(current, available));
    } catch (reason) {
      setError(errorMessage(reason, "Unable to load LLM providers."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const activeProvider = useMemo(
    () => providers.find((provider) => provider.id === providerId),
    [providers, providerId],
  );

  return { providers, providerId, setProviderId, activeProvider, error, loading, load };
}

function selectAvailable(current: string, providers: LlmProvider[]): string {
  if (providers.some((provider) => provider.id === current)) return current;
  return providers[0]?.id ?? "";
}

function errorMessage(reason: unknown, fallback: string): string {
  return reason instanceof Error ? reason.message : fallback;
}
