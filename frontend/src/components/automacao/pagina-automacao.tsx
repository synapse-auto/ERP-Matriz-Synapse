"use client";

import { useState } from "react";
import type { ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowLeft, Camera, ChevronDown, Database, Link2, MessageSquareText, Mic,
  MoreVertical, Paperclip, Phone, Plus, Smile, Sparkles, Trash2, UserRoundCheck, UsersRound,
} from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useAuthStore } from "@/lib/auth/auth-store";
import {
  useAlternarRegraFidelizacao, useAlternarRegraFollowUp, useAtualizarParametroAutomacao,
  useAtualizarResumoIa, useConfiguracaoAutomacao, useExcluirRegraFidelizacao,
  useExcluirRegraFollowUp, useMutacaoRegraFidelizacao, useMutacaoRegraFollowUp,
  useRecursosIa, useRegrasFidelizacao, useRegrasFollowUp, useTelemetriaAutomacao,
} from "@/lib/automacao/use-automacao";
import type {
  FidelizacaoPayload, FollowUpPayload, ParametroAutomacao, RegraFidelizacao,
  RegraFollowUp, StatusAutomacaoTelemetria,
} from "@/lib/automacao/types";
import { useTextos } from "@/lib/config/textos-provider";
import type { Textos } from "@/lib/config/schema";
import { resolverMensagemRapida } from "@/lib/suporte/resolver-mensagem-rapida";
import { recebeAtendimento } from "@/lib/equipe/papel";
import { useAtualizarDisponibilidadeParaIa, useEquipe } from "@/lib/equipe/use-equipe";
import type { StatusPresenca } from "@/lib/equipe/types";
import { cn } from "@/lib/utils";

type Aba = "geral" | "followUp" | "fidelizacao";
type UnidadeTempo = "HORAS" | "DIAS";
type Preview = { texto: string; legenda: string };

const ABAS: readonly Aba[] = ["geral", "followUp", "fidelizacao"];

export function PaginaAutomacao() {
  const textos = useTextos();
  const t = textos.automacao;
  const papel = useAuthStore((estado) => estado.papel);
  const podeAcessar = papel === "GESTOR" || papel === "SUBGESTOR" || papel === "ADMINISTRADOR";
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

  if (!podeAcessar) {
    return (
      <section className="m-6 rounded-xl border border-destructive/30 bg-destructive/5 p-6" role="alert">
        <h1 className="text-lg font-semibold">{t.titulo}</h1>
        <p className="mt-2 text-sm text-destructive">{t.semPermissao}</p>
      </section>
    );
  }

  return (
    <div className="min-h-full">
      <Tabs value={aba} onValueChange={(valor) => selecionarAba(valor as Aba)}>
        <header className="border-b bg-card px-6 pt-6 lg:px-9">
          <div className="mx-auto max-w-[82.5rem]">
            <h1 className="text-xl font-bold tracking-tight">{t.titulo}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{t.descricaoPorAba[aba]}</p>
            <TabsList variant="line" className="mt-5 h-auto gap-1 p-0">
              {ABAS.map((id) => (
                <TabsTrigger key={id} value={id} className="min-w-24 rounded-none px-4 pb-3 pt-2 shadow-none data-active:after:bg-primary">
                  {t.abas[id]}
                </TabsTrigger>
              ))}
            </TabsList>
          </div>
        </header>
      </Tabs>

      <main className="mx-auto max-w-[82.5rem] px-6 py-7 lg:px-9 lg:py-8">
        {aba === "geral" ? <GeralAutomacao textos={textos} /> : aba === "followUp" ? <PainelFollowUp textos={textos} /> : <PainelFidelizacao textos={textos} />}
      </main>
    </div>
  );
}

function normalizarAba(valor: string | null): Aba {
  return valor === "followUp" || valor === "fidelizacao" ? valor : "geral";
}

