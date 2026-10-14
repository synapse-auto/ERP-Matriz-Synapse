"use client";

import { Fragment, useMemo, useState } from "react";
import { AlarmClock, ArrowLeft, ArrowRight, ArrowUpRight, CheckCheck, ChevronDown, ChevronRight, Clock, Eye, EyeOff, Headset, Inbox, MessageCircleMore, MessageSquare, PauseCircle, Search, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { Skeleton } from "@/components/ui/skeleton";
import { CartaoConversa } from "./cartao-conversa";
import type { ItemInbox } from "@/lib/atendimento/types";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { useTextos } from "@/lib/config/textos-provider";
import { cn, iniciaisDoNome } from "@/lib/utils";
import { totalizarOperacional, useFilaOperacional, useSnapshotOperacional, type FiltroOperacional, type FilaOperacional, type LinhaOperacional, type SnapshotOperacional, type ConversaOperacional, type ConsultaFilaOperacional } from "@/lib/atendimento/painel-operacional";

interface Props {
  onFiltrar: (filtro: FiltroOperacional, nome?: string) => void;
  onAbrir: (atendimentoId: string) => void;
  conectado?: boolean;
  usuarioAtualId?: string;
}

export function PainelOperacional({ onFiltrar, onAbrir, conectado = false, usuarioAtualId }: Props) {
  const catalogo = useTextos();
  const t = catalogo.painelOperacional;
  const consulta = useSnapshotOperacional(true);
  const [busca, setBusca] = useState("");
  const [grupo, setGrupo] = useState("todos");
  const [ocultarVazios, setOcultarVazios] = useState(false);
  const [linhaAberta, setLinhaAberta] = useState<string | null>(null);
  const snapshot = consulta.data;
  const total = useMemo(() => totalizarOperacional(snapshot?.equipe ?? []), [snapshot]);
  const equipe = snapshot?.equipe.filter(linha =>
    (linha.nome ?? t.entrada).toLocaleLowerCase().includes(busca.toLocaleLowerCase())
    && (!ocultarVazios || linha.contagens.emAberto > 0)
    && (grupo === "todos" || (grupo === "online" && linha.presenca === "ONLINE")
      || (grupo === "pendentes" && linha.contagens.pendentes > 0)
      || (grupo === "atraso" && linha.contagens.semResposta > 0))) ?? [];
  const online = snapshot?.equipe.filter(l => l.elegivel && l.presenca === "ONLINE").length ?? 0;
  const elegiveis = snapshot?.equipe.filter(l => l.elegivel).length ?? 0;
  const limite = snapshot?.limiteMinutos;
  const atraso = t.semResposta.replace("{minutos}", String(limite ?? "—"));
  const rotulos = { EM_ABERTO: t.aberto, NOVAS: t.novas, ATIVOS: t.ativos, PENDENTES: t.pendentes,
    SEM_RESPOSTA: atraso, FINALIZADOS: t.finalizados, POTENCIAIS: t.potenciais };
  const cards = [
    { label: t.atendentesOnline, valor: online, detalhe: t.elegiveis.replace("{total}", String(elegiveis)), Icone: Users, classe: "text-cor-sucesso", acao: () => setGrupo("online") },
    { label: t.potenciais, valor: total.potenciais, detalhe: t.naFila, Icone: Inbox, classe: "text-primary", acao: () => onFiltrar({ recorte: "POTENCIAIS" }) },
    { label: t.novas, valor: total.novasMensagens, detalhe: t.conversas.replace("{total}", String(total.novasConversas)), Icone: MessageSquare, classe: "text-cor-sucesso", acao: () => onFiltrar({ recorte: "EM_ABERTO", sinal: "NOVAS" }) },
    { label: t.ativosCard, valor: total.ativos, detalhe: t.emAndamento, Icone: Headset, classe: "text-primary", acao: () => onFiltrar({ recorte: "ATIVOS" }) },
    { label: t.pendentesCard, valor: total.pendentes, detalhe: t.aguardandoRetomada, Icone: PauseCircle, classe: "text-operacional-pendente", acao: () => onFiltrar({ recorte: "PENDENTES" }) },
    { label: atraso, valor: total.semResposta, detalhe: total.esperaDesde && snapshot
      ? t.maiorEsperaTempo.replace("{tempo}", formatarEspera(total.esperaDesde, snapshot.atualizadoEm, t))
      : t.semEspera, Icone: AlarmClock, classe: "text-destructive", acao: () => onFiltrar({ recorte: "EM_ABERTO", sinal: "SEM_RESPOSTA" }) },
  ];
  return (
    <section className="painel-operacional flex h-full min-h-0 min-w-0 flex-col bg-background" aria-label={t.titulo}>
      <header className="flex shrink-0 flex-col gap-3 border-b border-border bg-card px-4 py-4 lg:flex-row lg:items-center lg:px-6">
        <div className="flex min-w-0 items-center gap-3">
        <Button variant="outline" className="lg:hidden" onClick={() => onFiltrar({ recorte: "EM_ABERTO" })}>{t.verConversas}</Button>
        <div className="min-w-0">
          <h2 className="text-base font-bold text-secondary-foreground">{t.titulo}</h2>
          {snapshot && <p className="mt-1 flex items-center gap-1.5 text-[11px] text-muted-foreground"><span aria-label={conectado ? t.conectado : t.desconectado} className={cn("size-1.5 rounded-full", conectado ? "bg-cor-sucesso" : "bg-muted-foreground")} /><time dateTime={snapshot.atualizadoEm}>{t.painelAtualizado.replace("{horario}", new Date(snapshot.atualizadoEm).toLocaleTimeString("pt-BR", { timeZone: snapshot.fuso, hour: "2-digit", minute: "2-digit" }))}</time></p>}
        </div>
        </div>
        <div className="flex w-full min-w-0 flex-wrap items-center gap-2 lg:ml-auto lg:w-auto lg:flex-1 lg:justify-end">
          <div className="relative min-w-0 basis-full sm:min-w-40 sm:flex-1 sm:basis-auto lg:max-w-[340px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input value={busca} onChange={e => setBusca(e.target.value)} placeholder={t.busca} aria-label={t.busca} className="h-10 rounded-lg bg-muted/30 pl-9 text-xs" />
          </div>
          <Seletor className="min-w-0 flex-1 sm:w-auto sm:min-w-44 sm:flex-none" valor={grupo} onChange={setGrupo} placeholder={t.todos} ariaLabel={t.todos} opcoes={[
            { valor: "todos", rotulo: t.todos }, { valor: "online", rotulo: t.online },
            { valor: "pendentes", rotulo: t.comPendentes }, { valor: "atraso", rotulo: t.comAtraso },
          ]} />
          <Button variant="outline" size="icon" className="shrink-0" aria-label={ocultarVazios ? t.mostrarVazios : t.ocultarVazios} aria-pressed={ocultarVazios} onClick={() => setOcultarVazios(v => !v)}>
            {ocultarVazios ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
          </Button>
        </div>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto bg-muted/10 p-4 lg:p-6">
        {consulta.isLoading && <div role="status" aria-label={t.carregando} className="grid grid-cols-2 gap-3 lg:grid-cols-3 2xl:grid-cols-6">{cards.map(c => <Skeleton key={c.label} className="h-36 rounded-xl" />)}</div>}
        {consulta.isError && <div role="alert" className="rounded-xl border border-border bg-card p-4 text-sm"><p>{t.erro}</p><Button variant="outline" onClick={() => void consulta.refetch()} className="mt-3">{t.tentar}</Button></div>}
        {snapshot && !consulta.isError && <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 2xl:grid-cols-6">
            {cards.map(({ label, valor, detalhe, Icone, classe, acao }) => <button key={label} type="button" onClick={acao} className={cn("flex min-h-34 min-w-0 flex-col justify-between gap-2 rounded-[var(--raio-md)] border border-border bg-card p-4 text-left outline-none transition-colors hover:border-primary focus-visible:ring-2 focus-visible:ring-ring", label === atraso && "border-destructive/20 bg-operacional-alerta")}>
              <span className="flex min-h-8 items-center justify-between gap-2"><span className={cn("max-w-28 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground", label === atraso && "text-destructive")}>{label}</span><span className={cn("flex size-8 shrink-0 items-center justify-center rounded-[var(--raio-sm)]", classe, label === atraso ? "bg-destructive text-primary-foreground" : label === t.pendentesCard ? "bg-cor-atencao/15" : classe === "text-primary" ? "bg-primary/10" : "bg-cor-sucesso/10")}><Icone className="size-4" aria-hidden /></span></span>
              <span className={cn("text-3xl font-bold tabular-nums text-secondary-foreground", label === atraso && "text-destructive")}>{valor.toLocaleString("pt-BR")}</span>
              <span className={cn("flex items-center justify-between gap-1 text-[11px] text-muted-foreground", label === atraso && "text-destructive")}><span>{detalhe}</span><ArrowRight className="size-3 shrink-0 opacity-60" aria-hidden /></span>
            </button>)}
          </div>
          <div className="mt-5 min-w-0 overflow-hidden rounded-[var(--raio-md)] border border-border bg-card">
            <div className="flex flex-wrap items-center justify-between gap-2 px-5 py-4"><h3 className="text-sm font-bold text-secondary-foreground">{t.equipe}</h3><p className="text-[11px] text-muted-foreground">{t.orientacao}</p></div>
            <div className="max-w-full overflow-x-auto" tabIndex={0} aria-label={t.equipe}>
              <table className="w-full min-w-[820px] text-[13px]">
                <colgroup>{[20,10,13,11,11,13,11,7,4].map((largura, i) => <col key={i} style={{ width: `${largura}%` }} />)}</colgroup>
                <thead className="border-y border-border bg-muted/25 text-[10px] uppercase tracking-wider text-muted-foreground"><tr>
                  {[t.atendente, t.status, t.novas, t.ativos, t.pendentes, atraso, t.finalizadosAba, t.total].map(label => <th key={label} scope="col" className={cn("px-3 py-3 text-center font-semibold first:text-left", label === atraso && "text-destructive")}>{label}</th>)}<th scope="col"><span className="sr-only">{t.expandir.replace("{nome}", t.equipe)}</span></th>
                </tr></thead>
                <tbody>{equipe.map(linha => {
                  const chave = linha.atendenteId ?? "entrada";
                  const nome = linha.nome ?? t.entrada;
                  const aberto = linhaAberta === chave;
                  const alternar = () => setLinhaAberta(atual => atual === chave ? null : chave);
                  const filtroBase = linha.entrada ? { entrada: true } : { atendenteId: linha.atendenteId! };
                  const valores: [FilaOperacional, number, string][] = [
                    ["NOVAS", linha.contagens.novasMensagens, "bg-cor-sucesso"], ["ATIVOS", linha.contagens.ativos, "bg-primary"],
                    ["PENDENTES", linha.contagens.pendentes, "bg-operacional-pendente"], ["SEM_RESPOSTA", linha.contagens.semResposta, "bg-destructive"],
                    ["FINALIZADOS", linha.contagens.finalizados, "bg-operacional-finalizado"], ["EM_ABERTO", linha.contagens.emAberto, ""],
                  ];
                  return <Fragment key={chave}>
                    <tr className={cn("border-b border-border last:border-b-0", aberto && "bg-muted/20", linha.entrada && "border-t-2")}>
                      <th scope="row" className="max-w-60 px-5 py-3 text-left font-normal"><button type="button" className="flex w-full min-w-0 items-center gap-3 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={alternar} aria-expanded={aberto} aria-controls={`fila-${chave}`}>
                        {linha.entrada ? <Inbox aria-hidden className="size-10 shrink-0 rounded-[var(--raio-sm)] bg-primary/10 p-2 text-primary" /> : <AvatarIniciais id={chave} nome={nome} fotoUrl={linha.fotoUrl} className="flex size-10 shrink-0 items-center justify-center rounded-[var(--raio-sm)] text-xs font-bold text-primary-foreground" />}
                        <span className="min-w-0"><span className="block truncate font-semibold text-secondary-foreground">{nome}</span><span className="block truncate text-[11px] italic text-muted-foreground">{linha.entrada ? t.filaDescricao : linha.papel && catalogo.gestao.papeis[linha.papel as keyof typeof catalogo.gestao.papeis]}{linha.atendenteId === usuarioAtualId && ` · ${t.voce}`}</span></span>
                      </button></th>
                      <td className={cn("whitespace-nowrap px-3 py-3 text-xs", linha.presenca === "ONLINE" ? "text-cor-sucesso" : linha.presenca === "AUSENTE" ? "text-operacional-pendente" : "text-muted-foreground")}>{linha.entrada ? "—" : <><span className={cn("mr-1.5 inline-block size-1.5 rounded-full", linha.presenca === "ONLINE" ? "bg-cor-sucesso" : linha.presenca === "AUSENTE" ? "bg-cor-atencao" : "bg-muted-foreground/40")} aria-hidden />{linha.presenca === "ONLINE" ? t.onlineStatus : linha.presenca === "AUSENTE" ? t.ausenteStatus : t.offlineStatus}</>}</td>
                      {valores.map(([fila, valor, cor]) => <td key={fila} className="px-2 py-3 text-center">{linha.entrada && valor === 0 && ["ATIVOS","PENDENTES","FINALIZADOS"].includes(fila) ? <span className="text-muted-foreground">—</span> : <button type="button" onClick={() => onFiltrar({ ...filtroBase, recorte: fila === "NOVAS" || fila === "SEM_RESPOSTA" ? "EM_ABERTO" : fila, sinal: fila === "NOVAS" || fila === "SEM_RESPOSTA" ? fila : "TODOS" }, nome)} className={cn("inline-flex h-8 w-full min-w-8 items-center justify-center gap-1 rounded-[var(--raio-sm)] px-2 text-xs font-bold tabular-nums outline-none transition-opacity hover:opacity-80 focus-visible:ring-2 focus-visible:ring-ring", fila === "EM_ABERTO" ? "text-secondary-foreground" : valor > 0 ? `${cor} text-primary-foreground` : "text-muted-foreground/70")} aria-label={`${nome}: ${rotulos[fila]} (${valor})`}>{valor > 0 && fila === "SEM_RESPOSTA" && <AlarmClock className="size-3" aria-hidden />}{valor > 0 && fila === "FINALIZADOS" && <CheckCheck className="size-3" aria-hidden />}{valor}</button>}</td>)}
                      <td className="pr-3"><Button variant="outline" size="icon" className="size-8 rounded-[var(--raio-sm)]" aria-label={(aberto ? t.recolher : t.expandir).replace("{nome}", nome)} aria-expanded={aberto} aria-controls={`fila-${chave}`} onClick={alternar}>{aberto ? <ChevronDown aria-hidden className="size-4" /> : <ChevronRight aria-hidden className="size-4" />}</Button></td>
                    </tr>
                    {aberto && <tr id={`fila-${chave}`}><td colSpan={9}><FilaExpandida linha={linha} rotulos={rotulos} referencia={snapshot} onAbrir={onAbrir} onFiltrar={onFiltrar} /></td></tr>}
                  </Fragment>;
                })}</tbody>
              </table>
            </div>
            {equipe.length === 0 && <p className="p-6 text-center text-sm text-muted-foreground">{t.equipeVazia}</p>}
          </div>
          <p className="mt-3 text-xs text-muted-foreground">{t.periodo.replace("{fuso}", snapshot.fuso)}</p>
        </>}
      </div>
    </section>
  );
}

function FilaExpandida({ linha, rotulos, referencia, onFiltrar, onAbrir }: Props & { linha: LinhaOperacional; rotulos: Record<FilaOperacional, string>; referencia: SnapshotOperacional }) {
  const t = useTextos().painelOperacional;
  const [fila, setFila] = useState<FilaOperacional>("EM_ABERTO");
  const filtro: FiltroOperacional = { recorte: fila, ...(linha.entrada ? { entrada: true } : { atendenteId: linha.atendenteId! }) };
  const consulta = useFilaOperacional(filtro, true);
  const contagens: Record<FilaOperacional, number> = { EM_ABERTO: linha.contagens.emAberto, NOVAS: linha.contagens.novasConversas, ATIVOS: linha.contagens.ativos, PENDENTES: linha.contagens.pendentes, SEM_RESPOSTA: linha.contagens.semResposta, FINALIZADOS: linha.contagens.finalizados, POTENCIAIS: linha.contagens.potenciais };
  return <section className="border-b border-border bg-muted/15 px-6 py-4 lg:px-10" aria-label={linha.nome ?? t.entrada}>
    <div className="mb-3 flex flex-wrap items-center gap-1.5">{(["EM_ABERTO", "NOVAS", "ATIVOS", "PENDENTES", "SEM_RESPOSTA", "FINALIZADOS"] as FilaOperacional[]).filter(f => !linha.entrada || !["ATIVOS", "PENDENTES", "FINALIZADOS"].includes(f)).map(f => <Button key={f} size="sm" variant="outline" className={cn("h-8 gap-1.5 rounded-md px-3 text-[11px]", fila === f && "border-secondary-foreground bg-secondary-foreground text-background hover:bg-secondary-foreground hover:text-background")} aria-pressed={fila === f} aria-label={`${rotulos[f]} (${contagens[f]})`} onClick={() => setFila(f)}>{f === "FINALIZADOS" ? t.finalizadosAba : rotulos[f]}<span aria-hidden className={cn("rounded-full bg-muted px-1.5 text-[10px] tabular-nums text-muted-foreground", fila === f && "bg-background/20 text-background")}>{contagens[f]}</span></Button>)}<Button size="sm" variant="ghost" className="ml-auto gap-1 text-xs text-primary" onClick={() => onFiltrar(filtro, linha.nome ?? t.entrada)}>{t.filtrarLista}<ArrowLeft className="size-3" aria-hidden /></Button></div>
    <ListaFilaOperacional consulta={consulta} onAbrir={onAbrir} referencia={referencia} tabela />
  </section>;
}

export function ListaFilaOperacional({ consulta, onAbrir, tabela = false, referencia, selecionadoId, internos = [], onAbrirInterno }: { consulta: ConsultaFilaOperacional; onAbrir: (id: string) => void; tabela?: boolean; referencia?: SnapshotOperacional; selecionadoId?: string | null; internos?: ItemInbox[]; onAbrirInterno?: (cartao: ItemInbox) => void }) {
  const catalogo = useTextos();
  const t = catalogo.painelOperacional;
  const itens = consulta.data?.pages.flatMap(p => p.itens) ?? [];
  const mesclados = [
    ...itens.map(item => ({ tipo: "CLIENTE" as const, id:item.atendimentoId, horario:item.atividadeEm, item })),
    ...internos.filter(item => item.tipo === "EQUIPE_INTERNA").map(item => ({ tipo: "INTERNO" as const, id:item.conversaId, horario:item.ultimaMensagemEm ?? "", item })),
  ].sort((a,b) => (b.horario ?? "").localeCompare(a.horario ?? "") || b.id.localeCompare(a.id));
  return <div aria-live="polite">
    {consulta.isLoading && <Skeleton className="h-20 w-full" aria-label={t.carregando} />}
    {consulta.isError && <div role="alert" className="p-3 text-sm"><p>{t.erro}</p><Button variant="outline" onClick={() => void consulta.refetch()}>{t.tentar}</Button></div>}
    {!consulta.isLoading && !consulta.isError && itens.length === 0 && internos.length === 0 && <p className="p-5 text-center text-sm text-muted-foreground">{t.vazio}</p>}
    {tabela && itens.length > 0 ? <div className="overflow-hidden rounded-[var(--raio-md)] border border-border bg-card"><table className="w-full table-fixed text-xs">
      <colgroup>{[24,13,10,26,17,10].map((largura, i) => <col key={i} style={{ width: `${largura}%` }} />)}</colgroup>
      <thead className="border-b border-border bg-muted/15 text-[10px] uppercase tracking-wide text-muted-foreground"><tr>{[t.lead,t.etapa,t.situacao,t.ultimaMensagem,t.espera,t.hora].map(label => <th scope="col" key={label} className="px-3 py-2.5 text-left font-medium">{label}</th>)}</tr></thead>
      <tbody className="divide-y divide-border">{itens.map(item => <tr key={item.atendimentoId} className="hover:bg-muted/15">
        <td className="px-3 py-2.5"><button type="button" aria-label={t.abrir.replace("{nome}",item.leadNome)} onClick={() => onAbrir(item.atendimentoId)} className="flex w-full min-w-0 items-center gap-2 rounded-md text-left outline-none focus-visible:ring-2 focus-visible:ring-ring"><div className="relative shrink-0"><AvatarIniciais id={item.leadId} nome={item.leadNome} fotoUrl={item.leadFotoUrl} className="flex size-8 shrink-0 items-center justify-center rounded-[var(--raio-sm)] text-[10px] font-bold text-primary-foreground" />{item.naoLidas > 0 && <span className="absolute -right-1 -top-1 flex min-w-3.5 items-center justify-center rounded-full bg-cor-sucesso px-0.5 text-[9px] text-primary-foreground">{item.naoLidas}</span>}</div><span className="min-w-0"><span className="block truncate text-xs font-semibold text-secondary-foreground">{item.leadNome}</span>{item.leadEmpresa && <span className="block truncate text-[10px] text-muted-foreground">{item.leadEmpresa}</span>}</span></button></td>
        <td className="px-3 py-2.5"><EtapaDaFila item={item} /></td>
        <td className="px-3 py-2.5"><SituacaoDaFila item={item} /></td>
        <td className="px-3 py-2.5"><span className="block truncate text-[11px] text-muted-foreground">{previaDaFila(item,catalogo)}</span></td>
        <td className="px-3 py-2.5"><EsperaDaFila item={item} referencia={referencia} /></td>
        <td className="px-2 py-2.5"><span className="flex items-center justify-end gap-2 text-[10px] text-muted-foreground"><time dateTime={item.ultimaMensagemEm ?? item.atividadeEm}>{horaDaFila(item,referencia?.fuso)}</time><button type="button" aria-label={`${t.abrirTodos}: ${item.leadNome}`} className="shrink-0 rounded p-0.5 text-muted-foreground outline-none hover:text-primary focus-visible:ring-2 focus-visible:ring-ring" onClick={() => onAbrir(item.atendimentoId)}><ArrowUpRight className="size-3" aria-hidden /></button></span></td>
      </tr>)}</tbody>
    </table></div> : <ul>{mesclados.map(entrada => { if (entrada.tipo === "INTERNO") return <li key={`interno-${entrada.id}`}><CartaoConversa cartao={entrada.item} selecionado={entrada.item.conversaId === selecionadoId} onAbrirAtendimento={() => onAbrirInterno?.(entrada.item)} /></li>; const item = entrada.item; return <li key={item.atendimentoId}>
      <button type="button" className={cn("m-1.5 flex min-h-28 w-[calc(100%-0.75rem)] min-w-0 items-start gap-3 rounded-[var(--raio-sm)] border border-transparent px-4 py-3 text-left outline-none hover:bg-muted/20 focus-visible:ring-2 focus-visible:ring-ring", item.leadId === selecionadoId && "border-primary/25 bg-primary/5")} aria-current={item.leadId === selecionadoId ? "true" : undefined} aria-label={t.abrir.replace("{nome}", item.leadNome)} onClick={() => onAbrir(item.atendimentoId)}>
        <AvatarIniciais id={item.leadId} nome={item.leadNome} fotoUrl={item.leadFotoUrl} className="flex size-11 shrink-0 items-center justify-center rounded-[var(--raio-sm)] text-sm font-bold text-primary-foreground" />
        <span className="min-w-0 flex-1"><span className="flex items-center justify-between gap-2"><span className="truncate text-[13px] font-semibold text-secondary-foreground">{item.leadNome}</span><time className="shrink-0 text-[10px] text-muted-foreground" dateTime={item.ultimaMensagemEm ?? item.atividadeEm}>{horaDaFila(item,referencia?.fuso)}</time></span>{item.leadEmpresa && <span className="block truncate text-[11px] text-muted-foreground">{item.leadEmpresa}</span>}<span className="mt-1 block truncate text-xs text-muted-foreground">{previaDaFila(item,catalogo)}</span><span className="mt-2 flex min-w-0 items-center gap-1.5">{item.canalTipo === "WHATSAPP" && <MessageCircleMore aria-label={catalogo.atendimentos.canais.whatsapp} className="size-3 shrink-0 text-cor-sucesso" />}<EtapaDaFila item={item} /><span className="ml-auto inline-flex min-w-0 items-center gap-1.5">{item.esperaDesde && referencia && new Date(referencia.atualizadoEm).getTime()-new Date(item.esperaDesde).getTime() >= referencia.limiteMinutos*60_000 && <EsperaDaFila item={item} referencia={referencia} />}{item.atendenteNome && <span title={item.atendenteNome} className="shrink-0 rounded-md bg-muted px-1.5 py-1 text-[9px] text-foreground">{iniciaisDoNome(item.atendenteNome)}</span>}{item.naoLidas > 0 && <span className="shrink-0 rounded-full bg-primary px-1.5 py-0.5 text-[10px] text-primary-foreground">{item.naoLidas}</span>}</span></span></span>
      </button>
    </li>; })}</ul>}
    {consulta.hasNextPage && <Button className="mt-3 w-full" variant="outline" disabled={consulta.isFetchingNextPage} onClick={() => void consulta.fetchNextPage()}>{t.mais}</Button>}
  </div>;
}

function formatarEspera(desde: string, agora: string, t: ReturnType<typeof useTextos>["painelOperacional"]) {
  const minutos = Math.max(0,Math.floor((new Date(agora).getTime()-new Date(desde).getTime())/60_000));
  return minutos < 60 ? t.tempoMinutos.replace("{minutos}",String(minutos)) : t.tempoHoras.replace("{horas}",String(Math.floor(minutos/60))).replace("{minutos}",String(minutos%60).padStart(2,"0"));
}

function horaDaFila(item: ConversaOperacional, fuso?: string) {
  const data = new Date(item.ultimaMensagemEm ?? item.atividadeEm);
  return Number.isFinite(data.getTime()) ? data.toLocaleTimeString("pt-BR",{ timeZone:fuso,hour:"2-digit",minute:"2-digit" }) : "—";
}

function previaDaFila(item: ConversaOperacional, catalogo: ReturnType<typeof useTextos>) {
  if (item.ultimaMensagemPreview) return item.ultimaMensagemPreview;
  const media = catalogo.atendimentos.media;
  return ({ AUDIO:media.audio, VIDEO:media.visualizador.video, IMAGEM:media.imagem, DOCUMENTO:media.documento, LOCALIZACAO:media.localizacao } as Record<string,string>)[item.ultimaMensagemTipo ?? ""] ?? "—";
}

function EtapaDaFila({item}: {item:ConversaOperacional}) {
  return item.etapaNome ? <span className="inline-block max-w-full truncate rounded-md bg-muted px-2 py-0.5 text-[10px] font-medium text-muted-foreground" style={item.etapaCor ? { color:item.etapaCor,backgroundColor:`color-mix(in srgb, ${item.etapaCor} 12%, transparent)` } : undefined}>{item.etapaNome}</span> : <span className="text-muted-foreground">—</span>;
}

function SituacaoDaFila({item}: {item:ConversaOperacional}) {
  const t = useTextos().painelOperacional;
  const estado = item.situacao ?? item.status;
  const label = ({ PENDENTE:t.pendente,EM_ATENDIMENTO:t.ativo,EM_IA:t.potenciais,FINALIZADO:t.finalizado } as Record<string,string>)[estado];
  return label ? <span className={cn("inline-block max-w-full truncate rounded-md px-2 py-0.5 text-[10px] font-medium",estado === "PENDENTE" ? "bg-cor-atencao/15 text-operacional-pendente" : estado === "FINALIZADO" ? "bg-cor-sucesso/10 text-cor-sucesso" : "bg-primary/10 text-primary")}>{label}</span> : <span className="text-muted-foreground">—</span>;
}

function EsperaDaFila({item,referencia}: {item:ConversaOperacional;referencia?:SnapshotOperacional}) {
  const t = useTextos().painelOperacional;
  if (item.respondido && !item.esperaDesde) return <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground"><CheckCheck className="size-3" aria-hidden />{t.respondido}</span>;
  if (!item.esperaDesde || !referencia) return <span className="text-muted-foreground">—</span>;
  const atrasado = new Date(referencia.atualizadoEm).getTime()-new Date(item.esperaDesde).getTime() >= referencia.limiteMinutos*60_000;
  const tempo = formatarEspera(item.esperaDesde,referencia.atualizadoEm,t);
  const Icone = atrasado ? AlarmClock : Clock;
  const label = (atrasado ? t.esperaAtrasada : t.esperaHa).replace("{tempo}",tempo);
  return <span title={label} className={cn("inline-flex min-w-0 max-w-full items-center gap-1 whitespace-nowrap rounded-md px-2 py-0.5 text-[10px] font-medium",atrasado ? "bg-destructive/10 text-destructive" : "bg-cor-atencao/15 text-operacional-pendente")}><Icone className="size-3 shrink-0" aria-hidden /><span className="truncate">{label}</span></span>;
}
