"use client";

import { useId, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { CalendarX2, ChevronDown, ChevronLeft, ChevronRight, Info, Search, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { Skeleton } from "@/components/ui/skeleton";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useTextos } from "@/lib/config/textos-provider";
import { ErroDeApi } from "@/lib/api/errors";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useCapacidades } from "@/lib/gestao/use-capacidades";
import { useMinhasPermissoes } from "@/lib/gestao/use-gestao";
import { useListaEmCartoes } from "./use-lista-em-cartoes";
import { listarCancelamentos, obterCancelamento, obterConfiguracaoCancelamentos,
  type ConfiguracaoCancelamentos, type FiltrosCancelamentos, type Cancelamento } from "@/lib/cancelamentos/api";
import { preencher } from "@/components/gestao/apoio";

type Textos = ReturnType<typeof useTextos>["cancelamentos"];

function Falha({ erro, tentar }: { erro: unknown; tentar: () => void }) {
  const t = useTextos().cancelamentos;
  const codigo = erro instanceof ErroDeApi ? erro.problema?.codigo : null;
  const status = erro instanceof ErroDeApi ? erro.status : null;
  const ausente = codigo === "INTEGRACAO_NAO_CONFIGURADA";
  const mensagem = ausente ? t.naoConfigurada : codigo === "FONTE_INDISPONIVEL" ? t.indisponivel
    : status === 403 ? t.semAcesso : status === 404 ? t.naoEncontrado : status === 400 ? t.filtroInvalido : t.erro;
  return <div role="alert" className="space-y-3 rounded-xl border bg-card p-6 text-sm">
    <p className="font-medium text-foreground">{mensagem}</p>
    {!ausente && status !== 403 && status !== 404 && <Button variant="outline" onClick={tentar}>{t.tentar}</Button>}
  </div>;
}

function Carregando() {
  const t = useTextos().cancelamentos;
  return <div role="status" aria-busy="true" aria-label={t.carregando} className="space-y-5 rounded-xl border bg-card p-5">
    <span className="sr-only">{t.carregando}</span>
    <Skeleton className="h-4 w-36 motion-reduce:animate-none" />
    <div aria-hidden className="space-y-4">{[0, 1, 2].map(linha => <div key={linha} className="flex gap-5 border-t pt-4">
      <Skeleton className="h-9 w-1/3 motion-reduce:animate-none" /><Skeleton className="h-9 flex-1 motion-reduce:animate-none" />
    </div>)}</div>
  </div>;
}

export function PaginaCancelamentos() {
  const t = useTextos().cancelamentos;
  const { pode } = useCapacidades();
  const sessao = useAuthStore(e => `${e.usuarioId}:${e.papel}`);
  const permissoes = useMinhasPermissoes();
  const identidade = `${sessao}:${permissoes.data?.revisao ?? ""}`;
  const configuracao = useQuery({ queryKey: ["cancelamentos", identidade, "configuracao"],
    queryFn: ({ signal }) => obterConfiguracaoCancelamentos(signal), enabled: pode("cancelamentos.ver"), retry: false });
  return <div className="min-w-0 flex-1 overflow-auto bg-background">
    <div className="mx-auto w-full max-w-7xl space-y-5 p-4 md:p-6">
      <header className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"><CalendarX2 aria-hidden className="size-5" /></span>
        <div className="min-w-0 space-y-1"><h1 className="text-xl font-semibold text-foreground">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p></div>
      </header>
      {configuracao.isPending ? <Carregando /> : configuracao.isError
        ? <Falha erro={configuracao.error} tentar={() => void configuracao.refetch()} />
        : <Consulta key={`${identidade}:${JSON.stringify(configuracao.data)}`} config={configuracao.data} identidade={identidade} />}
    </div>
  </div>;
}

function filtrosIguais(a: FiltrosCancelamentos, b: FiltrosCancelamentos) {
  return (Object.keys(a) as (keyof FiltrosCancelamentos)[]).every(chave => a[chave] === b[chave]);
}

