import { apiFetch } from "@/lib/api/http-client";

export interface CandidatoDarwin { sessaoId: string | null; nome: string | null; cpf: string | null; cpfInvalido: boolean }
export interface PreviaDarwin {
  estado: "ENCONTRADO" | "SEM_RESULTADO" | "AMBIGUO" | "SEM_IDENTIDADE";
  candidatos: CandidatoDarwin[]; limiteAtingido: boolean;
  nomeAtual: string | null; cpfAtual: string | null; confirmacao: string | null;
}
export const disponibilidadeDarwin = (signal?: AbortSignal) =>
  apiFetch<{ habilitada: boolean }>("/api/v1/leads/integracoes/darwin", { signal });
export const consultarDarwin = (id: string, cpf: string, signal: AbortSignal) =>
  apiFetch<PreviaDarwin>(`/api/v1/leads/${encodeURIComponent(id)}/darwin/consultas`, { method: "POST", body: JSON.stringify({ cpf }), signal });
export const aplicarDarwin = (id: string, confirmacao: string, nome: boolean, cpf: boolean) =>
  apiFetch<{ leadId: string; camposAlterados: string[] }>(`/api/v1/leads/${encodeURIComponent(id)}/darwin/aplicacoes`, {
    method: "POST", body: JSON.stringify({ confirmacao, nome, cpf }),
  });
