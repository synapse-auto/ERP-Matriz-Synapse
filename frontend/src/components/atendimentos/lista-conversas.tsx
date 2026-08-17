"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Search, SlidersHorizontal } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Seletor } from "@/components/ui/seletor";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useAtendimentos,
  useContagemDeAtendimentos,
} from "@/lib/atendimento/use-atendimentos";
import type {
  CartaoAtendimento,
  VisaoAtendimento,
} from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

import { CartaoConversa } from "./cartao-conversa";

type Props = {
  selecionadoId: string | null;
  leadInicialId?: string | null;
  visaoInicial?: VisaoAtendimento | null;
  onAbrirAtendimento: (cartao: CartaoAtendimento) => void;
};

const VISOES: VisaoAtendimento[] = [
  "TODOS",
  "ATIVOS",
  "PENDENTES",
  "POTENCIAIS",
];

const ROTULO_VISAO: Record<
  VisaoAtendimento,
  keyof ReturnType<typeof useTextos>["atendimentos"]["visoes"]
> = {
  ATIVOS: "ativos",
  PENDENTES: "pendentes",
  POTENCIAIS: "potenciais",
  TODOS: "todos",
};

/**
 * As quatro visões são sempre apresentadas. O servidor mantém o recorte por papel e propriedade
 * (RN-CRM-01); esta lista nunca amplia a visibilidade recebida da API.
 */
export function ListaConversas({
  selecionadoId,
  leadInicialId,
  visaoInicial,
  onAbrirAtendimento,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos;
  const [visaoEscolhida, setVisaoEscolhida] = useState<VisaoAtendimento | null>(
    visaoInicial ?? null,
  );
  const visao =
    visaoEscolhida && VISOES.includes(visaoEscolhida)
      ? visaoEscolhida
      : VISOES[0];
  const [busca, setBusca] = useState("");
  const [filtrosAbertos, setFiltrosAbertos] = useState(false);
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

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase("pt-BR");
    return (data ?? []).filter((cartao) => {
      const correspondeABusca =
        !termo ||
        [cartao.leadNome, cartao.leadEmpresa, cartao.atendimentoId].some(
          (valor) => valor?.toLocaleLowerCase("pt-BR").includes(termo),
        );
      return (
        correspondeABusca &&
        (!filtroEtapa || cartao.etapaId === filtroEtapa) &&
        (!filtroAtendente || cartao.atendenteId === filtroAtendente)
      );
    });
  }, [busca, data, filtroAtendente, filtroEtapa]);

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden border-r border-border bg-background">
      <div className="px-4 pt-4">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-lg font-extrabold tracking-tight text-foreground">
            {catalogo.menu.itens.atendimentos}
          </h1>
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            aria-label={textos.lista.filtros}
            aria-pressed={filtrosAbertos}
            disabled={etapas.length === 0 && atendentes.length <= 1}
            onClick={() => setFiltrosAbertos((abertos) => !abertos)}
          >
            <SlidersHorizontal className="size-4" aria-hidden />
          </Button>
        </div>
        <div className="relative mt-3">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={busca}
            onChange={(evento) => setBusca(evento.target.value)}
            placeholder={textos.lista.busca}
            className="h-10 rounded-xl bg-muted/60 pl-9"
          />
        </div>
      </div>

      <Tabs
        value={visao}
        onValueChange={(valor) => setVisaoEscolhida(valor as VisaoAtendimento)}
      >
        <TabsList className="mx-4 mt-3 grid h-auto w-[calc(100%-2rem)] grid-cols-4 gap-0 rounded-none border-b border-border bg-transparent p-0">
          {VISOES.map((item) => (
            <TabsTrigger
              key={item}
              value={item}
              className="min-w-0 gap-1 rounded-none border-b-2 border-transparent px-0.5 pt-1 pb-2.5 text-[0.6875rem] shadow-none data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none"
            >
              {textos.visoes[ROTULO_VISAO[item]]}
              {contagens && (
                <Badge
                  variant={item === visao ? "default" : "secondary"}
                  className="shrink-0 px-1 text-[0.625rem]"
                >
                  {contagens[item]}
                </Badge>
              )}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {filtrosAbertos && (etapas.length > 0 || atendentes.length > 1) && (
        <div className="flex gap-2 border-b border-border p-2 text-xs">
          {etapas.length > 0 && (
            <Seletor
              className="min-w-28"
              valor={filtroEtapa ?? ""}
              placeholder={textos.filtros.etapa}
              opcoes={etapas.map(([id, nome]) => ({ valor: id, rotulo: nome }))}
              onChange={(valor) => setFiltroEtapa(valor || null)}
            />
          )}
          {atendentes.length > 1 && (
            <Seletor
              className="min-w-28"
              valor={filtroAtendente ?? ""}
              placeholder={textos.filtros.atendente}
              opcoes={atendentes.map(([id, nome]) => ({
                valor: id,
                rotulo: nome,
              }))}
              onChange={(valor) => setFiltroAtendente(valor || null)}
            />
          )}
        </div>
      )}

      <ScrollArea className="min-h-0 flex-1 overflow-hidden" data-slot="lista-conversas-scroll">
        {isLoading ? (
          <div className="space-y-2 p-3">
            {Array.from({ length: 5 }).map((_, indice) => (
              <Skeleton key={indice} className="h-14 w-full" />
            ))}
          </div>
        ) : filtrados.length === 0 ? (
          <p className="p-4 text-center text-sm text-muted-foreground">
            {textos.cartao.vazio}
          </p>
        ) : (
          filtrados.map((cartao) => (
            <CartaoConversa
              key={cartao.atendimentoId}
              cartao={cartao}
              selecionado={cartao.atendimentoId === selecionadoId}
              onAbrirAtendimento={() => onAbrirAtendimento(cartao)}
            />
          ))
        )}
      </ScrollArea>
    </div>
  );
}
