"use client";

import { useState } from "react";
import type { ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { Database, Link2, MessageSquareText, UserRoundCheck, Plus, Pencil, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useAtualizarParametroAutomacao,
  useConfiguracaoAutomacao,
  useTelemetriaAutomacao,
  useRegrasFollowUp, useRegrasFidelizacao, useMutacaoRegraFollowUp, useMutacaoRegraFidelizacao,
  useAlternarRegraFollowUp, useAlternarRegraFidelizacao, useExcluirRegraFollowUp, useExcluirRegraFidelizacao,
  useRecursosIa, useAtualizarResumoIa,
} from "@/lib/automacao/use-automacao";
import type { ParametroAutomacao, RegraFidelizacao, RegraFollowUp, StatusAutomacaoTelemetria } from "@/lib/automacao/types";
import type { Textos } from "@/lib/config/schema";

export function PaginaAutomacao() {
  const t = useTextos().automacao;
  const router = useRouter();
  const parametrosDaUrl = useSearchParams();
  const [aba, setAba] = useState<Aba>(() => normalizarAba(parametrosDaUrl.get("aba")));

  function selecionarAba(novaAba: Aba) {
    setAba(novaAba);
    const parametros = new URLSearchParams(parametrosDaUrl.toString());
    if (novaAba === "geral") parametros.delete("aba");
    else parametros.set("aba", novaAba);
    const query = parametros.toString();
    router.replace(query ? `/automacao?${query}` : "/automacao", { scroll: false });
  }

  return (
    <div className="space-y-5 p-6">
      <header>
        <h1 className="text-xl font-semibold">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descricao}</p>
      </header>

      <nav className="flex gap-1 border-b" role="tablist">{ABAS.map((id) => <button key={id} role="tab" aria-selected={aba === id} className={`border-b-2 px-3 py-2 text-sm ${aba === id ? "border-primary font-medium" : "border-transparent text-muted-foreground"}`} onClick={() => selecionarAba(id)}>{t.abas[id]}</button>)}</nav>
      {aba === "geral" ? <GeralAutomacao t={t} /> : aba === "followUp" ? <PainelFollowUp /> : <PainelFidelizacao />}
    </div>
  );
}

type Aba = "geral" | "followUp" | "fidelizacao";
const ABAS: readonly Aba[] = ["geral", "followUp", "fidelizacao"];

function normalizarAba(valor: string | null): Aba {
  return valor === "followUp" || valor === "fidelizacao" ? valor : "geral";
}

function GeralAutomacao({ t }: { t: ReturnType<typeof useTextos>["automacao"] }) {
  const parametros = useConfiguracaoAutomacao();
  const telemetria = useTelemetriaAutomacao();
  const recursos = useRecursosIa();
  const atualizarResumo = useAtualizarResumoIa();
  const atualizarParametro = useAtualizarParametroAutomacao();
  const preenchimento = parametros.data?.find((p) => p.chave === "ia.preenchimento_automatico");
  return <>
      <CardsDeTelemetria
        dados={telemetria.data}
        carregando={telemetria.isLoading}
        comErro={telemetria.isError}
        onTentarNovamente={() => telemetria.refetch()}
      />

      {!recursos.isLoading && !recursos.isError && recursos.data && <section className="rounded-lg border bg-card p-4">
        <h2 className="font-medium">{t.recursosIa.titulo}</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <label className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
            <span>{t.recursosIa.resumo}</span>
            <Switch checked={recursos.data.resumo.ativo} aria-label={t.recursosIa.resumo} onCheckedChange={(ativo) => atualizarResumo.mutate({ ...recursos.data!.resumo, ativo })} />
          </label>
          {preenchimento && <label className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
            <span>{t.recursosIa.preenchimento}</span>
            <Switch checked={recursos.data.preenchimentoAutomatico} aria-label={t.recursosIa.preenchimento} onCheckedChange={(ativo) => atualizarParametro.mutate({ chave: preenchimento.chave, valor: String(ativo) })} />
          </label>}
        </div>
      </section>}

      {parametros.isLoading ? (
        <p>{t.carregando}</p>
      ) : parametros.isError ? (
        <ErroDeCarregamento
          mensagem={t.erro}
          onTentarNovamente={() => parametros.refetch()}
        />
      ) : !parametros.data?.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <div className="space-y-3">
          {parametros.data.map((parametro) => (
            <LinhaParametro key={parametro.chave} parametro={parametro} />
          ))}
        </div>
      )}
  </>;
}

