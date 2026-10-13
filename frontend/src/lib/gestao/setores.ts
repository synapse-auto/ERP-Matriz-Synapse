"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api/http-client";

/**
 * Feature flag dos setores (HabilitacaoDeSetoresPorFeatureFlag.FUNCIONALIDADE). Desligada ou ausente de
 * `/api/v1/config/features`, nenhuma tela de setor aparece e nenhuma rota de setor é chamada.
 */
export const FLAG_SETORES = "setores";

export interface Setor {
  id: string;
  nome: string;
  recebeDaTriagem: boolean;
  ativo: boolean;
  quantidadeDeUsuarios: number;
}

export interface AtribuicaoDeSetor {
  usuarioId: string;
  setorId: string | null;
}

export interface VisaoDeSetores {
  setores: Setor[];
  atribuicoes: AtribuicaoDeSetor[];
}

export interface AlteracaoDeSetor {
  nome: string;
  recebeDaTriagem: boolean;
  ativo: boolean;
}

const CHAVE_SETORES = ["setores"] as const;

const listarSetores = () => apiFetch<VisaoDeSetores>("/api/v1/setores");

const criarSetor = (nome: string) =>
  apiFetch<Setor>("/api/v1/setores", { method: "POST", body: JSON.stringify({ nome }) });

const atualizarSetor = (id: string, dados: AlteracaoDeSetor) =>
  apiFetch<Setor>(`/api/v1/setores/${id}`, { method: "PUT", body: JSON.stringify(dados) });

const atribuirSetor = (usuarioId: string, setorId: string | null) =>
  apiFetch<AtribuicaoDeSetor>(`/api/v1/usuarios/${usuarioId}/setor`, {
    method: "PUT",
    body: JSON.stringify({ setorId }),
  });

/** `habilitado` só é verdadeiro com a flag ligada: com ela desligada a consulta nem é feita. */
export function useSetores(habilitado: boolean) {
  return useQuery({ queryKey: CHAVE_SETORES, queryFn: listarSetores, enabled: habilitado });
}

export function useCriarSetor() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: criarSetor,
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_SETORES }),
  });
}

export function useAtualizarSetor() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dados }: { id: string; dados: AlteracaoDeSetor }) => atualizarSetor(id, dados),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_SETORES }),
  });
}

export function useAtribuirSetor() {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: ({ usuarioId, setorId }: { usuarioId: string; setorId: string | null }) =>
      atribuirSetor(usuarioId, setorId),
    onSuccess: () => cache.invalidateQueries({ queryKey: CHAVE_SETORES }),
  });
}
