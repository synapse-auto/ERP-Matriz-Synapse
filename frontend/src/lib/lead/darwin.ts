import { apiFetch } from "@/lib/api/http-client";

export const estadoColetaDarwin = (id: string, signal?: AbortSignal) =>
  apiFetch<{ estado: string }>(`/api/v1/leads/${encodeURIComponent(id)}/darwin/coleta`, { signal });
export const tentarColetaDarwin = (id: string) =>
  apiFetch<void>(`/api/v1/leads/${encodeURIComponent(id)}/darwin/coleta`, { method: "POST" });

export interface CandidatoDarwin { sessaoId: string | null; nome: string | null; cpf: string | null; cpfInvalido: boolean; email: string | null; emailInvalido: boolean }
export interface PreviaDarwin {
  estado: "ENCONTRADO" | "SEM_RESULTADO" | "AMBIGUO" | "SEM_IDENTIDADE";
  candidatos: CandidatoDarwin[]; limiteAtingido: boolean;
  nomeAtual: string | null; cpfAtual: string | null; emailAtual: string | null; confirmacao: string | null;
}
export const disponibilidadeDarwin = (signal?: AbortSignal) =>
  apiFetch<{ habilitada: boolean; estado?: "DESABILITADA" | "CONFIGURACAO_INCOMPLETA" | "DISPONIVEL" }>("/api/v1/leads/integracoes/darwin", { signal });
export const consultarDarwin = (id: string, cpf: string, signal: AbortSignal) =>
  apiFetch<PreviaDarwin>(`/api/v1/leads/${encodeURIComponent(id)}/darwin/consultas`, { method: "POST", body: JSON.stringify({ cpf }), signal });
export const aplicarDarwin = (id: string, confirmacao: string, nome: boolean, cpf: boolean, email: boolean) =>
  apiFetch<{ leadId: string; camposAlterados: string[] }>(`/api/v1/leads/${encodeURIComponent(id)}/darwin/aplicacoes`, {
    method: "POST", body: JSON.stringify({ confirmacao, nome, cpf, email }),
  });