function PainelFollowUp() {
  const t = useTextos().automacao;
  const query = useRegrasFollowUp(); const mutacao = useMutacaoRegraFollowUp(); const alternar = useAlternarRegraFollowUp(); const excluir = useExcluirRegraFollowUp();
  const [editando, setEditando] = useState<RegraFollowUp | null>(null); const [novo, setNovo] = useState(false); const [remover, setRemover] = useState<RegraFollowUp | null>(null);
  return <PainelDeRegras tipo="follow" dados={query.data ?? []} carregando={query.isLoading} erro={query.isError} onRetry={() => query.refetch()} onNovo={() => setNovo(true)} onEditar={(r) => setEditando(r as RegraFollowUp)} onAlternar={(r: RegraCard) => { const f = r as RegraFollowUp; alternar.mutate({ id: f.id, ativo: !f.ativo }); }} onExcluir={(r) => setRemover(r as RegraFollowUp)} t={t}>
    {(novo || editando) && <FormularioFollowUp existente={editando ?? undefined} onFechar={() => { setNovo(false); setEditando(null); }} onSalvar={(dados) => mutacao.mutate({ id: editando?.id, dados }, { onSuccess: () => { setNovo(false); setEditando(null); } })} />}
    <Dialog open={!!remover} onOpenChange={(v) => !v && setRemover(null)}><DialogContent><DialogHeader><DialogTitle>{t.regras.confirmarExclusao}</DialogTitle><DialogDescription>{remover?.nome}</DialogDescription></DialogHeader><DialogFooter><Button variant="outline" onClick={() => setRemover(null)}>{t.regras.cancelar}</Button><Button variant="destructive" onClick={() => { if (remover) excluir.mutate(remover.id, { onSuccess: () => setRemover(null) }); }}>{t.regras.excluir}</Button></DialogFooter></DialogContent></Dialog>
  </PainelDeRegras>;
}

function PainelFidelizacao() {
  const t = useTextos().automacao; const query = useRegrasFidelizacao(); const mutacao = useMutacaoRegraFidelizacao(); const alternar = useAlternarRegraFidelizacao(); const excluir = useExcluirRegraFidelizacao();
  const [editando, setEditando] = useState<RegraFidelizacao | null>(null); const [novo, setNovo] = useState(false); const [remover, setRemover] = useState<RegraFidelizacao | null>(null);
  return <PainelDeRegras tipo="fidelizacao" dados={query.data ?? []} carregando={query.isLoading} erro={query.isError} onRetry={() => query.refetch()} onNovo={() => setNovo(true)} onEditar={(r) => setEditando(r as RegraFidelizacao)} onAlternar={(r: RegraCard) => { const f = r as RegraFidelizacao; alternar.mutate({ id: f.id, ativo: !f.ativo }); }} onExcluir={(r) => setRemover(r as RegraFidelizacao)} t={t}>
    {(novo || editando) && <FormularioFidelizacao existente={editando ?? undefined} onFechar={() => { setNovo(false); setEditando(null); }} onSalvar={(dados) => mutacao.mutate({ id: editando?.id, dados }, { onSuccess: () => { setNovo(false); setEditando(null); } })} />}
    <Dialog open={!!remover} onOpenChange={(v) => !v && setRemover(null)}><DialogContent><DialogHeader><DialogTitle>{t.regras.confirmarExclusao}</DialogTitle></DialogHeader><DialogFooter><Button variant="outline" onClick={() => setRemover(null)}>{t.regras.cancelar}</Button><Button variant="destructive" onClick={() => { if (remover) excluir.mutate(remover.id, { onSuccess: () => setRemover(null) }); }}>{t.regras.excluir}</Button></DialogFooter></DialogContent></Dialog>
  </PainelDeRegras>;
}

