"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { ErroDeApi } from "@/lib/api/errors";
import { useCapacidades } from "@/lib/gestao/use-capacidades";
import { listarEtapas } from "@/lib/lead/api";

import {
  alterarInterruptor,
  alterarLimite,
  atualizarConfiguracaoDeCampanhas,
  atualizarRascunho,
  cancelarCampanha,
  criarCampanha,
  iniciarCampanha,
  listarCampanhas,
  listarConferencia,
  listarDestinatarios,
  listarExcluidos,
  listarTemplatesParaCampanha,
  marcarComoConferido,
  obterCampanha,
  obterConfiguracaoDeCampanhas,
  pausarCampanha,
  preverPublico,
  projetarEnvio,
  retomarCampanha,
  type FiltroDeDestinatarios,
} from "./api";
import type {
  AtualizacaoDeConfiguracao,
  Campanha,
  FiltroDePublico,
  MotivoDoDestinatario,
  PedidoDeCampanha,
  PedidoDeProjecao,
} from "./types";

/** Atualização automática enquanto a campanha envia: sem socket novo, reconsulta a cada 5 s. */
export const INTERVALO_DE_ATUALIZACAO_MS = 5000;
export const TAMANHO_DA_PAGINA = 10;

export const CHAVE_CAMPANHAS = ["campanhas"] as const;

/** A decisão vem do mesmo catálogo efetivo usado pelo backend e pela Gestão. */
export function usePodeEmCampanhas(acao: "criar" | "editar" | "testar" | "operar" | "conferir" | "configurar" | "opt_out" | "ver_destinatarios"): boolean {
  return useCapacidades().pode(`campanhas.${acao}`);
}

/** Funcionalidade desligada ou canal sem templates: o backend responde 404 em toda rota. */
export function funcionalidadeIndisponivel(erro: unknown): boolean {
  return erro instanceof ErroDeApi && erro.status === 404;
}

function tentarDeNovo(tentativas: number, erro: unknown): boolean {
  return !funcionalidadeIndisponivel(erro) && tentativas < 2;
}

function enviando(campanha: Campanha | undefined): boolean {
  return campanha?.status === "EM_ANDAMENTO" || campanha?.status === "AGENDADA";
}

export function useListaDeCampanhas(pagina: number) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "lista", pagina],
    queryFn: () => listarCampanhas(pagina, TAMANHO_DA_PAGINA),
    placeholderData: keepPreviousData,
    retry: tentarDeNovo,
    refetchInterval: (consulta) =>
      consulta.state.data?.itens.some((item) => enviando(item)) ? INTERVALO_DE_ATUALIZACAO_MS : false,
  });
}

export function useDetalheDaCampanha(id: string) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "detalhe", id],
    queryFn: () => obterCampanha(id),
    retry: tentarDeNovo,
    refetchInterval: (consulta) =>
      enviando(consulta.state.data?.campanha) ? INTERVALO_DE_ATUALIZACAO_MS : false,
  });
}

export function useTemplatesParaCampanha() {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "templates"],
    queryFn: listarTemplatesParaCampanha,
    retry: tentarDeNovo,
  });
}

export function usePreviaDoPublico(filtro: Partial<FiltroDePublico>) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "previa", filtro],
    queryFn: () => preverPublico(filtro),
    placeholderData: keepPreviousData,
  });
}

export function useExcluidosDoPublico(filtro: Partial<FiltroDePublico>, motivo: MotivoDoDestinatario | null) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "excluidos", filtro, motivo],
    queryFn: () => listarExcluidos(filtro, motivo as MotivoDoDestinatario),
    enabled: motivo !== null,
  });
}

export function useProjecaoDeEnvio(pedido: PedidoDeProjecao | null) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "projecao", pedido],
    queryFn: () => projetarEnvio(pedido as PedidoDeProjecao),
    enabled: pedido !== null,
    placeholderData: keepPreviousData,
  });
}

