"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Plus, Search, SlidersHorizontal } from "lucide-react";

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
import type { ItemInbox, VisaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

import { CartaoConversa } from "./cartao-conversa";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

type Props = {
  selecionadoId: string | null;
  leadInicialId?: string | null;
  leadInicialGatilho?: number;
  visaoInicial?: VisaoAtendimento | null;
  onAtendimentosAtualizados?: (atendimentos: ItemInbox[]) => void;
  onAbrirAtendimento: (cartao: ItemInbox) => void;
  chatInternoHabilitado?: boolean;
  contatosInternos?: { id: string; nome: string }[];
  contatoInternoSelecionado?: string;
  onContatoInternoChange?: (valor: string) => void;
  onCriarConversaInterna?: () => void;
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
  leadInicialGatilho = 0,
  visaoInicial,
  onAtendimentosAtualizados,
  onAbrirAtendimento,
  chatInternoHabilitado = false,
  contatosInternos = [],
  contatoInternoSelecionado = "",
  onContatoInternoChange,
  onCriarConversaInterna,
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

  const { data, isLoading, hasNextPage, isFetchingNextPage, fetchNextPage } = useAtendimentos(visao);
  const cartoes = useMemo(() => (data ?? []).filter((item): item is ItemInbox => item != null), [data]);
  const { data: contagens } = useContagemDeAtendimentos();
  const abriuLeadInicial = useRef(false);
  const [contatoSelecionado, setContatoSelecionado] = useState("");
  const [novaInternaAberta, setNovaInternaAberta] = useState(false);

  const fimDaLista = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const alvo = fimDaLista.current;
    if (!alvo || visao !== "TODOS" || !hasNextPage) return;
    const observador = new IntersectionObserver(
      (entradas) => {
        if (entradas[0]?.isIntersecting && !isFetchingNextPage) void fetchNextPage();
      },
      { rootMargin: "240px" },
    );
    observador.observe(alvo);
    return () => observador.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage, visao]);

  useEffect(() => {
    onAtendimentosAtualizados?.(cartoes);
  }, [cartoes, onAtendimentosAtualizados]);

  useEffect(() => {
    abriuLeadInicial.current = false;
  }, [leadInicialGatilho, leadInicialId]);

  useEffect(() => {
    if (!leadInicialId || abriuLeadInicial.current) return;
    const cartao = cartoes.find((item) => item.tipo !== "EQUIPE_INTERNA" && item.leadId === leadInicialId);
    if (cartao) {
      abriuLeadInicial.current = true;
      onAbrirAtendimento(cartao);
    }
  }, [cartoes, leadInicialGatilho, leadInicialId, onAbrirAtendimento]);

  const etapas = useMemo(() => {
    const mapa = new Map<string, string>();
    for (const cartao of cartoes) {
      if (cartao.tipo !== "EQUIPE_INTERNA" && cartao.etapaId && cartao.etapaNome) {
        mapa.set(cartao.etapaId, cartao.etapaNome);
      }
    }
    return Array.from(mapa.entries());
  }, [cartoes]);

  const atendentes = useMemo(() => {
    const mapa = new Map<string, string>();
    for (const cartao of cartoes) {
      if (cartao.tipo !== "EQUIPE_INTERNA" && cartao.atendenteId && cartao.atendenteNome) {
        mapa.set(cartao.atendenteId, cartao.atendenteNome);
      }
    }
    return Array.from(mapa.entries());
  }, [cartoes]);

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase("pt-BR");
    return cartoes.filter((cartao) => {
      const correspondeABusca =
        !termo ||
        [cartao.tipo === "EQUIPE_INTERNA" ? cartao.nome : cartao.leadNome,
          cartao.tipo === "EQUIPE_INTERNA" ? cartao.conversaId : cartao.leadEmpresa,
          cartao.tipo === "EQUIPE_INTERNA" ? cartao.participantes : cartao.atendimentoId].some(
          (valor) => valor?.toLocaleLowerCase("pt-BR").includes(termo),
        );
      return (
        correspondeABusca &&
        (cartao.tipo === "EQUIPE_INTERNA" || !filtroEtapa || cartao.etapaId === filtroEtapa) &&
        (cartao.tipo === "EQUIPE_INTERNA" || !filtroAtendente || cartao.atendenteId === filtroAtendente)
      );
    });
  }, [busca, cartoes, filtroAtendente, filtroEtapa]);

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden border-r border-border bg-background">
      <div className="px-4 pt-4">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-lg font-extrabold tracking-tight text-foreground">
            {catalogo.menu.itens.atendimentos}
          </h1>
          <div className="flex items-center gap-1">
            {chatInternoHabilitado && (
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label={catalogo.chatInterno.novaConversa}
                  aria-expanded={novaInternaAberta}
                  onClick={() => {
                    if (novaInternaAberta) {
                      setNovaInternaAberta(false);
                      setContatoSelecionado("");
                      onContatoInternoChange?.("");
                    } else {
                      setNovaInternaAberta(true);
                    }
                  }}
                >
                  <Plus className="size-4" aria-hidden />
                </Button>
                {novaInternaAberta && <>
                  <SelectContato contatos={contatosInternos} valor={contatoInternoSelecionado || contatoSelecionado} onChange={onContatoInternoChange ?? setContatoSelecionado} placeholder={catalogo.chatInterno.selecionarPessoa} />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label={catalogo.chatInterno.novaConversa}
                    disabled={!(contatoInternoSelecionado || contatoSelecionado)}
                    onClick={() => {
                      onCriarConversaInterna?.();
                      setNovaInternaAberta(false);
                      setContatoSelecionado("");
                      onContatoInternoChange?.("");
                    }}
                  >
                    <Plus className="size-4" aria-hidden />
                  </Button>
                </>}
              </div>
            )}
            <Button type="button" variant="outline" size="icon-sm" aria-label={textos.lista.filtros} aria-pressed={filtrosAbertos} disabled={etapas.length === 0 && atendentes.length <= 1} onClick={() => setFiltrosAbertos((abertos) => !abertos)}>
              <SlidersHorizontal className="size-4" aria-hidden />
            </Button>
          </div>
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
        <TabsList variant="line" className="mx-4 mt-3 grid h-auto w-[calc(100%-2rem)] grid-cols-4 p-0">
          {VISOES.map((item) => (
            <TabsTrigger
              key={item}
              value={item}
              className="min-w-0 gap-1 rounded-none px-0.5 pt-1 pb-2.5 text-[0.6875rem] shadow-none data-active:after:bg-primary"
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
              key={cartao.tipo === "EQUIPE_INTERNA" ? `equipe-${cartao.conversaId}` : `cliente-${cartao.leadId}`}
              cartao={cartao}
              selecionado={cartao.tipo === "EQUIPE_INTERNA" ? cartao.conversaId === selecionadoId : cartao.leadId === selecionadoId}
              onAbrirAtendimento={() => onAbrirAtendimento(cartao)}
            />
          ))
        )}
        {visao === "TODOS" && hasNextPage && (
          <div ref={fimDaLista} className="p-3 text-center text-xs text-muted-foreground" aria-live="polite">
            {isFetchingNextPage ? textos.lista.carregandoMais : textos.lista.carregarMais}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}

function SelectContato({
  contatos,
  valor,
  onChange,
  placeholder,
}: { contatos: { id: string; nome: string }[]; valor: string; onChange: (valor: string) => void; placeholder: string }) {
  return (
    <Select value={valor} onValueChange={(novoValor) => onChange(novoValor ?? "")}>
      <SelectTrigger className="h-9 w-32" aria-label={placeholder}><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent>{contatos.map((contato) => <SelectItem key={contato.id} value={contato.id}>{contato.nome}</SelectItem>)}</SelectContent>
    </Select>
  );
}