type RegraCard = RegraFollowUp | RegraFidelizacao;
function PainelDeRegras({ tipo, dados, carregando, erro, onRetry, onNovo, onEditar, onAlternar, onExcluir, children, t }: { tipo: "follow" | "fidelizacao"; dados: RegraCard[]; carregando: boolean; erro: boolean; onRetry: () => void; onNovo: () => void; onEditar: (r: RegraCard) => void; onAlternar: (r: RegraCard) => void; onExcluir: (r: RegraCard) => void; children: ReactNode; t: Textos["automacao"] }) {
  if (carregando) return <p>{t.carregando}</p>; if (erro) return <ErroDeCarregamento mensagem={t.regras.erro} onTentarNovamente={onRetry} />;
  return <section className="space-y-3"><div className="flex justify-end"><Button onClick={onNovo}><Plus className="mr-2 size-4" />{t.regras.novo}</Button></div>{children}<div className="grid gap-3 md:grid-cols-2">{dados.length ? dados.map((r) => <article key={r.id} className="rounded-lg border bg-card p-4"><div className="flex items-start justify-between"><div><h2 className="font-medium">{tipo === "follow" && "tempoMinutos" in r ? labelTempo(r.tempoMinutos, t.regras) : "diasSemContato" in r ? `${r.diasSemContato} ${t.regras.dias}` : ""}</h2><p className="mt-1 text-sm text-muted-foreground">{tipo === "follow" && "texto" in r ? r.texto : "mensagem" in r ? r.mensagem : ""}</p></div><span className={r.ativo ? "text-cor-sucesso" : "text-muted-foreground"}>{r.ativo ? t.regras.ativo : t.regras.inativo}</span></div><div className="mt-3 flex gap-2"><Button size="sm" variant="outline" onClick={() => onEditar(r)}><Pencil className="mr-1 size-3" />{t.regras.editar}</Button><Button size="sm" variant="outline" onClick={() => onAlternar(r)}>{r.ativo ? t.desativado : t.ativado}</Button><Button size="sm" variant="ghost" onClick={() => onExcluir(r)}><Trash2 className="size-4" /></Button></div></article>) : <p className="text-muted-foreground">{t.regras.vazio}</p>}</div></section>;
}

function labelTempo(minutos: number, t: Textos["automacao"]["regras"]) { return minutos % 1440 === 0 ? `${minutos / 1440} ${t.unidadeDias}` : `${minutos / 60} ${t.unidadeHoras}`; }

function FormularioFollowUp({ existente, onFechar, onSalvar }: { existente?: RegraFollowUp; onFechar: () => void; onSalvar: (x: { tempoMinutos: number; texto: string; ativo: boolean }) => void }) {
  const t = useTextos().automacao; const [valor, setValor] = useState(String(existente ? (existente.tempoMinutos % 1440 === 0 ? existente.tempoMinutos / 1440 : existente.tempoMinutos / 60) : 1)); const [unidade, setUnidade] = useState(existente && existente.tempoMinutos % 1440 === 0 ? "DIAS" : "HORAS"); const [texto, setTexto] = useState(existente?.texto ?? "");
  return <div className="rounded-lg border bg-muted/30 p-4"><div className="grid gap-3 sm:grid-cols-3"><Input type="number" min="1" value={valor} onChange={(e) => setValor(e.target.value)} /><Select value={unidade} onValueChange={(v) => setUnidade(v ?? "HORAS")}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="HORAS">{t.regras.unidadeHoras}</SelectItem><SelectItem value="DIAS">{t.regras.unidadeDias}</SelectItem></SelectContent></Select><Textarea value={texto} onChange={(e) => setTexto(e.target.value)} rows={2} /></div><p className="mt-2 text-xs text-muted-foreground">{t.regras.placeholderAjuda}</p><div className="mt-3 flex justify-end gap-2"><Button variant="outline" onClick={onFechar}>{t.regras.cancelar}</Button><Button onClick={() => onSalvar({ tempoMinutos: Number(valor) * (unidade === "DIAS" ? 1440 : 60), texto, ativo: existente?.ativo ?? true })}>{t.salvar}</Button></div></div>;
}

