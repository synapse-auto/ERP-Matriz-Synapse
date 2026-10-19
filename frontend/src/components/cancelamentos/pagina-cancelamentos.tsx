"use client";

import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { CalendarX2 } from "lucide-react";
import Link from "next/link";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useTextos } from "@/lib/config/textos-provider";
import { ErroDeApi } from "@/lib/api/errors";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useCapacidades } from "@/lib/gestao/use-capacidades";
import { useMinhasPermissoes } from "@/lib/gestao/use-gestao";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
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
  return <div role="status" className="space-y-3 rounded-xl border bg-card p-6 text-sm">
    <p className="font-semibold text-foreground">{mensagem}</p>
    {ausente && <p className="text-muted-foreground">{t.pendencia}</p>}
    {!ausente && status !== 403 && status !== 404 && <Button variant="outline" onClick={tentar}>{t.tentar}</Button>}
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
  return <div className="min-w-0 flex-1 space-y-5 overflow-auto bg-background p-4 md:p-6">
    <header className="space-y-1"><h1 className="flex items-center gap-2 text-xl font-semibold text-foreground">
      <CalendarX2 aria-hidden className="size-5 text-primary" />{t.titulo}</h1>
      <p className="text-sm text-muted-foreground">{t.descricao}</p></header>
    {configuracao.isPending ? <p role="status">{t.carregando}</p> : configuracao.isError
      ? <Falha erro={configuracao.error} tentar={() => void configuracao.refetch()} />
      : <Consulta key={`${identidade}:${JSON.stringify(configuracao.data)}`} config={configuracao.data} identidade={identidade} />}
  </div>;
}

