"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAtendimentos, useContagemDeAtendimentos } from "@/lib/atendimento/use-atendimentos";
import type { CartaoAtendimento, VisaoAtendimento } from "@/lib/atendimento/types";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

import { CartaoConversa } from "./cartao-conversa";

type Props = {
  selecionadoId: string | null;
  leadInicialId?: string | null;
  visaoInicial?: VisaoAtendimento | null;
  onAbrirPainel: (cartao: CartaoAtendimento) => void;
  onAbrirAtendimento: (cartao: CartaoAtendimento) => void;
};

const VISOES_ATENDENTE: VisaoAtendimento[] = ["ATIVOS", "PENDENTES", "POTENCIAIS"];
const VISOES_PAPEL_AMPLO: VisaoAtendimento[] = ["TODOS", "PENDENTES"];

const ROTULO_VISAO: Record<VisaoAtendimento, keyof ReturnType<typeof useTextos>["atendimentos"]["visoes"]> = {
  ATIVOS: "ativos",
  PENDENTES: "pendentes",
  POTENCIAIS: "potenciais",
  TODOS: "todos",
};

/**
 * Ativos/Pendentes/Potenciais para atendente, Todos/Pendentes para gestor/subgestor — o servidor
 * já filtra por papel (RN-CRM-01); aqui só decidimos QUAIS abas pedir, nunca filtramos visibilidade.
 */
export function ListaConversas({
  selecionadoId,
  leadInicialId,
  visaoInicial,
  onAbrirPainel,
  onAbrirAtendimento,
}: Props) {
  const textos = useTextos().atendimentos;
  const papel = useAuthStore((estado) => estado.papel);
  // null (sessao ainda carregando) usa o mesmo default de ATENDENTE — a suposicao mais restrita,
  // nunca "papel amplo" so porque o papel real ainda nao chegou do JWT.
  const visoes = papel && papel !== "ATENDENTE" ? VISOES_PAPEL_AMPLO : VISOES_ATENDENTE;
  const [visaoEscolhida, setVisaoEscolhida] = useState<VisaoAtendimento | null>(visaoInicial ?? null);
  // Deriva no render, sem efeito: se o papel real (chegando depois da hidratacao) mudar o conjunto
  // de abas, uma escolha orfa (ex.: "ATIVOS" fora de VISOES_PAPEL_AMPLO) cai de volta pra primeira
  // aba valida automaticamente, sem precisar de setState dentro de useEffect.
  const visao = visaoEscolhida && visoes.includes(visaoEscolhida) ? visaoEscolhida : visoes[0];
  const [filtroEtapa, setFiltroEtapa] = useState<string | null>(null);
  const [filtroAtendente, setFiltroAtendente] = useState<string | null>(null);

  const { data, isLoading } = useAtendimentos(visao);
  const { data: contagens } = useContagemDeAtendimentos();
  const abriuLeadInicial = useRef(false);

  useEffect(() => {
    if (!leadInicialId || abriuLeadInicial.current || !data) return;
    const cartao = data.find((item) => item.leadId === leadInicialId);
    if (cartao) {
      abriuLeadInicial.current = true;
      onAbrirAtendimento(cartao);
    }
  }, [data, leadInicialId, onAbrirAtendimento]);

  const etapas = useMemo(() => {
    const mapa = new Map<string, string>();
    for (const cartao of data ?? []) {
      if (cartao.etapaId && cartao.etapaNome) {
        mapa.set(cartao.etapaId, cartao.etapaNome);
      }
    }
    return Array.from(mapa.entries());
  }, [data]);

  const atendentes = useMemo(() => {
    const mapa = new Map<string, string>();
    for (const cartao of data ?? []) {
      if (cartao.atendenteId && cartao.atendenteNome) {
        mapa.set(cartao.atendenteId, cartao.atendenteNome);
      }
    }
    return Array.from(mapa.entries());
  }, [data]);

  const filtrados = (data ?? []).filter(
    (cartao) =>
      (!filtroEtapa || cartao.etapaId === filtroEtapa) &&
      (!filtroAtendente || cartao.atendenteId === filtroAtendente),
  );

  return (
    <div className="flex h-full flex-col border-r border-border">
      <Tabs value={visao} onValueChange={(valor) => setVisaoEscolhida(valor as VisaoAtendimento)}>
        <TabsList className="w-full">
          {visoes.map((item) => (
            <TabsTrigger key={item} value={item} className="flex-1 gap-1.5">
              {textos.visoes[ROTULO_VISAO[item]]}
              {contagens && (
                <Badge variant={item === visao ? "default" : "secondary"} className="px-1.5">
                  {contagens[item]}
                </Badge>
              )}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {(etapas.length > 0 || atendentes.length > 1) && (
        <div className="flex gap-2 border-b border-border p-2 text-xs">
          {etapas.length > 0 && (
            <select
              className="rounded border border-border bg-background px-1.5 py-1"
              value={filtroEtapa ?? ""}
              onChange={(evento) => setFiltroEtapa(evento.target.value || null)}
            >
              <option value="">{textos.filtros.etapa}</option>
              {etapas.map(([id, nome]) => (
                <option key={id} value={id}>
                  {nome}
                </option>
              ))}
            </select>
          )}
          {atendentes.length > 1 && (
            <select
              className="rounded border border-border bg-background px-1.5 py-1"
              value={filtroAtendente ?? ""}
              onChange={(evento) => setFiltroAtendente(evento.target.value || null)}
            >
              <option value="">{textos.filtros.atendente}</option>
              {atendentes.map(([id, nome]) => (
                <option key={id} value={id}>
                  {nome}
                </option>
              ))}
            </select>
          )}
        </div>
      )}

      <ScrollArea className="flex-1">
        {isLoading ? (
          <div className="space-y-2 p-3">
            {Array.from({ length: 5 }).map((_, indice) => (
              <Skeleton key={indice} className="h-14 w-full" />
            ))}
          </div>
        ) : filtrados.length === 0 ? (
          <p className="p-4 text-center text-sm text-muted-foreground">{textos.cartao.vazio}</p>
        ) : (
          filtrados.map((cartao) => (
            <CartaoConversa
              key={cartao.atendimentoId}
              cartao={cartao}
              selecionado={cartao.atendimentoId === selecionadoId}
              onAbrirPainel={() => onAbrirPainel(cartao)}
              onAbrirAtendimento={() => onAbrirAtendimento(cartao)}
            />
          ))
        )}
      </ScrollArea>
    </div>
  );
}