function FormularioFidelizacao({ existente, onFechar, onSalvar }: { existente?: RegraFidelizacao; onFechar: () => void; onSalvar: (x: { diasSemContato: number; mensagem: string; ativo: boolean }) => void }) {
  const t = useTextos().automacao; const [dias, setDias] = useState(String(existente?.diasSemContato ?? 30)); const [mensagem, setMensagem] = useState(existente?.mensagem ?? "");
  return <div className="rounded-lg border bg-muted/30 p-4"><div className="grid gap-3 sm:grid-cols-2"><Input type="number" min="1" value={dias} onChange={(e) => setDias(e.target.value)} /><Textarea value={mensagem} onChange={(e) => setMensagem(e.target.value)} rows={2} /></div><p className="mt-2 text-xs text-muted-foreground">{t.regras.placeholderAjuda}</p><div className="mt-3 flex justify-end gap-2"><Button variant="outline" onClick={onFechar}>{t.regras.cancelar}</Button><Button onClick={() => onSalvar({ diasSemContato: Number(dias), mensagem, ativo: existente?.ativo ?? true })}>{t.salvar}</Button></div></div>;
}

/**
 * Os quatro cards do topo (E17b §Bloco 5): mensagens enviadas, clientes transferidos, conexão da
 * Automação e status do CRM — snapshot de status_automacao_telemetria (Prompt A). Sem esqueleto
 * quando carregando ou com erro: a tela some os cards em vez de mostrar zero, que pareceria dado
 * real.
 */
function CardsDeTelemetria({
  dados,
  carregando,
  comErro,
  onTentarNovamente,
}: {
  dados: StatusAutomacaoTelemetria | undefined;
  carregando: boolean;
  comErro: boolean;
  onTentarNovamente: () => void | Promise<unknown>;
}) {
  const t = useTextos().automacao.telemetria;

  if (carregando) return null;
  if (comErro || !dados) {
    return <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={onTentarNovamente} />;
  }

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <CardDeTelemetria
        icone={<MessageSquareText className="size-5 text-primary" />}
        rotulo={t.mensagensEnviadas}
        valor={dados.mensagensEnviadas.toLocaleString("pt-BR")}
      />
      <CardDeTelemetria
        icone={<UserRoundCheck className="size-5 text-cor-ia" />}
        rotulo={t.clientesTransferidos}
        valor={dados.clientesTransferidos.toLocaleString("pt-BR")}
      />
      <CardDeTelemetria
        icone={<Link2 className="size-5 text-cor-sucesso" />}
        rotulo={t.conexaoAutomacao}
        status={dados.conexaoAutomacaoAtiva}
        rotuloAtivo={t.conectado}
        rotuloInativo={t.desconectado}
      />
      <CardDeTelemetria
        icone={<Database className="size-5 text-cor-sucesso" />}
        rotulo={t.statusDoCrm}
        status={dados.crmOnline}
        rotuloAtivo={t.online}
        rotuloInativo={t.offline}
      />
    </div>
  );
}

