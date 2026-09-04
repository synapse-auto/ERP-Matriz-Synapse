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
import {
  ABAS_ATENDENTE,
  ABAS_GESTAO,
  ehAbaDeAtendimento,
} from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";

import { cn } from "@/lib/utils";
import type { ChatContato } from "@/lib/chat-interno/types";

import { CartaoConversa } from "./cartao-conversa";
import { DialogoSelecionarPessoa } from "@/components/chat-interno/dialogo-selecionar-pessoa";
import { DialogoCriarGrupo } from "@/components/chat-interno/dialogo-criar-grupo";
import { RadioGroup, RadioItem } from "@/components/ui/radio-group";

const SELECAO_TODOS = "todos";

type Props = {
  selecionadoId: string | null;
  leadInicialId?: string | null;
  leadInicialGatilho?: number;
  visaoInicial?: VisaoAtendimento | null;
  visaoAtual?: VisaoAtendimento;
  onVisaoAlterada?: (visao: VisaoAtendimento) => void;
  onAtendimentosAtualizados?: (atendimentos: ItemInbox[]) => void;
  onAbrirAtendimento: (cartao: ItemInbox) => void;
  chatInternoHabilitado?: boolean;
  contatosInternos?: ChatContato[];
  contatosInternosCarregando?: boolean;
  contatosInternosErro?: boolean;
  onRecarregarContatos?: () => void;
  onCriarConversaInterna?: (usuarioId: string) => Promise<unknown>;
  onCriarGrupoInterno?: (nome: string, participantes: string[]) => Promise<unknown>;
  onNovoContato?: () => void;
  className?: string;
};

type VisaoDeAba = Exclude<VisaoAtendimento, "FINALIZADOS">;

