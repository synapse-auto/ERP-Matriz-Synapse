"use client";

import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import { MoreHorizontal, Search, SlidersHorizontal, UserPlus, UsersRound } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Seletor } from "@/components/ui/seletor";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useAtendimentos,
  useContagemDeAtendimentos,
} from "@/lib/atendimento/use-atendimentos";
import {
  useFinalizarAtendimentosVisiveis,
  useQuantidadeAtendimentosFinalizaveis,
} from "@/lib/atendimento/use-transferir-finalizar";
import type { ItemInbox, VisaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";

import { cn } from "@/lib/utils";
import type { ChatContato } from "@/lib/chat-interno/types";

import { CartaoConversa } from "./cartao-conversa";
import { DialogoSelecionarPessoa } from "@/components/chat-interno/dialogo-selecionar-pessoa";

type Props = {
  selecionadoId: string | null;
  leadInicialId?: string | null;
  leadInicialGatilho?: number;
  visaoInicial?: VisaoAtendimento | null;
  onAtendimentosAtualizados?: (atendimentos: ItemInbox[]) => void;
  onAbrirAtendimento: (cartao: ItemInbox) => void;
  chatInternoHabilitado?: boolean;
  contatosInternos?: ChatContato[];
  contatosInternosCarregando?: boolean;
  contatosInternosErro?: boolean;
  onRecarregarContatos?: () => void;
  onCriarConversaInterna?: (usuarioId: string) => Promise<unknown>;
  onNovoContato?: () => void;
  className?: string;
};

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
 * A lista de visões acompanha o papel da mesma sessão que a Agenda e a Sidebar usam. O servidor
 * continua sendo a autoridade final: esta lista só evita oferecer uma visão que a API recusaria.
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
  contatosInternosCarregando = false,
  contatosInternosErro = false,
  onRecarregarContatos,
  onCriarConversaInterna,
  onNovoContato,
  className,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos;
  const papel = useAuthStore((estado) => estado.papel);
  const papelAmplo = papel != null && papel !== "ATENDENTE";
  const visoes = useMemo<VisaoAtendimento[]>(
    () => papelAmplo
      ? ["TODOS", "ATIVOS", "PENDENTES", "POTENCIAIS"]
      : ["PENDENTES", "ATIVOS", "POTENCIAIS"],
    [papelAmplo],
  );
  const [visaoEscolhida, setVisaoEscolhida] = useState<VisaoAtendimento | null>(
    visaoInicial ?? null,
  );
  const visao =
    visaoEscolhida && visoes.includes(visaoEscolhida)
      ? visaoEscolhida
      : visoes[0];
  const [busca, setBusca] = useState("");
  const [filtrosAbertos, setFiltrosAbertos] = useState(false);
  const [filtroEtapa, setFiltroEtapa] = useState<string | null>(null);
  const [filtroAtendente, setFiltroAtendente] = useState<string | null>(null);

  const { data, isLoading, hasNextPage, isFetchingNextPage, fetchNextPage } = useAtendimentos(visao);
  const cartoes = useMemo(() => (data ?? []).filter((item): item is ItemInbox => item != null), [data]);
  const { data: contagens } = useContagemDeAtendimentos();
  const abriuLeadInicial = useRef(false);
  const [novaInternaAberta, setNovaInternaAberta] = useState(false);
  const [finalizarTodosAberto, setFinalizarTodosAberto] = useState(false);
  const [resultadoFinalizacao, setResultadoFinalizacao] = useState<{
    finalizados: number;
    recusados: number;
  } | null>(null);
  const finalizarTodos = useFinalizarAtendimentosVisiveis();
  const quantidadeFinalizavel = useQuantidadeAtendimentosFinalizaveis();

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
  const indicePrimeiroFinalizado = visao === "TODOS"
    ? filtrados.findIndex(
        (cartao) =>
          cartao.tipo !== "EQUIPE_INTERNA"
          && (cartao.atendimentoAtivoId === null
            || (cartao.atendimentoAtivoId === undefined && cartao.status === "FINALIZADO")),
      )
    : -1;

  return (
    <div className={cn("flex h-full min-h-0 flex-col overflow-hidden border-r border-border bg-background", className)}>
      <div className="px-4 pt-4">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-lg font-bold tracking-tight text-foreground">
            {catalogo.menu.itens.atendimentos}
          </h1>
          <div className="flex items-center gap-1">
            <DropdownMenu>
              <DropdownMenuTrigger
                className="inline-flex size-10 items-center justify-center rounded-md text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
                aria-label={textos.finalizar.todosMenu}
              >
                <MoreHorizontal className="size-(--tamanho-icone-interface)" aria-hidden />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  onClick={() => {
                    setResultadoFinalizacao(null);
                    setFinalizarTodosAberto(true);
                  }}
                  disabled={
                    quantidadeFinalizavel.isLoading ||
                    quantidadeFinalizavel.data?.quantidade === 0
                  }
                >
                  {textos.finalizar.todos}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              className="min-h-10 min-w-10"
              aria-label={textos.novoContato.botao}
              onClick={onNovoContato}
            >
              <UserPlus className="size-(--tamanho-icone-interface)" aria-hidden />
            </Button>
            {chatInternoHabilitado && (
              <Button
                type="button"
                variant="outline"
                size="icon-sm"
                className="min-h-10 min-w-10"
                aria-label={catalogo.chatInterno.novaConversa}
                title={catalogo.chatInterno.novaConversa}
                aria-expanded={novaInternaAberta}
                onClick={() => {
                  setNovaInternaAberta(true);
                  onRecarregarContatos?.();
                }}
              >
                <UsersRound className="size-(--tamanho-icone-interface)" aria-hidden />
              </Button>
            )}
            <Button type="button" variant="outline" size="icon-sm" className="min-h-10 min-w-10" aria-label={textos.lista.filtros} aria-pressed={filtrosAbertos} disabled={etapas.length === 0 && atendentes.length <= 1} onClick={() => setFiltrosAbertos((abertos) => !abertos)}>
              <SlidersHorizontal className="size-(--tamanho-icone-interface)" aria-hidden />
            </Button>
          </div>
        </div>
        <div className="relative mt-3">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-(--tamanho-icone-interface) -translate-y-1/2 text-muted-foreground" />
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
        <TabsList
          variant="line"
          className={cn(
            "mx-4 mt-3 flex h-auto w-[calc(100%-2rem)] justify-start gap-2 overflow-x-auto [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden p-0 md:grid md:gap-1 md:overflow-x-hidden",
            papelAmplo ? "md:grid-cols-4" : "md:grid-cols-3",
          )}
        >
          {visoes.map((item) => (
            <TabsTrigger
              key={item}
              value={item}
              className="min-w-0 shrink-0 gap-1 rounded-full border border-border px-3 py-1.5 text-[0.75rem] shadow-none after:hidden data-active:border-primary data-active:bg-primary data-active:text-primary-foreground md:rounded-none md:border-0 md:px-0.5 md:pt-1 md:pb-2.5 md:text-[0.6875rem] md:data-active:bg-transparent md:data-active:text-foreground md:data-active:after:bg-primary md:after:opacity-0 md:data-active:after:opacity-100"
            >
              {textos.visoes[ROTULO_VISAO[item]]}
              {contagens && (
                <Badge
                  variant={item === visao ? "default" : "secondary"}
                  className="shrink-0 px-1 text-[0.625rem] data-active:bg-primary-foreground/20"
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
        <div className="pt-4" data-slot="lista-conversas-itens">
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
            filtrados.map((cartao, indice) => (
              <Fragment
                key={cartao.tipo === "EQUIPE_INTERNA" ? `equipe-${cartao.conversaId}` : `cliente-${cartao.leadId}`}
              >
                {indice === indicePrimeiroFinalizado && (
                  <div
                    className="mx-4 mb-1 mt-4 flex items-center gap-3 text-xs font-semibold text-muted-foreground"
                    role="separator"
                    aria-label={textos.lista.finalizados}
                  >
                    <span>{textos.lista.finalizados}</span>
                    <span className="h-px flex-1 bg-border" aria-hidden />
                  </div>
                )}
                <CartaoConversa
                  cartao={cartao}
                  selecionado={cartao.tipo === "EQUIPE_INTERNA" ? cartao.conversaId === selecionadoId : cartao.leadId === selecionadoId}
                  onAbrirAtendimento={() => onAbrirAtendimento(cartao)}
                />
              </Fragment>
            ))
          )}
          {visao === "TODOS" && hasNextPage && (
            <div ref={fimDaLista} className="p-3 text-center text-xs text-muted-foreground" aria-live="polite">
              {isFetchingNextPage ? textos.lista.carregandoMais : textos.lista.carregarMais}
            </div>
          )}
        </div>
      </ScrollArea>

      {chatInternoHabilitado && (
        <DialogoSelecionarPessoa
          aberto={novaInternaAberta}
          onFechar={() => setNovaInternaAberta(false)}
          contatos={contatosInternos}
          carregando={contatosInternosCarregando}
          erro={contatosInternosErro}
          onTentarNovamente={onRecarregarContatos}
          onSelecionar={(usuarioId) => onCriarConversaInterna?.(usuarioId) ?? Promise.resolve()}
          textos={catalogo.chatInterno}
        />
      )}

      <Dialog
        open={finalizarTodosAberto}
        onOpenChange={(novo) => !novo && setFinalizarTodosAberto(false)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{textos.finalizar.todosTitulo}</DialogTitle>
            <DialogDescription>
              {textos.finalizar.todosDescricao.replace(
                "{quantidade}",
                String(quantidadeFinalizavel.data?.quantidade ?? 0),
              )}
            </DialogDescription>
          </DialogHeader>
          {resultadoFinalizacao ? (
            <p role="status" className="text-sm text-foreground">
              {textos.finalizar.todosResultado
                .replace("{finalizados}", String(resultadoFinalizacao.finalizados))
                .replace("{recusados}", String(resultadoFinalizacao.recusados))}
            </p>
          ) : finalizarTodos.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {textos.finalizar.todosErro}
            </p>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setFinalizarTodosAberto(false)}
              disabled={finalizarTodos.isPending}
            >
              {textos.finalizar.todosCancelar}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() =>
                finalizarTodos.mutate(undefined, {
                  onSuccess: (resultado) =>
                    setResultadoFinalizacao({
                      finalizados: resultado.finalizados,
                      recusados: resultado.recusados,
                    }),
                })
              }
              disabled={
                finalizarTodos.isPending ||
                quantidadeFinalizavel.isLoading ||
                !quantidadeFinalizavel.data?.quantidade
              }
            >
              {textos.finalizar.todosConfirmar.replace(
                "{quantidade}",
                String(quantidadeFinalizavel.data?.quantidade ?? 0),
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