function Consulta({ config, identidade }: { config: ConfiguracaoCancelamentos; identidade: string }) {
  const t = useTextos().cancelamentos;
  const id = useId();
  const telaEstreita = useListaEmCartoes();
  const { pode } = useCapacidades();
  const iniciais: FiltrosCancelamentos = { inicio: config.inicioPadrao, fim: config.fimPadrao,
    base: "REGISTRO", descricao: "TODAS", profissional: "", tipo: "", nome: "" };
  const [rascunho, setRascunho] = useState<FiltrosCancelamentos>(iniciais);
  const [consulta, setConsulta] = useState({ filtros: rascunho, pagina: 0 });
  const [maisFiltros, mostrarMaisFiltros] = useState(false);
  const [ajudaAberta, mostrarAjuda] = useState(false);
  const [erroPeriodo, setErroPeriodo] = useState<string | null>(null);
  const [selecionado, selecionar] = useState<string | null>(null);
  const lista = useQuery({ queryKey: ["cancelamentos", identidade, "lista", consulta, config.tamanhoPagina],
    queryFn: ({ signal }) => listarCancelamentos(consulta.filtros, consulta.pagina, config.tamanhoPagina, signal),
    enabled: pode("cancelamentos.ver"), retry: false });
  const detalhe = useQuery({ queryKey: ["cancelamentos", identidade, "detalhe", selecionado],
    queryFn: ({ signal }) => obterCancelamento(selecionado!, signal), enabled: selecionado !== null && pode("cancelamentos.ver"), retry: false });
  const extrasAtivos = [consulta.filtros.nome.trim(), consulta.filtros.profissional.trim(), consulta.filtros.tipo.trim(),
    consulta.filtros.descricao !== "TODAS"].filter(Boolean).length;
  const consultaAlterada = !filtrosIguais(consulta.filtros, iniciais);
  const podeLimpar = consultaAlterada || !filtrosIguais(rascunho, iniciais) || consulta.pagina > 0;

  function aplicar(e: FormEvent) {
    e.preventDefault();
    // Mesmos dias locais e limite inclusivo do servidor; nao muda fuso/contrato.
    const inicio = Date.parse(`${rascunho.inicio}T00:00:00Z`);
    const fim = Date.parse(`${rascunho.fim}T00:00:00Z`);
    const erro = !Number.isFinite(inicio) || !Number.isFinite(fim) ? t.periodoObrigatorio
      : fim < inicio ? t.periodoInvertido
      : (fim - inicio) / 86_400_000 >= config.maximoDias ? preencher(t.periodoExcedido, { dias: config.maximoDias }) : null;
    setErroPeriodo(erro);
    if (erro) { document.getElementById(`${id}-inicio`)?.focus(); return; }
    selecionar(null); setConsulta({ filtros: { ...rascunho }, pagina: 0 });
  }
  function limpar() {
    setRascunho(iniciais); setConsulta({ filtros: iniciais, pagina: 0 }); selecionar(null);
    setErroPeriodo(null); mostrarMaisFiltros(false);
  }
  function campo<K extends keyof FiltrosCancelamentos>(chave: K, valor: FiltrosCancelamentos[K]) {
    setRascunho(atual => ({ ...atual, [chave]: valor }));
    if (chave === "inicio" || chave === "fim") setErroPeriodo(null);
  }
  const data = (valor: string | null) => valor ? new Intl.DateTimeFormat("pt-BR", {
    timeZone: config.fusoHorario, dateStyle: "short", timeStyle: "short",
  }).format(new Date(valor)) : t.naoInformado;
  const paginar = !lista.isPending && (consulta.pagina > 0 || (lista.isSuccess && lista.data.itens.length > 0 && lista.data.temProxima));
  return <>
    <form onSubmit={aplicar} className="min-w-0 overflow-hidden rounded-xl border bg-card">
      <div className="flex flex-col gap-3 p-4 md:p-5">
        <div className="flex items-center gap-1.5"><h2 className="text-sm font-semibold text-foreground">{t.periodo}</h2>
          <Tooltip open={ajudaAberta} onOpenChange={mostrarAjuda}><TooltipTrigger aria-describedby={ajudaAberta ? `${id}-ajuda-periodo` : undefined} render={<Button type="button" size="icon-sm" variant="ghost" aria-label={t.ajudaPeriodo} />}>
            <Info aria-hidden className="size-4 text-muted-foreground" />
          </TooltipTrigger><TooltipContent id={`${id}-ajuda-periodo`} role="tooltip"><span>{preencher(t.limitePeriodo, { dias: config.maximoDias })}<br />{preencher(t.fuso, { fuso: config.fusoHorario })}</span></TooltipContent></Tooltip>
        </div>
        <fieldset aria-describedby={erroPeriodo ? `${id}-erro-periodo` : undefined} aria-invalid={!!erroPeriodo}
          className="grid min-w-0 grid-cols-2 gap-3 sm:grid-cols-3">
          <legend className="sr-only">{t.periodo}</legend>
          <label htmlFor={`${id}-base`} className="col-span-2 grid min-w-0 gap-2 text-sm font-medium sm:col-span-1">{t.base}
            <Seletor id={`${id}-base`} valor={rascunho.base} placeholder={t.base} ariaLabel={t.base}
              className="rounded-lg font-normal data-[size=default]:h-9"
              opcoes={[{ valor: "REGISTRO", rotulo: t.registro }, { valor: "AGENDAMENTO", rotulo: t.agendamento }, { valor: "CANCELAMENTO", rotulo: t.canceladoEm }]}
              obrigatorio onChange={v => { if (v) campo("base", v as FiltrosCancelamentos["base"]); }} /></label>
          <label htmlFor={`${id}-inicio`} className="grid min-w-0 gap-2 text-sm font-medium">{t.inicio}
            <SeletorData id={`${id}-inicio`} className="h-9 rounded-lg" obrigatorio valor={rascunho.inicio} placeholder={t.inicio} onChange={v => campo("inicio", v)} /></label>
          <label htmlFor={`${id}-fim`} className="grid min-w-0 gap-2 text-sm font-medium">{t.fim}
            <SeletorData id={`${id}-fim`} className="h-9 rounded-lg" obrigatorio valor={rascunho.fim} placeholder={t.fim} onChange={v => campo("fim", v)} /></label>
        </fieldset>
        {erroPeriodo && <p id={`${id}-erro-periodo`} role="alert" className="text-sm text-destructive">{erroPeriodo}</p>}
        <div id={`${id}-extras`} hidden={!maisFiltros}>
          <div className="grid min-w-0 grid-cols-1 gap-4 border-t pt-4 sm:grid-cols-2 xl:grid-cols-4">
            <label className="grid min-w-0 gap-2 text-sm font-medium">{t.nome}<Input className="h-9 font-normal" maxLength={config.maximoBusca} value={rascunho.nome} onChange={e => campo("nome", e.target.value)} /></label>
            <label className="grid min-w-0 gap-2 text-sm font-medium">{t.profissional}<Input className="h-9 font-normal" maxLength={config.maximoBusca} value={rascunho.profissional} onChange={e => campo("profissional", e.target.value)} /></label>
            <label className="grid min-w-0 gap-2 text-sm font-medium">{t.tipo}<Input className="h-9 font-normal" maxLength={config.maximoBusca} value={rascunho.tipo} onChange={e => campo("tipo", e.target.value)} /></label>
            <label htmlFor={`${id}-descricao`} className="grid min-w-0 gap-2 text-sm font-medium">{t.descricaoFiltro}<Seletor id={`${id}-descricao`} valor={rascunho.descricao} placeholder={t.descricaoFiltro} ariaLabel={t.descricaoFiltro}
              className="rounded-lg font-normal data-[size=default]:h-9"
              opcoes={[{ valor: "TODAS", rotulo: t.todas }, { valor: "COM_DESCRICAO", rotulo: t.comDescricao }, { valor: "SEM_DESCRICAO", rotulo: t.semDescricao }]}
              obrigatorio onChange={v => { if (v) campo("descricao", v as FiltrosCancelamentos["descricao"]); }} /></label>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t px-4 py-3 md:px-5">
        <Button type="button" variant="ghost" className="gap-2" aria-expanded={maisFiltros} aria-controls={`${id}-extras`} onClick={() => mostrarMaisFiltros(v => !v)}>
          <SlidersHorizontal aria-hidden className="size-4" />{t.maisFiltros}
          {extrasAtivos > 0 && <><span aria-hidden className="rounded-md bg-primary/10 px-1.5 text-xs font-semibold text-primary">{extrasAtivos}</span><span className="sr-only">{preencher(t.filtrosAtivos, { quantidade: extrasAtivos })}</span></>}
          <ChevronDown aria-hidden className={`size-4 ${maisFiltros ? "rotate-180" : ""}`} />
        </Button>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto"><Button type="button" variant="outline" className="h-9 flex-1 sm:flex-none" disabled={!podeLimpar} onClick={limpar}>{t.limpar}</Button>
          <Button type="submit" className="h-9 flex-1 sm:flex-none" disabled={lista.isFetching && filtrosIguais(rascunho, consulta.filtros)}><Search aria-hidden className="size-4" />{t.aplicar}</Button></div>
      </div>
    </form>

    <section aria-label={t.resultados} className="space-y-3" aria-busy={lista.isFetching}>
      {lista.isPending ? <Carregando /> : lista.isError ? <Falha erro={lista.error} tentar={() => void lista.refetch()} />
        : lista.data.itens.length === 0 ? <div role="status" className="flex min-h-56 flex-col items-center justify-center gap-3 rounded-xl border bg-card px-6 py-9 text-center">
          <span className="flex size-11 items-center justify-center rounded-lg bg-muted"><CalendarX2 aria-hidden className="size-5 text-muted-foreground" /></span>
          <div className="max-w-md space-y-1"><h2 className="text-base font-semibold text-foreground">{consulta.pagina > 0 ? t.paginaVazia : extrasAtivos > 0 ? t.vazio : t.vazioPeriodo}</h2>
            <p className="text-sm text-muted-foreground">{consulta.pagina > 0 ? t.paginaVaziaAjuda : extrasAtivos > 0 ? t.vazioAjuda : t.vazioPeriodoAjuda}</p></div>
          {consultaAlterada && <Button type="button" variant="outline" onClick={limpar}>{t.limpar}</Button>}
        </div> : <>
          <h2 className="text-sm font-semibold text-foreground">{t.resultados}</h2>
          {telaEstreita ? <div className="grid gap-3 md:grid-cols-2">{lista.data.itens.map(item =>
            <article key={item.id} aria-label={item.contatoNome || t.naoInformado} className="flex min-w-0 flex-col gap-4 rounded-xl border bg-card p-4">
              <Detalhes item={item} t={t} data={data} resumido />
              <Button variant="outline" className="w-full" onClick={() => selecionar(item.id)}>{t.detalhes}</Button>
            </article>)}</div>
            : <div className="overflow-hidden rounded-xl border bg-card"><table className="w-full table-fixed text-left text-sm">
              <thead className="border-b bg-muted/40 text-xs text-muted-foreground"><tr>
                <th scope="col" className="w-1/4 p-3 font-medium">{t.contato}</th><th scope="col" className="p-3 font-medium">{t.profissional}</th>
                <th scope="col" className="p-3 font-medium">{t.agendamento}</th><th scope="col" className="p-3 font-medium">{t.registro}</th>
                <th scope="col" className="p-3 font-medium">{t.descricaoFiltro}</th><th scope="col" className="w-28 p-3 font-medium"><span className="sr-only">{t.detalhes}</span></th>
              </tr></thead><tbody>{lista.data.itens.map(item => <tr key={item.id} className="border-b last:border-0 hover:bg-muted/30">
                <td className="min-w-0 p-3 align-top [overflow-wrap:anywhere]"><p className="line-clamp-2 font-semibold text-foreground">{item.contatoNome || t.naoInformado}</p>
                  <p className="mt-1 text-xs text-muted-foreground"><span className="sr-only">{t.tipo}: </span>{item.tipoAgendamento || t.naoInformado}</p></td>
                <td className="p-3 align-top [overflow-wrap:anywhere]"><span className="line-clamp-2">{item.profissionalNome || t.naoInformado}</span></td>
                <td className="p-3 align-top text-xs tabular-nums [overflow-wrap:anywhere]">{data(item.agendadoPara)}</td>
                <td className="p-3 align-top text-xs text-muted-foreground tabular-nums [overflow-wrap:anywhere]">{data(item.recebidoEm ?? null)}</td>
                <td className="p-3 align-top [overflow-wrap:anywhere]"><span className="line-clamp-2 whitespace-pre-wrap text-muted-foreground">{item.descricao?.trim() ? item.descricao : t.semDescricao}</span></td>
                <td className="p-3 align-top"><Button variant="ghost" size="sm" onClick={() => selecionar(item.id)}>{t.detalhes}</Button></td>
              </tr>)}</tbody></table></div>}
        </>}
      {paginar && <nav aria-label={t.paginacao} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card px-3 py-2 text-sm">
        <span className="text-muted-foreground">{preencher(t.pagina, { pagina: consulta.pagina + 1 })}</span>
        <div className="flex gap-2"><Button variant="outline" size="sm" disabled={consulta.pagina === 0} onClick={() => setConsulta(v => ({ ...v, pagina: v.pagina - 1 }))}><ChevronLeft aria-hidden className="size-4" />{t.anterior}</Button>
          <Button variant="outline" size="sm" disabled={!lista.data?.temProxima || lista.isError || consulta.pagina + 1 >= config.maximoPaginas}
            onClick={() => setConsulta(v => ({ ...v, pagina: v.pagina + 1 }))}>{t.proxima}<ChevronRight aria-hidden className="size-4" /></Button></div>
      </nav>}
      {lista.isSuccess && lista.data.temProxima && consulta.pagina + 1 >= config.maximoPaginas && <p role="status" className="text-sm text-muted-foreground">{t.limitePaginas}</p>}
    </section>

    <Dialog open={selecionado !== null && pode("cancelamentos.ver")} onOpenChange={aberto => { if (!aberto) selecionar(null); }}>
      <DialogContent showCloseButton={false} className="flex max-h-[85dvh] flex-col overflow-hidden sm:max-w-xl">
        <DialogHeader className="shrink-0"><DialogTitle>{t.detalheTitulo}</DialogTitle><DialogDescription>{t.detalheDescricao}</DialogDescription></DialogHeader>
        <div className="min-h-0 overflow-y-auto">
          {detalhe.isPending ? <Carregando /> : detalhe.isError ? <Falha erro={detalhe.error} tentar={() => void detalhe.refetch()} />
            : <Detalhes item={detalhe.data} t={t} data={data} />}
        </div>
        {detalhe.isSuccess && !(detalhe.data.leadId && detalhe.data.atendimentoId && pode("atendimentos.ver")) && <p className="shrink-0 text-xs text-muted-foreground">{t.vinculoIndisponivel}</p>}
        <DialogFooter className="shrink-0"><Button variant="outline" onClick={() => selecionar(null)}>{t.fechar}</Button>
          {detalhe.isSuccess && detalhe.data.leadId && detalhe.data.atendimentoId && pode("atendimentos.ver") &&
            <Link className={buttonVariants({ variant: "outline" })} href={`/atendimentos?leadId=${encodeURIComponent(detalhe.data.leadId)}&atendimentoId=${encodeURIComponent(detalhe.data.atendimentoId)}`}>{t.abrirAtendimento}</Link>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}

function Detalhes({ item, t, data, resumido = false }: { item: Cancelamento; t: Textos; data: (v: string | null) => string; resumido?: boolean }) {
  return <dl className={`grid min-w-0 grid-cols-2 content-start gap-x-5 gap-y-4 text-sm ${resumido ? "flex-1" : ""}`}>{[
    [t.contato, item.contatoNome], [t.profissional, item.profissionalNome], [t.tipo, item.tipoAgendamento],
    [t.agendamento, data(item.agendadoPara)], [t.registro, data(item.recebidoEm ?? null)],
    [t.canceladoEm, data(item.canceladoEm ?? null)], [t.descricaoCampo, item.descricao?.trim() ? item.descricao : t.semDescricao],
  ].map(([rotulo, valor], indice) => <div key={rotulo} className={`min-w-0 space-y-1 ${indice === 0 || indice === 6 ? "col-span-2" : ""}`}>
    <dt className="text-xs text-muted-foreground">{rotulo}</dt>
    <dd className={`whitespace-pre-wrap text-foreground [overflow-wrap:anywhere] ${indice === 0 ? "font-semibold" : ""} ${resumido && [0, 1, 2, 6].includes(indice) ? "line-clamp-2" : ""}`}>{valor || t.naoInformado}</dd>
  </div>)}</dl>;
}