const ROTULO_VISAO: Record<
  VisaoDeAba,
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
  visaoAtual,
  onVisaoAlterada,
  onAtendimentosAtualizados,
  onAbrirAtendimento,
  chatInternoHabilitado = false,
  contatosInternos = [],
  contatosInternosCarregando = false,
  contatosInternosErro = false,
  onRecarregarContatos,
  onCriarConversaInterna,
  onCriarGrupoInterno,
  onNovoContato,
  className,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos;
  const papel = useAuthStore((estado) => estado.papel);
  const papelAmplo = papel != null && papel !== "ATENDENTE";
  const abas = useMemo<VisaoDeAba[]>(
    () => (papelAmplo ? ABAS_GESTAO : ABAS_ATENDENTE) as VisaoDeAba[],
    [papelAmplo],
  );
  const [visaoEscolhida, setVisaoEscolhida] = useState<VisaoAtendimento | null>(
    visaoInicial ?? null,
  );
  // FINALIZADOS não entra em `abas`, mas é visão válida (menu). Sem este recorte, o fallback
  // para abas[0] expulsava o usuário da lista de finalizados (E136 × PR #71).
  const visaoSolicitavel = (candidata: VisaoAtendimento | null | undefined): candidata is VisaoAtendimento =>
    candidata != null && (candidata === "FINALIZADOS" || abas.includes(candidata as VisaoDeAba));
  const visao: VisaoAtendimento = visaoSolicitavel(visaoAtual)
    ? visaoAtual
    : visaoSolicitavel(visaoEscolhida)
      ? visaoEscolhida
      : abas[0];
  const abaAtiva = ehAbaDeAtendimento(visao) ? visao : "";
  const [busca, setBusca] = useState("");
  const [filtrosAbertos, setFiltrosAbertos] = useState(false);
  const [filtroEtapa, setFiltroEtapa] = useState<string | null>(null);
  const [filtroAtendente, setFiltroAtendente] = useState<string | null>(null);

  const { data, isLoading, hasNextPage, isFetchingNextPage, fetchNextPage } = useAtendimentos(visao);
  const cartoes = useMemo(() => (data ?? []).filter((item): item is ItemInbox => item != null), [data]);
  const { data: contagens } = useContagemDeAtendimentos();
  const abriuLeadInicial = useRef(false);
  const [novaInternaAberta, setNovaInternaAberta] = useState(false);
  const [novoGrupoAberto, setNovoGrupoAberto] = useState(false);
  const [finalizarTodosAberto, setFinalizarTodosAberto] = useState(false);
  const [selecaoFinalizacao, setSelecaoFinalizacao] = useState(SELECAO_TODOS);
  const [resultadoFinalizacao, setResultadoFinalizacao] = useState<{
    finalizados: number;
    recusados: number;
  } | null>(null);
  const finalizarTodos = useFinalizarAtendimentosVisiveis();
  const quantidadeFinalizavel = useQuantidadeAtendimentosFinalizaveis();
  const porAtendente = quantidadeFinalizavel.data?.porAtendente ?? [];
  const quantidadeSelecionada =
    selecaoFinalizacao === SELECAO_TODOS
      ? (quantidadeFinalizavel.data?.quantidade ?? 0)
      : (porAtendente.find((item) => item.atendenteId === selecaoFinalizacao)?.quantidade ?? 0);

  const fimDaLista = useRef<HTMLDivElement>(null);
  const paginaComCursor = visao === "TODOS" || visao === "ATIVOS" || visao === "FINALIZADOS";
  useEffect(() => {
    const alvo = fimDaLista.current;
    if (!alvo || !paginaComCursor || !hasNextPage) return;
    const observador = new IntersectionObserver(
      (entradas) => {
        if (entradas[0]?.isIntersecting && !isFetchingNextPage) void fetchNextPage();
      },
      { rootMargin: "240px" },
    );
    observador.observe(alvo);
    return () => observador.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage, paginaComCursor]);

  useEffect(() => {
    onVisaoAlterada?.(visao);
  }, [onVisaoAlterada, visao]);

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

  function escolherVisao(proxima: VisaoAtendimento) {
    setVisaoEscolhida(proxima);
    onVisaoAlterada?.(proxima);
  }

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
                <DropdownMenuItem onClick={() => escolherVisao("FINALIZADOS")}>
                  {textos.lista.finalizados}
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => {
                    setResultadoFinalizacao(null);
                    setSelecaoFinalizacao(SELECAO_TODOS);
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
              <DropdownMenu>
                <DropdownMenuTrigger
                  className="inline-flex min-h-10 min-w-10 items-center justify-center rounded-md border border-input bg-background text-sm font-medium hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
                  aria-label="Opções de chat interno"
                  title="Chat interno"
                >
                  <UsersRound className="size-(--tamanho-icone-interface)" aria-hidden />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem
                    onClick={() => {
                      setNovaInternaAberta(true);
                      onRecarregarContatos?.();
                    }}
                  >
                    {catalogo.chatInterno.novaConversa}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => {
                      setNovoGrupoAberto(true);
                      onRecarregarContatos?.();
                    }}
                  >
                    Criar grupo interno
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
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
        value={abaAtiva}
        onValueChange={(valor) => {
          escolherVisao(valor as VisaoAtendimento);
        }}
      >
        <TabsList
          variant="line"
          className={cn(
            "mx-4 mt-3 flex h-auto w-[calc(100%-2rem)] justify-start gap-2 overflow-x-auto [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden p-0 md:grid md:gap-1 md:overflow-x-hidden",
            papelAmplo ? "md:grid-cols-4" : "md:grid-cols-3",
          )}
        >
          {abas.map((item) => (
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
          {paginaComCursor && hasNextPage && (
            <div ref={fimDaLista} className="p-3 text-center text-xs text-muted-foreground" aria-live="polite">
              {isFetchingNextPage ? textos.lista.carregandoMais : textos.lista.carregarMais}
            </div>
          )}
        </div>
      </ScrollArea>

      {chatInternoHabilitado && (
        <>
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
          <DialogoCriarGrupo
            aberto={novoGrupoAberto}
            onFechar={() => setNovoGrupoAberto(false)}
            contatos={contatosInternos ?? []}
            onCriar={async (nome, participantes) => {
              if (onCriarGrupoInterno) {
                await onCriarGrupoInterno(nome, participantes);
              }
            }}
            textos={catalogo.chatInterno}
          />
        </>
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
                String(quantidadeSelecionada),
              )}
            </DialogDescription>
          </DialogHeader>
          {!resultadoFinalizacao && (
            <RadioGroup
              value={selecaoFinalizacao}
              onValueChange={(valor) => setSelecaoFinalizacao(valor)}
              aria-label={textos.finalizar.todosTitulo}
            >
              <RadioItem value={SELECAO_TODOS}>
                <span className="flex items-center justify-between gap-2">
                  <span>{textos.visoes.todos}</span>
                  <span className="text-muted-foreground">
                    {quantidadeFinalizavel.data?.quantidade ?? 0}
                  </span>
                </span>
              </RadioItem>
              {porAtendente.map((item) => (
                <RadioItem key={item.atendenteId} value={item.atendenteId}>
                  <span className="flex items-center justify-between gap-2">
                    <span>{item.nome}</span>
                    <span className="text-muted-foreground">{item.quantidade}</span>
                  </span>
                </RadioItem>
              ))}
            </RadioGroup>
          )}
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
                finalizarTodos.mutate(
                  selecaoFinalizacao === SELECAO_TODOS ? null : selecaoFinalizacao,
                  {
                    onSuccess: (resultado) =>
                      setResultadoFinalizacao({
                        finalizados: resultado.finalizados,
                        recusados: resultado.recusados,
                      }),
                  },
                )
              }
              disabled={
                finalizarTodos.isPending ||
                quantidadeFinalizavel.isLoading ||
                quantidadeSelecionada === 0
              }
            >
              {textos.finalizar.todosConfirmar.replace(
                "{quantidade}",
                String(quantidadeSelecionada),
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