function GeralAutomacao({ textos }: { textos: Textos }) {
  const t = textos.automacao;
  const parametros = useConfiguracaoAutomacao();
  const telemetria = useTelemetriaAutomacao();
  const recursos = useRecursosIa();
  const atualizarResumo = useAtualizarResumoIa();
  const atualizarParametro = useAtualizarParametroAutomacao();
  const preenchimento = parametros.data?.find((p) => p.chave === "ia.preenchimento_automatico");

  return (
    <div className="space-y-6">
      <CardsDeTelemetria dados={telemetria.data} carregando={telemetria.isLoading} comErro={telemetria.isError} onTentarNovamente={() => telemetria.refetch()} />
      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_26.25rem]">
        <AtendentesDisponiveis textos={textos} />
        <section className="rounded-xl border bg-card p-6">
          <div className="flex items-center gap-2.5">
            <Sparkles className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-primary" />
            <h2 className="text-base font-bold">{t.recursosIa.titulo}</h2>
          </div>
          {recursos.isLoading ? null : recursos.isError || !recursos.data ? (
            <div className="mt-4"><ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => recursos.refetch()} /></div>
          ) : (
            <div className="mt-4 divide-y">
              <LinhaRecurso titulo={t.recursosIa.resumo} descricao={t.recursosIa.resumoDescricao} ativo={recursos.data.resumo.ativo} onChange={(ativo) => atualizarResumo.mutate({ ...recursos.data!.resumo, ativo })} />
              {preenchimento && <LinhaRecurso titulo={t.recursosIa.preenchimento} descricao={t.recursosIa.preenchimentoDescricao} ativo={recursos.data.preenchimentoAutomatico} onChange={(ativo) => atualizarParametro.mutate({ chave: preenchimento.chave, valor: String(ativo) })} />}
            </div>
          )}
        </section>
      </div>

      <details className="group rounded-xl border bg-card">
        <summary className="flex cursor-pointer list-none items-center gap-3 p-5 [&::-webkit-details-marker]:hidden">
          <div className="min-w-0 flex-1">
            <h2 className="font-semibold">{t.avancado.titulo}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t.avancado.descricao}</p>
          </div>
          <span className="sr-only group-open:hidden">{t.avancado.abrir}</span>
          <span className="sr-only hidden group-open:inline">{t.avancado.fechar}</span>
          <ChevronDown className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-muted-foreground transition-transform group-open:rotate-180" />
        </summary>
        <div className="space-y-3 border-t p-5">
          {parametros.isLoading ? <p>{t.carregando}</p> : parametros.isError ? (
            <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => parametros.refetch()} />
          ) : !parametros.data?.length ? <p className="py-5 text-center text-sm text-muted-foreground">{t.vazio}</p> : (
            parametros.data.map((parametro) => <LinhaParametro key={parametro.chave} parametro={parametro} />)
          )}
        </div>
      </details>
    </div>
  );
}