function Consulta({ config, identidade }: { config: ConfiguracaoCancelamentos; identidade: string }) {
  const t = useTextos().cancelamentos;
  const telaEstreita = useTelaEstreita();
  const { pode } = useCapacidades();
  const iniciais: FiltrosCancelamentos = { inicio: config.inicioPadrao, fim: config.fimPadrao,
    base: "REGISTRO", descricao: "TODAS", profissional: "", tipo: "", nome: "" };
  const [rascunho, setRascunho] = useState<FiltrosCancelamentos>(iniciais);
  const [consulta, setConsulta] = useState({ filtros: rascunho, pagina: 0 });
  const [selecionado, selecionar] = useState<string | null>(null);
  const lista = useQuery({ queryKey: ["cancelamentos", identidade, "lista", consulta, config.tamanhoPagina],
    queryFn: ({ signal }) => listarCancelamentos(consulta.filtros, consulta.pagina, config.tamanhoPagina, signal),
    enabled: pode("cancelamentos.ver"), retry: false });
  const detalhe = useQuery({ queryKey: ["cancelamentos", identidade, "detalhe", selecionado],
    queryFn: ({ signal }) => obterCancelamento(selecionado!, signal), enabled: selecionado !== null && pode("cancelamentos.ver"), retry: false });
  function aplicar(e: FormEvent) {
    e.preventDefault(); selecionar(null); setConsulta({ filtros: { ...rascunho }, pagina: 0 });
  }
  function campo<K extends keyof FiltrosCancelamentos>(chave: K, valor: FiltrosCancelamentos[K]) {
    setRascunho(atual => ({ ...atual, [chave]: valor }));
  }
  const data = (valor: string | null) => valor ? new Intl.DateTimeFormat("pt-BR", {
    timeZone: config.fusoHorario, dateStyle: "short", timeStyle: "short",
  }).format(new Date(valor)) : t.naoInformado;
  return <>
    <form onSubmit={aplicar} className="grid min-w-0 grid-cols-1 items-end gap-3 rounded-xl border bg-card p-4 sm:grid-cols-2 xl:grid-cols-4">
      <label className="space-y-1 text-sm">{t.base}<Seletor valor={rascunho.base} placeholder={t.base} ariaLabel={t.base}
        opcoes={[{valor:"REGISTRO",rotulo:t.registro},{valor:"AGENDAMENTO",rotulo:t.agendamento},{valor:"CANCELAMENTO",rotulo:t.canceladoEm}]}
        obrigatorio onChange={v => { if (v) campo("base", v as FiltrosCancelamentos["base"]); }} /></label>
      <label className="space-y-1 text-sm">{t.inicio}<SeletorData obrigatorio valor={rascunho.inicio} placeholder={t.inicio} onChange={v => campo("inicio",v)} /></label>
      <label className="space-y-1 text-sm">{t.fim}<SeletorData obrigatorio valor={rascunho.fim} placeholder={t.fim} onChange={v => campo("fim",v)} /></label>
      <label className="space-y-1 text-sm">{t.descricaoFiltro}<Seletor valor={rascunho.descricao} placeholder={t.descricaoFiltro} ariaLabel={t.descricaoFiltro}
        opcoes={[{valor:"TODAS",rotulo:t.todas},{valor:"COM_DESCRICAO",rotulo:t.comDescricao},{valor:"SEM_DESCRICAO",rotulo:t.semDescricao}]}
        obrigatorio onChange={v => { if (v) campo("descricao", v as FiltrosCancelamentos["descricao"]); }} /></label>
      <label className="space-y-1 text-sm">{t.nome}<Input maxLength={config.maximoBusca} value={rascunho.nome} onChange={e => campo("nome", e.target.value)} /></label>
      <label className="space-y-1 text-sm">{t.profissional}<Input maxLength={config.maximoBusca} value={rascunho.profissional} onChange={e => campo("profissional", e.target.value)} /></label>
      <label className="space-y-1 text-sm">{t.tipo}<Input maxLength={config.maximoBusca} value={rascunho.tipo} onChange={e => campo("tipo", e.target.value)} /></label>
      <div className="flex flex-wrap gap-2"><Button type="submit">{t.aplicar}</Button>
        <Button type="button" variant="outline" onClick={() => {
          setRascunho(iniciais); setConsulta({ filtros: iniciais, pagina: 0 }); selecionar(null);
        }}>{t.limpar}</Button></div>
    </form>
    <div className="space-y-1 text-xs text-muted-foreground"><p>{preencher(t.fuso, { fuso: config.fusoHorario })} · {preencher(t.limitePeriodo, { dias: config.maximoDias })}</p><p>{t.notaDatas}</p></div>
    {lista.isPending ? <p role="status">{t.carregando}</p> : lista.isError ? <Falha erro={lista.error} tentar={() => void lista.refetch()} />
      : lista.data.itens.length === 0 ? <p role="status" className="rounded-xl border bg-card p-8 text-center text-muted-foreground">{t.vazio}</p>
      : telaEstreita ? <div className="space-y-3">{lista.data.itens.map(item =>
        <article key={item.id} aria-label={item.contatoNome || t.naoInformado} className="space-y-4 rounded-xl border bg-card p-4">
          <Detalhes item={item} t={t} data={data} resumido />
          <Button variant="outline" className="w-full" onClick={() => selecionar(item.id)}>{t.detalhes}</Button>
        </article>)}</div>
      : <div className="overflow-x-auto rounded-xl border bg-card"><table className="w-full text-left text-sm">
        <thead className="border-b bg-muted/40 text-xs text-muted-foreground"><tr>
          {[t.contato, t.agendamento, t.registro, t.profissional, t.tipo, t.descricaoFiltro, t.detalhes].map(c => <th key={c} scope="col" className="p-3 font-medium">{c}</th>)}
        </tr></thead><tbody>{lista.data.itens.map(item => <tr key={item.id} className="border-b last:border-0">
          <td className="max-w-60 break-words p-3 font-semibold text-foreground">{item.contatoNome || t.naoInformado}</td>
          <td className="p-3">{data(item.agendadoPara)}</td><td className="p-3">{data(item.recebidoEm ?? null)}</td>
          <td className="max-w-52 break-words p-3">{item.profissionalNome || t.naoInformado}</td><td className="max-w-52 break-words p-3">{item.tipoAgendamento || t.naoInformado}</td>
          <td className="max-w-64 break-words p-3"><span className="line-clamp-3 whitespace-pre-wrap">{item.descricao?.trim() ? item.descricao : t.semDescricao}</span></td>
          <td className="p-3"><Button variant="outline" size="sm" onClick={() => selecionar(item.id)}>{t.detalhes}</Button></td>
        </tr>)}</tbody></table></div>}
    <nav aria-label={t.periodo} className="flex flex-wrap items-center justify-between gap-3 text-sm">
      <Button variant="outline" disabled={consulta.pagina === 0 || lista.isPending} onClick={() => setConsulta(v => ({ ...v, pagina: v.pagina - 1 }))}>{t.anterior}</Button>
      <span>{preencher(t.pagina, { pagina: consulta.pagina + 1 })}</span>
      <Button variant="outline" disabled={!lista.data?.temProxima || lista.isError || lista.isPending || consulta.pagina + 1 >= config.maximoPaginas}
        onClick={() => setConsulta(v => ({ ...v, pagina: v.pagina + 1 }))}>{t.proxima}</Button>
    </nav>
    {lista.data?.temProxima && consulta.pagina + 1 >= config.maximoPaginas && <p role="status" className="text-sm text-muted-foreground">{t.limitePaginas}</p>}
    <Dialog open={selecionado !== null && pode("cancelamentos.ver")} onOpenChange={aberto => { if (!aberto) selecionar(null); }}>
      <DialogContent showCloseButton={false} className="max-h-[85dvh] overflow-y-auto"><DialogHeader><DialogTitle>{t.detalheTitulo}</DialogTitle>
        <DialogDescription>{t.detalheDescricao}</DialogDescription></DialogHeader>
        {detalhe.isPending ? <p role="status">{t.carregando}</p> : detalhe.isError ? <Falha erro={detalhe.error} tentar={() => void detalhe.refetch()} />
          : <Detalhes item={detalhe.data} t={t} data={data} />}
        <p className="text-xs text-muted-foreground">{t.notaDatas}</p>
        {detalhe.isSuccess && detalhe.data.leadId && detalhe.data.atendimentoId && pode("atendimentos.ver")
          ? <Link className={buttonVariants({variant:"outline"})}
              href={`/atendimentos?leadId=${encodeURIComponent(detalhe.data.leadId)}&atendimentoId=${encodeURIComponent(detalhe.data.atendimentoId)}`}>
              {t.abrirAtendimento}</Link>
          : <p className="text-sm text-muted-foreground">{t.vinculoIndisponivel}</p>}
        <Button variant="outline" onClick={() => selecionar(null)}>{t.fechar}</Button>
      </DialogContent>
    </Dialog>
  </>;
}

function Detalhes({ item, t, data, resumido = false }: { item: Cancelamento; t: Textos; data: (v: string | null) => string; resumido?: boolean }) {
  return <dl className="space-y-3 text-sm">{[
    [t.contato, item.contatoNome], [t.agendamento, data(item.agendadoPara)], [t.registro, data(item.recebidoEm ?? null)],
    [t.profissional, item.profissionalNome], [t.tipo, item.tipoAgendamento], [t.descricaoCampo, item.descricao?.trim() ? item.descricao : t.semDescricao],
    [t.canceladoEm, data(item.canceladoEm ?? null)],
  ].map(([rotulo, valor], indice) => <div key={rotulo}><dt className="text-xs text-muted-foreground">{rotulo}</dt>
    <dd className={`whitespace-pre-wrap break-words text-foreground ${indice === 0 ? "font-semibold" : ""} ${resumido && indice === 5 ? "line-clamp-3" : ""}`}>{valor || t.naoInformado}</dd></div>)}</dl>;
}
