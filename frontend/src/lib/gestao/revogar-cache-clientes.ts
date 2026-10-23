import type { QueryClient, QueryKey } from "@tanstack/react-query";
import type { MinhasPermissoes } from "./types";
import { CHAVE_MINHAS_PERMISSOES } from "./use-gestao";

const PREFIXOS = new Set([
  "lead", "leads", "agenda", "mensagens", "midia-url-assinada", "resumo-ia",
  "informacoes-chatbot", "dashboard", "cancelamentos", "lembretes", "mensagens-programadas",
  "finalizacao-em-massa", "campanhas",
]);

const CAPACIDADES_EXTERNAS = new Set([
  "clientes", "atendimentos", "contatos", "resumo_ia", "dashboard", "mensagens_programadas",
  "lembretes", "campanhas", "cancelamentos",
]);

export function consultaDeClientes(chave: QueryKey): boolean {
  return PREFIXOS.has(String(chave[0]))
    || chave[0] === "atendimentos" && chave[1] !== "internos-painel"
    || chave[0] === "equipe" && ["avaliacoes", "desempenho"].includes(String(chave[1]))
    || chave[0] === "dialogos" && chave[1] === "atendimentos";
}

/** O aviso não contém novas concessões. Fecha a leitura até a revalidação, sem tocar no chat interno.
 * Cancelar antes de remover impede que GETs anteriores republiquem dados após a revogação.
 * Não repete mutations nem apaga rascunhos.
 */
export function revogarCacheDeClientes(cache: QueryClient): void {
  void cache.cancelQueries({ queryKey: CHAVE_MINHAS_PERMISSOES });
  cache.setQueryData<MinhasPermissoes>(CHAVE_MINHAS_PERMISSOES, anterior => anterior ? {
    ...anterior,
    capacidades: Object.fromEntries(Object.entries(anterior.capacidades).map(([id, valor]) => [id,
      CAPACIDADES_EXTERNAS.has(id.split(".")[0]) || id === "tags.aplicar"
        ? { ...valor, permitido: false, motivo: "DEPENDENCIA" as const } : valor,
    ])),
  } : undefined);
  const filtro = { predicate: (q: { queryKey: QueryKey }) => consultaDeClientes(q.queryKey) };
  void cache.cancelQueries(filtro);
  cache.removeQueries(filtro);
}