function AtendentesDisponiveis({ textos }: { textos: Textos }) {
  const equipe = useEquipe();
  const atualizar = useAtualizarDisponibilidadeParaIa();
  const t = textos.automacao.disponibilidade;
  const atendentes = (equipe.data ?? []).filter((usuario) => usuario.ativo && recebeAtendimento(usuario.papel));
  const disponiveis = atendentes.filter((usuario) => usuario.disponivelParaIa).length;
  const contagem = interpolar(t.contagem, { disponiveis: String(disponiveis), total: String(atendentes.length) });

  return (
    <section className="rounded-xl border bg-card p-6">
      <div className="flex flex-wrap items-center gap-2.5">
        <UsersRound className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-primary" />
        <h2 className="text-base font-bold">{t.titulo}</h2>
        <span className="rounded-full bg-cor-sucesso/10 px-2.5 py-1 text-xs font-bold text-cor-sucesso">{contagem}</span>
      </div>
      <p className="mt-1.5 text-sm text-muted-foreground">{t.descricao}</p>
      <p className="mt-5 text-xs font-bold tracking-wider text-muted-foreground">{t.rotuloAtual}</p>
      {equipe.isLoading ? null : equipe.isError ? (
        <div className="mt-3"><ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => equipe.refetch()} /></div>
      ) : atendentes.length === 0 ? (
        <p className="mt-3 rounded-lg border border-dashed p-5 text-center text-sm text-muted-foreground">{t.vazio}</p>
      ) : (
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {atendentes.map((usuario) => {
            const ativo = usuario.disponivelParaIa === true;
            const chavePresenca = usuario.statusPresenca.toLowerCase() as Lowercase<StatusPresenca>;
            return (
              <div key={usuario.id} className={cn("flex items-center gap-3 rounded-lg border p-3 transition-colors", ativo ? "border-primary/25 bg-accent/50" : "bg-card")}>
                <div className="relative shrink-0">
                  <AvatarIniciais id={usuario.id} nome={usuario.nome} fotoUrl={usuario.fotoUrl} className="flex size-10 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-primary-foreground" />
                  <span title={textos.equipe.presenca[chavePresenca]} className={cn("absolute -bottom-0.5 -right-0.5 size-3 rounded-full border-2 border-card", COR_PRESENCA[usuario.statusPresenca])} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold">{usuario.nome}</p>
                  <p className="truncate text-xs text-muted-foreground">{usuario.cargo || textos.equipe.papeis.atendente}</p>
                </div>
                <Switch checked={ativo} disabled={atualizar.isPending} aria-label={`${textos.equipe.disponibilidadeIa.rotulo}: ${usuario.nome}`} onCheckedChange={(disponivelParaIa) => atualizar.mutate({ id: usuario.id, disponivelParaIa })} />
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

const COR_PRESENCA: Record<StatusPresenca, string> = { ONLINE: "bg-cor-sucesso", AUSENTE: "bg-cor-atencao", OFFLINE: "bg-muted-foreground" };

function LinhaRecurso({ titulo, descricao, ativo, onChange }: { titulo: string; descricao: string; ativo: boolean; onChange: (ativo: boolean) => void }) {
  return (
    <div className="flex items-center gap-4 py-4 first:pt-1 last:pb-1">
      <div className="min-w-0 flex-1"><p className="text-sm font-semibold">{titulo}</p><p className="mt-1 text-xs leading-relaxed text-muted-foreground">{descricao}</p></div>
      <Switch checked={ativo} aria-label={titulo} onCheckedChange={onChange} />
    </div>
  );
}

function PainelFollowUp({ textos }: { textos: Textos }) {
  const t = textos.automacao;
  const query = useRegrasFollowUp();
  const mutacao = useMutacaoRegraFollowUp();
  const alternar = useAlternarRegraFollowUp();
  const excluir = useExcluirRegraFollowUp();
  const [ativoId, setAtivoId] = useState<string | null>(null);
  const [novoId, setNovoId] = useState<string | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [remover, setRemover] = useState<RegraFollowUp | null>(null);
  const dados = moverParaInicio(query.data ?? [], novoId);
  const regraAtiva = dados.find((regra) => regra.id === ativoId) ?? dados[0];
  const previewAtual = preview ?? (regraAtiva ? previewFollowUp(regraAtiva, t) : previewVazio(t));
  if (query.isLoading) return <p>{t.carregando}</p>;
  if (query.isError) return <ErroDeCarregamento mensagem={t.regras.erro} onTentarNovamente={() => query.refetch()} />;

  return (
    <>
      <div className="grid items-start gap-7 xl:grid-cols-[minmax(0,45rem)_23.75rem]">
        <section className="min-w-0 space-y-4">
          <CabecalhoLista contagem={interpolar(dados.length === 1 ? t.regras.followUpContagemSingular : t.regras.followUpsContagem, { quantidade: String(dados.length) })} botao={t.regras.novoFollowUp} pendente={mutacao.isPending} onNovo={() => mutacao.mutate(
            { dados: { tempoMinutos: 60, texto: t.regras.mensagemNovaFollowUp, ativo: false } },
            { onSuccess: (criada) => { setNovoId(criada.id); setAtivoId(criada.id); setPreview(previewFollowUp(criada, t)); } },
          )} />
          {dados.length === 0 ? <EstadoVazio texto={t.regras.vazioFollowUp} /> : dados.map((regra) => (
            <CardFollowUp key={regra.id} regra={regra} ativo={regra.id === (ativoId ?? dados[0]?.id)} autoFocus={regra.id === novoId} t={t}
              onAtivar={(proximoPreview) => { setAtivoId(regra.id); setPreview(proximoPreview); }}
              onSalvar={(dadosAtualizados, callbacks) => mutacao.mutate({ id: regra.id, dados: dadosAtualizados }, callbacks)}
              onAlternar={() => alternar.mutate({ id: regra.id, ativo: !regra.ativo })} onExcluir={() => setRemover(regra)} />
          ))}
        </section>
        <PreviewWhatsApp textos={textos} preview={previewAtual} />
      </div>
      <ConfirmarExclusao aberto={remover != null} descricao={remover?.nome} t={t} onFechar={() => setRemover(null)} onConfirmar={() => { if (remover) excluir.mutate(remover.id, { onSuccess: () => setRemover(null) }); }} />
    </>
  );
}

function PainelFidelizacao({ textos }: { textos: Textos }) {
  const t = textos.automacao;
  const query = useRegrasFidelizacao();
  const mutacao = useMutacaoRegraFidelizacao();
  const alternar = useAlternarRegraFidelizacao();
  const excluir = useExcluirRegraFidelizacao();
  const [ativoId, setAtivoId] = useState<string | null>(null);
  const [novoId, setNovoId] = useState<string | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [remover, setRemover] = useState<RegraFidelizacao | null>(null);
  const dados = moverParaInicio(query.data ?? [], novoId);
  const regraAtiva = dados.find((regra) => regra.id === ativoId) ?? dados[0];
  const previewAtual = preview ?? (regraAtiva ? previewFidelizacao(regraAtiva, t) : previewVazio(t));
  if (query.isLoading) return <p>{t.carregando}</p>;
  if (query.isError) return <ErroDeCarregamento mensagem={t.regras.erro} onTentarNovamente={() => query.refetch()} />;

  return (
    <>
      <div className="grid items-start gap-7 xl:grid-cols-[minmax(0,45rem)_23.75rem]">
        <section className="min-w-0 space-y-4">
          <CabecalhoLista contagem={interpolar(dados.length === 1 ? t.regras.mensagemContagemSingular : t.regras.mensagensContagem, { quantidade: String(dados.length) })} botao={t.regras.novaMensagem} pendente={mutacao.isPending} onNovo={() => mutacao.mutate(
            { dados: { diasSemContato: 30, mensagem: t.regras.mensagemNovaFidelizacao, ativo: false } },
            { onSuccess: (criada) => { setNovoId(criada.id); setAtivoId(criada.id); setPreview(previewFidelizacao(criada, t)); } },
          )} />
          {dados.length === 0 ? <EstadoVazio texto={t.regras.vazioFidelizacao} /> : dados.map((regra) => (
            <CardFidelizacao key={regra.id} regra={regra} ativo={regra.id === (ativoId ?? dados[0]?.id)} autoFocus={regra.id === novoId} t={t}
              onAtivar={(proximoPreview) => { setAtivoId(regra.id); setPreview(proximoPreview); }}
              onSalvar={(dadosAtualizados, callbacks) => mutacao.mutate({ id: regra.id, dados: dadosAtualizados }, callbacks)}
              onAlternar={() => alternar.mutate({ id: regra.id, ativo: !regra.ativo })} onExcluir={() => setRemover(regra)} />
          ))}
        </section>
        <PreviewWhatsApp textos={textos} preview={previewAtual} />
      </div>
      <ConfirmarExclusao aberto={remover != null} t={t} onFechar={() => setRemover(null)} onConfirmar={() => { if (remover) excluir.mutate(remover.id, { onSuccess: () => setRemover(null) }); }} />
    </>
  );
}

function CabecalhoLista({ contagem, botao, pendente, onNovo }: { contagem: string; botao: string; pendente: boolean; onNovo: () => void }) {
  return <div className="flex items-center justify-between gap-4"><span className="text-sm font-bold text-muted-foreground">{contagem}</span><Button variant="outline" size="sm" disabled={pendente} onClick={onNovo}><Plus className="size-(--tamanho-icone-interface)" />{botao}</Button></div>;
}

function CardFollowUp({ regra, ativo, autoFocus, t, onAtivar, onSalvar, onAlternar, onExcluir }: {
  regra: RegraFollowUp; ativo: boolean; autoFocus: boolean; t: Textos["automacao"];
  onAtivar: (preview: Preview) => void; onSalvar: (dados: FollowUpPayload, callbacks: { onError: () => void }) => void;
  onAlternar: () => void; onExcluir: () => void;
}) {
  const inicial = decomporTempo(regra.tempoMinutos);
  const [valor, setValor] = useState(String(inicial.valor));
  const [unidade, setUnidade] = useState<UnidadeTempo>(inicial.unidade);
  const [texto, setTexto] = useState(regra.texto);
  const [erro, setErro] = useState<"tempo" | "texto" | null>(null);
  function dadosAtuais(proximo: Partial<{ valor: string; unidade: UnidadeTempo; texto: string }> = {}): FollowUpPayload {
    const valorAtual = Number(proximo.valor ?? valor); const unidadeAtual = proximo.unidade ?? unidade;
    return { tempoMinutos: valorAtual * (unidadeAtual === "DIAS" ? 1440 : 60), texto: proximo.texto ?? texto, ativo: regra.ativo };
  }
  function ativar(proximo?: Partial<{ valor: string; unidade: UnidadeTempo; texto: string }>) { onAtivar(previewFollowUp(dadosAtuais(proximo), t)); }
  function restaurarTempo() { setValor(String(inicial.valor)); setUnidade(inicial.unidade); ativar({ valor: String(inicial.valor), unidade: inicial.unidade }); }
  function salvarTempo() {
    const dados = dadosAtuais();
    if (!Number.isFinite(dados.tempoMinutos) || dados.tempoMinutos <= 0) { restaurarTempo(); setErro("tempo"); return; }
    if (dados.tempoMinutos === regra.tempoMinutos) return;
    setErro(null); onSalvar(dados, { onError: () => { restaurarTempo(); setErro("tempo"); } });
  }
  function trocarUnidade(proxima: UnidadeTempo) {
    if (proxima === unidade) return;
    setUnidade(proxima); ativar({ unidade: proxima });
    onSalvar(dadosAtuais({ unidade: proxima }), { onError: () => { setUnidade(inicial.unidade); setValor(String(inicial.valor)); setErro("tempo"); } });
  }
  function salvarTexto() {
    if (texto === regra.texto) return;
    setErro(null); onSalvar(dadosAtuais(), { onError: () => { setTexto(regra.texto); ativar({ texto: regra.texto }); setErro("texto"); } });
  }
  return (
    <article className={cn("rounded-xl border bg-card p-5 transition-shadow", ativo ? "border-l-4 border-l-primary shadow-sm" : "border-l-4 border-l-transparent")} onFocus={() => ativar()}>
      <TopoCard gatilho={labelTempoCompleto(dadosAtuais().tempoMinutos, t.regras)} ativo={regra.ativo} t={t} onAlternar={onAlternar} onExcluir={onExcluir} />
      <div className="mt-5">
        <label htmlFor={`tempo-${regra.id}`} className="text-xs font-bold tracking-wider text-muted-foreground">{t.regras.tempo}</label>
        <div className="mt-2 flex items-center gap-2.5">
          <Input id={`tempo-${regra.id}`} aria-label={t.regras.tempo} type="number" min="1" value={valor} autoFocus={autoFocus} onChange={(evento) => { setValor(evento.target.value); ativar({ valor: evento.target.value }); }} onBlur={salvarTempo} className="w-20 text-center font-bold" />
          <div className="inline-flex overflow-hidden rounded-lg border">
            {(["HORAS", "DIAS"] as const).map((opcao) => <button key={opcao} type="button" className={cn("h-9 px-4 text-sm font-semibold transition-colors", unidade === opcao ? "bg-primary text-primary-foreground" : "bg-card text-muted-foreground hover:bg-muted")} onClick={() => trocarUnidade(opcao)}>{opcao === "HORAS" ? t.regras.unidadeHoras : t.regras.unidadeDias}</button>)}
          </div>
        </div>
        {erro === "tempo" && <ErroCampo texto={t.regras.erroSalvar} />}
      </div>
      <CampoMensagem id={regra.id} valor={texto} erro={erro === "texto"} t={t} onChange={(proximo) => { setTexto(proximo); ativar({ texto: proximo }); }} onBlur={salvarTexto} />
    </article>
  );
}

function CardFidelizacao({ regra, ativo, autoFocus, t, onAtivar, onSalvar, onAlternar, onExcluir }: {
  regra: RegraFidelizacao; ativo: boolean; autoFocus: boolean; t: Textos["automacao"];
  onAtivar: (preview: Preview) => void; onSalvar: (dados: FidelizacaoPayload, callbacks: { onError: () => void }) => void;
  onAlternar: () => void; onExcluir: () => void;
}) {
  const [dias, setDias] = useState(String(regra.diasSemContato));
  const [mensagem, setMensagem] = useState(regra.mensagem);
  const [erro, setErro] = useState<"dias" | "mensagem" | null>(null);
  function dadosAtuais(proximo: Partial<{ dias: string; mensagem: string }> = {}): FidelizacaoPayload { return { diasSemContato: Number(proximo.dias ?? dias), mensagem: proximo.mensagem ?? mensagem, ativo: regra.ativo }; }
  function ativar(proximo?: Partial<{ dias: string; mensagem: string }>) { onAtivar(previewFidelizacao(dadosAtuais(proximo), t)); }
  function salvarDias() {
    const dados = dadosAtuais();
    if (!Number.isFinite(dados.diasSemContato) || dados.diasSemContato <= 0) { setDias(String(regra.diasSemContato)); setErro("dias"); return; }
    if (dados.diasSemContato === regra.diasSemContato) return;
    setErro(null); onSalvar(dados, { onError: () => { setDias(String(regra.diasSemContato)); setErro("dias"); } });
  }
  function salvarMensagem() {
    if (mensagem === regra.mensagem) return;
    setErro(null); onSalvar(dadosAtuais(), { onError: () => { setMensagem(regra.mensagem); ativar({ mensagem: regra.mensagem }); setErro("mensagem"); } });
  }
  return (
    <article className={cn("rounded-xl border bg-card p-5 transition-shadow", ativo ? "border-l-4 border-l-primary shadow-sm" : "border-l-4 border-l-transparent")} onFocus={() => ativar()}>
      <TopoCard gatilho={labelDias(regra.diasSemContato, t.regras)} ativo={regra.ativo} t={t} onAlternar={onAlternar} onExcluir={onExcluir} />
      <div className="mt-5">
        <label htmlFor={`dias-${regra.id}`} className="text-xs font-bold tracking-wider text-muted-foreground">{t.regras.dias}</label>
        <div className="mt-2 flex items-center gap-3">
          <Input id={`dias-${regra.id}`} aria-label={t.regras.dias} type="number" min="1" value={dias} autoFocus={autoFocus} onChange={(evento) => { setDias(evento.target.value); ativar({ dias: evento.target.value }); }} onBlur={salvarDias} className="w-20 text-center font-bold" />
          <span className="text-sm font-semibold text-muted-foreground">{t.regras.diasSemContato}</span>
        </div>
        {erro === "dias" && <ErroCampo texto={t.regras.erroSalvar} />}
      </div>
      <CampoMensagem id={regra.id} valor={mensagem} erro={erro === "mensagem"} t={t} onChange={(proximo) => { setMensagem(proximo); ativar({ mensagem: proximo }); }} onBlur={salvarMensagem} />
    </article>
  );
}

function TopoCard({ gatilho, ativo, t, onAlternar, onExcluir }: { gatilho: string; ativo: boolean; t: Textos["automacao"]; onAlternar: () => void; onExcluir: () => void }) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <span className="rounded-full bg-accent px-3 py-1.5 text-xs font-bold text-accent-foreground">{gatilho}</span>
      <div className="ml-auto flex items-center gap-2.5">
        <span className={cn("text-xs font-bold", ativo ? "text-cor-sucesso" : "text-muted-foreground")}>{ativo ? t.regras.ativo : t.regras.inativo}</span>
        <Switch checked={ativo} aria-label={ativo ? t.regras.desativar : t.regras.ativar} onCheckedChange={onAlternar} />
        <Button variant="ghost" size="icon-sm" aria-label={t.regras.excluir} onClick={onExcluir}><Trash2 className="size-(--tamanho-icone-interface)" /></Button>
      </div>
    </div>
  );
}

function CampoMensagem({ id, valor, erro, t, onChange, onBlur }: { id: string; valor: string; erro: boolean; t: Textos["automacao"]; onChange: (valor: string) => void; onBlur: () => void }) {
  return (
    <div className="mt-5">
      <label htmlFor={`mensagem-${id}`} className="text-xs font-bold tracking-wider text-muted-foreground">{t.regras.mensagem}</label>
      <Textarea id={`mensagem-${id}`} aria-label={t.regras.mensagem} value={valor} rows={3} onChange={(evento) => onChange(evento.target.value)} onBlur={onBlur} className="mt-2 resize-y leading-relaxed" />
      <p className="mt-1.5 text-xs text-muted-foreground">{t.regras.placeholderAjuda}</p>
      {erro && <ErroCampo texto={t.regras.erroSalvar} />}
    </div>
  );
}

function ErroCampo({ texto }: { texto: string }) { return <p role="alert" className="mt-1.5 text-xs text-destructive">{texto}</p>; }

function PreviewWhatsApp({ textos, preview }: { textos: Textos; preview: Preview }) {
  const t = textos.automacao.regras;
  const nomeInstancia = textos.app.marca;
  const mensagem = resolverMensagemRapida(preview.texto || t.previewVazio, { nome: "{nome}", empresa: "{empresa}" }).texto;
  return (
    <aside className="sticky top-6 hidden xl:block">
      <h2 className="mb-3 text-center text-xs font-bold tracking-widest text-muted-foreground">{t.visualizacaoWhatsapp}</h2>
      <div className="mx-auto flex h-[32rem] max-w-[22.5rem] flex-col overflow-hidden rounded-xl border bg-muted shadow-md">
        <div className="flex items-center gap-2.5 bg-cor-sucesso px-4 py-3 text-primary-foreground">
          <ArrowLeft className="size-5" />
          <AvatarIniciais id={nomeInstancia} nome={nomeInstancia} className="flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-bold text-primary-foreground" />
          <div className="min-w-0 flex-1"><p className="truncate text-sm font-bold">{nomeInstancia}</p><p className="text-xs text-primary-foreground/80">{t.online}</p></div>
          <Phone className="size-4" /><MoreVertical className="size-5" />
        </div>
        <div className="flex flex-1 flex-col justify-end gap-3 overflow-hidden bg-muted/70 p-3">
          <div className="text-center"><span className="rounded-lg bg-cor-sucesso/10 px-3 py-1 text-[0.65rem] font-bold text-cor-sucesso">{t.hoje}</span></div>
          <div className="flex justify-start"><div className="max-w-[84%] rounded-lg rounded-tl-sm bg-card px-3 py-2 text-sm shadow-sm"><span className="whitespace-pre-wrap break-words leading-relaxed">{mensagem}</span><span className="ml-2 float-right mt-1 text-[0.625rem] text-muted-foreground">{t.horario}</span></div></div>
        </div>
        <div className="flex items-center gap-2 bg-muted/70 p-3">
          <div className="flex h-10 flex-1 items-center gap-2 rounded-full bg-card px-3 text-muted-foreground"><Smile className="size-4" /><span className="flex-1 text-xs">{t.composer}</span><Paperclip className="size-4" /><Camera className="size-4" /></div>
          <span className="flex size-10 items-center justify-center rounded-full bg-cor-sucesso text-primary-foreground"><Mic className="size-4" /></span>
        </div>
      </div>
      <p className="mt-3 text-center text-sm text-muted-foreground">{preview.legenda}</p>
    </aside>
  );
}

function ConfirmarExclusao({ aberto, descricao, t, onFechar, onConfirmar }: { aberto: boolean; descricao?: string; t: Textos["automacao"]; onFechar: () => void; onConfirmar: () => void }) {
  return (
    <Dialog open={aberto} onOpenChange={(proximo) => !proximo && onFechar()}><DialogContent><DialogHeader><DialogTitle>{t.regras.confirmarExclusao}</DialogTitle>{descricao && <DialogDescription>{descricao}</DialogDescription>}</DialogHeader><DialogFooter><Button variant="outline" onClick={onFechar}>{t.regras.cancelar}</Button><Button variant="destructive" onClick={onConfirmar}>{t.regras.excluir}</Button></DialogFooter></DialogContent></Dialog>
  );
}

function EstadoVazio({ texto }: { texto: string }) { return <div className="rounded-xl border border-dashed bg-card p-7 text-center text-sm text-muted-foreground">{texto}</div>; }
function moverParaInicio<T extends { id: string }>(dados: T[], id: string | null): T[] { if (!id) return dados; return [...dados].sort((a, b) => a.id === id ? -1 : b.id === id ? 1 : 0); }
function decomporTempo(minutos: number): { valor: number; unidade: UnidadeTempo } { return minutos % 1440 === 0 ? { valor: minutos / 1440, unidade: "DIAS" } : { valor: minutos / 60, unidade: "HORAS" }; }
function labelTempoCompleto(minutos: number, t: Textos["automacao"]["regras"]): string {
  const tempo = decomporTempo(minutos);
  const unidade = tempo.unidade === "DIAS" ? tempo.valor === 1 ? t.unidadeDia : t.unidadeDias.toLowerCase() : tempo.valor === 1 ? t.unidadeHora : t.unidadeHoras.toLowerCase();
  return interpolar(t.badgeFollowUp, { tempo: `${tempo.valor} ${unidade}` });
}
function labelDias(dias: number, t: Textos["automacao"]["regras"]): string {
  return interpolar(t.badgeFidelizacao, { dias: `${dias} ${dias === 1 ? t.unidadeDia : t.unidadeDias.toLowerCase()}` });
}
function previewFollowUp(regra: Pick<RegraFollowUp, "tempoMinutos" | "texto">, t: Textos["automacao"]): Preview {
  const decomposto = decomporTempo(regra.tempoMinutos);
  const unidade = decomposto.unidade === "DIAS" ? decomposto.valor === 1 ? t.regras.unidadeDia : t.regras.unidadeDias.toLowerCase() : decomposto.valor === 1 ? t.regras.unidadeHora : t.regras.unidadeHoras.toLowerCase();
  const tempo = `${decomposto.valor} ${unidade}`;
  return { texto: regra.texto, legenda: interpolar(t.regras.gatilhoFollowUp, { tempo }) };
}
function previewFidelizacao(regra: Pick<RegraFidelizacao, "diasSemContato" | "mensagem">, t: Textos["automacao"]): Preview {
  const dias = `${regra.diasSemContato} ${regra.diasSemContato === 1 ? t.regras.unidadeDia : t.regras.unidadeDias.toLowerCase()}`;
  return { texto: regra.mensagem, legenda: interpolar(t.regras.gatilhoFidelizacao, { dias }) };
}
function previewVazio(t: Textos["automacao"]): Preview { return { texto: t.regras.previewVazio, legenda: "" }; }
function interpolar(modelo: string, valores: Record<string, string>): string { return Object.entries(valores).reduce((texto, [chave, valor]) => texto.replaceAll(`{${chave}}`, valor), modelo); }

/** Snapshot de telemetria: sem esqueleto ao carregar e sem zero inventado em caso de erro. */
function CardsDeTelemetria({ dados, carregando, comErro, onTentarNovamente }: { dados: StatusAutomacaoTelemetria | undefined; carregando: boolean; comErro: boolean; onTentarNovamente: () => void | Promise<unknown> }) {
  const t = useTextos().automacao.telemetria;
  if (carregando) return null;
  if (comErro || !dados) return <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={onTentarNovamente} />;
  return (
    <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
      <CardDeTelemetria icone={<MessageSquareText className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-primary" />} tom="bg-accent" rotulo={t.mensagensEnviadas} valor={dados.mensagensEnviadas.toLocaleString("pt-BR")} />
      <CardDeTelemetria icone={<UserRoundCheck className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-cor-ia" />} tom="bg-cor-ia/10" rotulo={t.clientesTransferidos} valor={dados.clientesTransferidos.toLocaleString("pt-BR")} />
      <CardDeTelemetria icone={<Link2 className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-cor-sucesso" />} tom="bg-cor-sucesso/10" rotulo={t.conexaoAutomacao} status={dados.conexaoAutomacaoAtiva} rotuloAtivo={t.conectado} rotuloInativo={t.desconectado} />
      <CardDeTelemetria icone={<Database className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-cor-sucesso" />} tom="bg-cor-sucesso/10" rotulo={t.statusDoCrm} status={dados.crmOnline} rotuloAtivo={t.online} rotuloInativo={t.offline} />
    </div>
  );
}

function CardDeTelemetria({ icone, tom, rotulo, valor, status, rotuloAtivo, rotuloInativo }: { icone: ReactNode; tom: string; rotulo: string; valor?: string; status?: boolean; rotuloAtivo?: string; rotuloInativo?: string }) {
  return (
    <div className="flex items-center gap-3.5 rounded-xl border bg-card px-4 py-4"><div className={cn("flex size-11 shrink-0 items-center justify-center rounded-xl", tom)}>{icone}</div><div className="min-w-0"><p className="text-xs font-semibold tracking-wide text-muted-foreground">{rotulo}</p>{status != null ? <p className={cn("mt-1 flex items-center gap-1.5 text-sm font-bold", status ? "text-cor-sucesso" : "text-cor-erro")}><span className={cn("size-2 rounded-full", status ? "bg-cor-sucesso" : "bg-cor-erro")} />{status ? rotuloAtivo : rotuloInativo}</p> : <p className="mt-0.5 text-2xl font-bold tracking-tight">{valor}</p>}</div></div>
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
  return (
    <div className="rounded-lg border p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><p className="text-sm font-medium">{parametro.descricao ?? parametro.chave}</p><p className="font-mono text-xs text-muted-foreground">{parametro.chave}</p></div>{parametro.tipo !== "BOOLEAN" && temFaixa && <p className="shrink-0 text-xs text-muted-foreground">{t.faixaLabel}: {parametro.valorMin ?? "—"}–{parametro.valorMax ?? "—"}{parametro.unidade ? ` ${parametro.unidade}` : ""}</p>}</div><div className="mt-3 flex flex-wrap items-center gap-3"><CampoValor parametro={parametro} valor={valor} onChange={setValor} />{foraDaFaixa && <ErroCampo texto={t.erroFaixa} />}{atualizar.isError && <ErroCampo texto={t.erroSalvar} />}<Button size="sm" disabled={!podeSalvar} onClick={() => atualizar.mutate({ chave: parametro.chave, valor })} className="ml-auto">{atualizar.isPending ? t.salvando : t.salvar}</Button></div></div>
  );
}

function CampoValor({ parametro, valor, onChange }: { parametro: ParametroAutomacao; valor: string; onChange: (valor: string) => void }) {
  const t = useTextos().automacao;
  if (parametro.tipo === "BOOLEAN") {
    const ligado = valor === "true";
    return (
      <div className="flex items-center gap-2 text-sm">
        <Switch
          checked={ligado}
          aria-label={parametro.descricao ?? parametro.chave}
          onCheckedChange={(proximo) => onChange(proximo ? "true" : "false")}
        />
        <span>{ligado ? t.ativado : t.desativado}</span>
      </div>
    );
  }
  if (parametro.tipo === "TEXT") return <Textarea value={valor} onChange={(evento) => onChange(evento.target.value)} rows={2} className="min-w-64 flex-1" />;
  return <Input type="number" inputMode="decimal" step={parametro.tipo === "DECIMAL" ? "any" : "1"} min={parametro.valorMin ?? undefined} max={parametro.valorMax ?? undefined} value={valor} onChange={(evento) => onChange(evento.target.value)} className="w-32" />;
}

function valorForaDaFaixa(parametro: ParametroAutomacao, valor: string): boolean {
  if (parametro.tipo !== "INT" && parametro.tipo !== "DECIMAL") return false;
  const numero = Number(valor);
  if (Number.isNaN(numero)) return true;
  if (parametro.valorMin != null && numero < parametro.valorMin) return true;
  return parametro.valorMax != null && numero > parametro.valorMax;
}