function CardDeTelemetria({
  icone,
  rotulo,
  valor,
  status,
  rotuloAtivo,
  rotuloInativo,
}: {
  icone: ReactNode;
  rotulo: string;
  valor?: string;
  status?: boolean;
  rotuloAtivo?: string;
  rotuloInativo?: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card p-4">
      <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted">
        {icone}
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-muted-foreground">{rotulo}</p>
        {status != null ? (
          <p
            className={
              status
                ? "mt-0.5 flex items-center gap-1.5 text-sm font-bold text-cor-sucesso"
                : "mt-0.5 flex items-center gap-1.5 text-sm font-bold text-cor-erro"
            }
          >
            <span
              className={status ? "size-2 rounded-full bg-cor-sucesso" : "size-2 rounded-full bg-cor-erro"}
            />
            {status ? rotuloAtivo : rotuloInativo}
          </p>
        ) : (
          <p className="text-lg font-bold">{valor}</p>
        )}
      </div>
    </div>
  );
}

function LinhaParametro({ parametro }: { parametro: ParametroAutomacao }) {
  const t = useTextos().automacao;
  const atualizar = useAtualizarParametroAutomacao();
  const [valor, setValor] = useState(parametro.valor);

  const alterado = valor !== parametro.valor;
  const foraDaFaixa = valorForaDaFaixa(parametro, valor);
  const podeSalvar = alterado && !foraDaFaixa && !atualizar.isPending;
  const temFaixa = parametro.valorMin != null || parametro.valorMax != null;

  function salvar() {
    atualizar.mutate({ chave: parametro.chave, valor });
  }

  return (
    <div className="rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium">{parametro.descricao ?? parametro.chave}</p>
          <p className="font-mono text-xs text-muted-foreground">{parametro.chave}</p>
        </div>
        {parametro.tipo !== "BOOLEAN" && temFaixa && (
          <p className="shrink-0 text-xs text-muted-foreground">
            {t.faixaLabel}: {parametro.valorMin ?? "—"}–{parametro.valorMax ?? "—"}
            {parametro.unidade ? ` ${parametro.unidade}` : ""}
          </p>
        )}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <CampoValor parametro={parametro} valor={valor} onChange={setValor} />
        {foraDaFaixa && (
          <p role="alert" className="text-xs text-destructive">
            {t.erroFaixa}
          </p>
        )}
        {atualizar.isError && (
          <p role="alert" className="text-xs text-destructive">
            {t.erroSalvar}
          </p>
        )}
        <Button size="sm" disabled={!podeSalvar} onClick={salvar} className="ml-auto">
          {atualizar.isPending ? t.salvando : t.salvar}
        </Button>
      </div>
    </div>
  );
}

function CampoValor({
  parametro,
  valor,
  onChange,
}: {
  parametro: ParametroAutomacao;
  valor: string;
  onChange: (valor: string) => void;
}) {
  const t = useTextos().automacao;

  if (parametro.tipo === "BOOLEAN") {
    return (
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={valor === "true"}
          onChange={(e) => onChange(e.target.checked ? "true" : "false")}
        />
        {valor === "true" ? t.ativado : t.desativado}
      </label>
    );
  }

  if (parametro.tipo === "TEXT") {
    return (
      <Textarea
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        rows={2}
        className="min-w-64 flex-1"
      />
    );
  }

  return (
    <Input
      type="number"
      inputMode="decimal"
      step={parametro.tipo === "DECIMAL" ? "any" : "1"}
      min={parametro.valorMin ?? undefined}
      max={parametro.valorMax ?? undefined}
      value={valor}
      onChange={(e) => onChange(e.target.value)}
      className="w-32"
    />
  );
}

function valorForaDaFaixa(parametro: ParametroAutomacao, valor: string): boolean {
  if (parametro.tipo !== "INT" && parametro.tipo !== "DECIMAL") return false;
  const numero = Number(valor);
  if (Number.isNaN(numero)) return true;
  if (parametro.valorMin != null && numero < parametro.valorMin) return true;
  if (parametro.valorMax != null && numero > parametro.valorMax) return true;
  return false;
}
