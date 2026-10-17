import type { QueryClient } from "@tanstack/react-query";

type Lote = { sujo: boolean; promessa: Promise<void> };
const lotesPorCache = new WeakMap<QueryClient, Map<string, Lote>>();

/** Agrupa eventos sem cancelar requests nem perder a atualização durante uma leitura antiga. */
export function invalidarArquivosDoLead(cache: QueryClient, leadId: string) {
  let lotes = lotesPorCache.get(cache);
  if (!lotes) {
    lotes = new Map();
    lotesPorCache.set(cache, lotes);
  }
  const atual = lotes.get(leadId);
  if (atual) {
    atual.sujo = true;
    return atual.promessa;
  }
  const chave = ["lead", leadId, "midias"];
  const lote: Lote = { sujo: true, promessa: Promise.resolve() };
  lotes.set(leadId, lote);
  lote.promessa = (async () => {
    try {
      do {
        const pendentes = cache.getQueryCache().findAll({queryKey: chave, type: "active"})
          .filter(query => query.state.fetchStatus === "fetching")
          .flatMap(query => query.promise ? [query.promise.catch(() => undefined)] : []);
        await Promise.all(pendentes);
        // Eventos anteriores a esta nova leitura já serão satisfeitos por ela.
        lote.sujo = false;
        await cache.invalidateQueries({queryKey: chave}, {cancelRefetch: false});
      } while (lote.sujo);
    } finally {
      lotes.delete(leadId);
    }
  })();
  return lote.promessa;
}