export function useDestinatarios(id: string, filtro: FiltroDeDestinatarios, pagina: number) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "destinatarios", id, filtro, pagina],
    queryFn: () => listarDestinatarios(id, filtro, pagina, TAMANHO_DA_PAGINA),
    placeholderData: keepPreviousData,
  });
}

export function useConferencia(id: string, pagina: number) {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "conferencia", id, pagina],
    queryFn: () => listarConferencia(id, pagina, TAMANHO_DA_PAGINA),
    placeholderData: keepPreviousData,
  });
}

export function useConfiguracaoDeCampanhas() {
  return useQuery({
    queryKey: [...CHAVE_CAMPANHAS, "configuracao"],
    queryFn: obterConfiguracaoDeCampanhas,
    retry: tentarDeNovo,
  });
}

function useInvalidarCampanhas() {
  const cliente = useQueryClient();
  return () => cliente.invalidateQueries({ queryKey: CHAVE_CAMPANHAS });
}

export function useSalvarRascunho() {
  const invalidar = useInvalidarCampanhas();
  return useMutation({
    mutationFn: ({ id, pedido }: { id: string | null; pedido: PedidoDeCampanha }) =>
      id ? atualizarRascunho(id, pedido) : criarCampanha(pedido),
    onSuccess: invalidar,
  });
}

export type AcaoDaCampanha = "iniciar" | "pausar" | "retomar" | "cancelar";

const ACOES: Record<AcaoDaCampanha, (id: string) => Promise<Campanha>> = {
  iniciar: (id) => iniciarCampanha(id),
  pausar: (id) => pausarCampanha(id),
  retomar: (id) => retomarCampanha(id),
  cancelar: (id) => cancelarCampanha(id),
};

export function useAcaoDaCampanha(acao: AcaoDaCampanha) {
  const invalidar = useInvalidarCampanhas();
  return useMutation({ mutationFn: (id: string) => ACOES[acao](id), onSuccess: invalidar });
}

export function useAlterarLimite(id: string) {
  const invalidar = useInvalidarCampanhas();
  return useMutation({
    mutationFn: ({ limiteDiario, ritmoPorMinuto }: { limiteDiario: number; ritmoPorMinuto: number | null }) =>
      alterarLimite(id, limiteDiario, ritmoPorMinuto),
    onSuccess: invalidar,
  });
}

export function useAlterarInterruptor(id: string) {
  const invalidar = useInvalidarCampanhas();
  return useMutation({
    mutationFn: (desligada: boolean) => alterarInterruptor(id, desligada),
    onSuccess: invalidar,
  });
}

export function useMarcarComoConferido(id: string) {
  const invalidar = useInvalidarCampanhas();
  return useMutation({
    mutationFn: (destinatarioId: string) => marcarComoConferido(id, destinatarioId),
    onSuccess: invalidar,
  });
}

export function useAtualizarConfiguracao() {
  const invalidar = useInvalidarCampanhas();
  return useMutation({
    mutationFn: (atualizacao: AtualizacaoDeConfiguracao) => atualizarConfiguracaoDeCampanhas(atualizacao),
    onSuccess: invalidar,
  });
}

export function useEtapasParaFiltro() {
  return useQuery({ queryKey: ["etapas"], queryFn: listarEtapas, staleTime: 5 * 60 * 1000 });
}

/**
 * Segura o valor por `atrasoMs`: a prévia do público e a projeção só são consultadas quando a digitação para.
 * A comparação é pelo conteúdo (JSON): quem chama costuma montar um objeto novo a cada render, e comparar a
 * identidade reiniciaria o temporizador sem parar.
 */
export function useValorComAtraso<T>(valor: T, atrasoMs = 400): T {
  const chave = JSON.stringify(valor);
  const [atrasado, setAtrasado] = useState(valor);
  useEffect(() => {
    const temporizador = setTimeout(() => setAtrasado(JSON.parse(chave) as T), atrasoMs);
    return () => clearTimeout(temporizador);
  }, [chave, atrasoMs]);
  return atrasado;
}
