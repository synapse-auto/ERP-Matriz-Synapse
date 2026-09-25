"use client";

import { useRef, useState, type ReactNode } from "react";
import {
  ArrowLeft,
  ArrowLeftRight,
  CheckCheck,
  MessageCircleMore,
  MessageCirclePlus,
  PanelRightOpen,
  Phone,
  Search,
  Tag,
  UserPlus,
} from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  DropdownMenuCheckboxItem,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import { ErroDeApi } from "@/lib/api/errors";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useFinalizarAtendimento } from "@/lib/atendimento/use-transferir-finalizar";
import type {
  AtendimentoResumo,
  CartaoAtendimento,
  EstadoAtendimentoSelecionado,
} from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { useLead } from "@/lib/lead/use-painel-lead";
import {
  aprovarPedido,
  entrarAtendimento,
  invalidarParticipacao,
  pedirEntrada,
  recusarPedido,
  sairAtendimento,
  useMeuPedido,
  usePedidosPendentes,
} from "@/lib/atendimento/use-participacao";
import { cn } from "@/lib/utils";

import { DialogoTransferir } from "./dialogo-transferir";
import { DialogoConvidar } from "./dialogo-convidar";
import { AtalhoTags, DialogoTagsDoLead } from "./atalho-tags";
import { BarraDeAcoesComTransbordo, type AcaoTransbordavel } from "./barra-de-acoes-com-transbordo";

/**
 * E210 — espaço que a identificação da conversa (avatar + nome + canal) sempre mantém antes de as
 * ações começarem a ir para o "⋯". Constante de layout, não regra de negócio.
 */
const LARGURA_MINIMA_DA_IDENTIFICACAO_PX = 224;

/** Ordem de saída para o "⋯": menor sai primeiro. Participação, Finalizar e Novo atendimento são fixos. */
const PRIORIDADE_TELEFONE = 20;
const PRIORIDADE_CONVIDAR = 30;
const PRIORIDADE_TAGS = 40;
const PRIORIDADE_REABRIR_PAINEL = 45;
const PRIORIDADE_BUSCAR = 50;
const PRIORIDADE_TRANSFERIR = 60;

/** Botão da barra e item do menu a partir da mesma definição — estados nunca divergem. */
function acaoDeBotao({
  id,
  prioridade,
  rotulo,
  icone,
  aoSelecionar,
  desabilitado = false,
}: {
  id: string;
  prioridade: number;
  rotulo: string;
  icone: ReactNode;
  aoSelecionar: () => void;
  desabilitado?: boolean;
}): AcaoTransbordavel {
  return {
    id,
    prioridade,
    inline: (
      <Button type="button" variant="outline" size="sm" onClick={aoSelecionar} disabled={desabilitado}>
        {icone}
        {rotulo}
      </Button>
    ),
    itemDeMenu: (
      <DropdownMenuItem onClick={aoSelecionar} disabled={desabilitado}>
        {icone}
        {rotulo}
      </DropdownMenuItem>
    ),
  };
}

type Props = {
  conversa: CartaoAtendimento;
  estado: EstadoAtendimentoSelecionado;
  onReconciliarEstado?: () => Promise<void>;
  onAlternarBusca: () => void;
  buscaAberta: boolean;
  painelDetalhesAberto: boolean;
  onAlternarPainelDetalhes: () => void;
  onVoltar?: () => void;
  onAbrirNovoAtendimento?: () => void;
  abrindoNovoAtendimento?: boolean;
  onAtendimentoFinalizado?: (resumo: AtendimentoResumo) => void;
};

/** Identificação da conversa, tags persistidas e ações operacionais. */
export function CabecalhoConversa({
  conversa,
  estado,
  onReconciliarEstado,
  onAlternarBusca,
  buscaAberta,
  painelDetalhesAberto,
  onAlternarPainelDetalhes,
  onVoltar,
  onAbrirNovoAtendimento,
  abrindoNovoAtendimento = false,
  onAtendimentoFinalizado,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.cabecalho;
  const [transferirAberto, setTransferirAberto] = useState(false);
  const [convidarAberto, setConvidarAberto] = useState(false);
  const [tagsAberto, setTagsAberto] = useState(false);
  const cabecalhoRef = useRef<HTMLDivElement>(null);
  const finalizar = useFinalizarAtendimento(onAtendimentoFinalizado);
  const papel = useAuthStore((estado) => estado.papel);
  const lead = useLead(conversa.leadId);
  const participantes = estado.participantes;
  const meuPedido = useMeuPedido(conversa.atendimentoId);
  const pedidosPendentes = usePedidosPendentes(conversa.atendimentoId);
  const [estadoLocal, setEstadoLocal] = useState<"SEM_PEDIDO" | "PENDENTE" | "DENTRO" | "RECUSADO">("SEM_PEDIDO");
  const [processandoParticipacao, setProcessandoParticipacao] = useState(false);
  const [feedbackParticipacao, setFeedbackParticipacao] = useState<{ tipo: "erro" | "sucesso"; texto: string } | null>(null);
  const finalizado = conversa.status === "FINALIZADO";
  const telefone = lead.data?.telefone ?? null;
  const nomeDoLead = lead.data?.nome ?? conversa.leadNome;
  const canal =
    conversa.canalTipo === "WHATSAPP"
      ? catalogo.atendimentos.canais.whatsapp
      : conversa.canalTipo;

  const estaDentro = estado.usuarioAtualParticipa;
  const ehResponsavel = estado.usuarioAtualEhResponsavel;
  const estadoPersistido = estaDentro
    ? "DENTRO"
    : meuPedido?.status === "PENDENTE"
      ? "PENDENTE"
      : meuPedido?.status === "RECUSADO"
        ? "RECUSADO"
        : estadoLocal;
  const podeEntrarDireto = papel !== "ATENDENTE" && !estaDentro && !ehResponsavel;
  const acoesDoCabecalho = [
    {
      id: "convidar",
      texto: textos.convidar,
      visivel: !finalizado && (ehResponsavel || estaDentro || papel !== "ATENDENTE"),
      abrir: () => setConvidarAberto(true),
    },
  ];

  async function executarParticipacao(
    acao: () => Promise<unknown>,
    proximo: "SEM_PEDIDO" | "PENDENTE" | "DENTRO" | "RECUSADO",
    sucesso: string,
  ) {
    setProcessandoParticipacao(true);
    setFeedbackParticipacao(null);
    try {
      await acao();
    } catch (erro) {
      setFeedbackParticipacao({ tipo: "erro", texto: mensagemDeErroParticipacao(erro, textos) });
      setProcessandoParticipacao(false);
      return;
    }

    setEstadoLocal(proximo);
    setFeedbackParticipacao({ tipo: "sucesso", texto: sucesso });
    invalidarParticipacao(conversa.atendimentoId);

    // A ação já foi concluída. A reconciliação é apenas best-effort: ao sair, perder acesso
    // ao atendimento faz o snapshot retornar 403/404 e não pode transformar o sucesso em erro.
    try {
      await onReconciliarEstado?.();
    } catch {
      // O próximo evento/refetch pode atualizar a tela; o feedback da ação permanece sucesso.
    }
    setProcessandoParticipacao(false);
  }

  const subtitulo = [
    telefone,
    lead.data?.empresa ?? conversa.leadEmpresa,
    conversa.atendenteNome
      ? `${textos.atendidoPor} ${conversa.atendenteNome}`
      : textos.semAtendente,
  ]
    .filter(Boolean)
    .join(" · ");

  const iconeDeAcao = "size-[calc(var(--tamanho-icone-interface)*0.875)]";
  const iconeUtilitario = "size-(--tamanho-icone-interface)";
  const rotuloTelefone = telefone ? `${catalogo.painelLead.dados.telefone}: ${telefone}` : "";
  const hrefTelefone = telefone ? `tel:${telefone.replace(/[^+\d]/g, "")}` : "";
  const mostrarParticipacao = (!finalizado && !ehResponsavel)
    || (!finalizado && ehResponsavel && pedidosPendentes.length > 0)
    || feedbackParticipacao !== null;

  // Ordem visual preservada. Fixos nunca saem; os demais saem para o "⋯" do menor para o maior
  // `prioridade` quando o espaço real não comporta (E210). Botão e item de menu nascem da mesma
  // definição: mesmo rótulo, mesmo handler, mesmo estado desabilitado.
  const acoes: AcaoTransbordavel[] = [];
  if (mostrarParticipacao) {
    acoes.push({
      id: "participacao",
      fixo: true,
      inline: (
        <div className="flex max-w-full flex-wrap items-center justify-end gap-2">
          {/* Entrar direto nao tem descricao: o botao fica sozinho e alinha na linha dos demais.
              Pedir entrada mantem a coluna, porque o aviso de aprovacao continua no catalogo. */}
          {!finalizado && !ehResponsavel && estadoPersistido === "SEM_PEDIDO" && podeEntrarDireto && (
            <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => entrarAtendimento(conversa.atendimentoId), "DENTRO", textos.sucessoEntrou)} disabled={processandoParticipacao}>
              {textos.entrar}
            </Button>
          )}
          {!finalizado && !ehResponsavel && estadoPersistido === "SEM_PEDIDO" && !podeEntrarDireto && (
            <span className="flex max-w-56 flex-col items-end gap-0.5 text-right">
              <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => pedirEntrada(conversa.atendimentoId), "PENDENTE", textos.sucessoPedido)} disabled={processandoParticipacao}>
              {textos.pedirEntrada}
              </Button>
              <span className="text-[0.65rem] leading-tight text-muted-foreground">{textos.pedirEntradaDescricao}</span>
            </span>
          )}
          {!finalizado && !ehResponsavel && estadoPersistido === "PENDENTE" && (
            meuPedido?.tipo === "CONVITE" ? (
              <span className="flex max-w-64 flex-col items-end gap-1 text-right text-[0.65rem] text-muted-foreground">
                <span className="truncate">{textos.convitePendente}</span>
                <span>{textos.conviteRecebidoDescricao}</span>
                <span className="flex gap-1">
                  <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => aprovarPedido(meuPedido.id), "DENTRO", textos.sucessoConviteAceito)} disabled={processandoParticipacao}>{textos.aceitarConvite}</Button>
                  <Button type="button" variant="ghost" size="sm" onClick={() => executarParticipacao(() => recusarPedido(meuPedido.id), "SEM_PEDIDO", textos.sucessoConviteRecusado)} disabled={processandoParticipacao}>{textos.recusarConvite}</Button>
                </span>
              </span>
            ) : (
              <span className="flex max-w-64 flex-col items-end gap-0.5 text-right text-[0.65rem] text-muted-foreground">
                <Button type="button" variant="outline" size="sm" disabled>{textos.pedidoPendente}</Button>
                <span className="truncate">{textos.pedidoEnviado.replace("{nome}", conversa.atendenteNome ?? textos.semAtendente)}</span>
                {meuPedido?.solicitadoEm && <span>{textos.pedidoSolicitadoEm.replace("{horario}", formatarHorario(meuPedido.solicitadoEm))}</span>}
                <span>{textos.pedidoValidadeConfigurada}</span>
              </span>
            )
          )}
          {!finalizado && !ehResponsavel && estadoPersistido === "RECUSADO" && (
            <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => pedirEntrada(conversa.atendimentoId), "PENDENTE", textos.sucessoPedido)} disabled={processandoParticipacao}>{textos.recusado}</Button>
          )}
          {!finalizado && !ehResponsavel && estadoPersistido === "DENTRO" && (
            <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => sairAtendimento(conversa.atendimentoId), "SEM_PEDIDO", textos.sucessoSaiu)} disabled={processandoParticipacao}>{textos.sair}</Button>
          )}
          {!finalizado && pedidosPendentes.length > 0 && ehResponsavel && pedidosPendentes.map((pedido) => (
            <span key={pedido.id} className="flex min-w-0 flex-wrap items-center justify-end gap-1 rounded-md border border-border/60 px-2 py-1">
              <span className="max-w-32 truncate text-xs font-medium" title={pedido.solicitanteNome}>{pedido.solicitanteNome}</span>
              <span className="sr-only">{textos.pedidoRecebido.replace("{nome}", pedido.solicitanteNome)}</span>
              <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => aprovarPedido(pedido.id), "SEM_PEDIDO", textos.sucessoAprovado.replace("{nome}", pedido.solicitanteNome))} disabled={processandoParticipacao}>{textos.aprovarEntrada}</Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => executarParticipacao(() => recusarPedido(pedido.id), "SEM_PEDIDO", textos.sucessoRecusado.replace("{nome}", pedido.solicitanteNome))} disabled={processandoParticipacao}>{textos.recusarEntrada}</Button>
            </span>
          ))}
          {feedbackParticipacao && (
            <p role={feedbackParticipacao.tipo === "erro" ? "alert" : "status"} aria-live="polite" className={cn("max-w-64 text-xs", feedbackParticipacao.tipo === "erro" ? "text-destructive" : "text-cor-sucesso")}>
              {feedbackParticipacao.texto}
            </p>
          )}
        </div>
      ),
    });
  }
  if (!finalizado) {
    for (const acao of acoesDoCabecalho.filter((item) => item.visivel)) {
      acoes.push(acaoDeBotao({
        id: acao.id,
        prioridade: PRIORIDADE_CONVIDAR,
        rotulo: acao.texto,
        icone: <UserPlus className={iconeDeAcao} aria-hidden />,
        aoSelecionar: acao.abrir,
      }));
    }
    acoes.push(acaoDeBotao({
      id: "transferir",
      prioridade: PRIORIDADE_TRANSFERIR,
      rotulo: textos.transferir,
      icone: <ArrowLeftRight className={iconeDeAcao} aria-hidden />,
      aoSelecionar: () => setTransferirAberto(true),
    }));
    acoes.push({
      id: "finalizar",
      fixo: true,
      inline: (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="border-cor-sucesso/25 bg-cor-sucesso/10 text-cor-sucesso hover:bg-cor-sucesso/15 hover:text-cor-sucesso"
          onClick={() => finalizar.mutate(conversa.atendimentoId)}
          disabled={finalizar.isPending}
        >
          <CheckCheck className={iconeDeAcao} aria-hidden />
          {textos.finalizar}
        </Button>
      ),
    });
  }
  if (finalizado && onAbrirNovoAtendimento) {
    acoes.push({
      id: "novo-atendimento",
      fixo: true,
      inline: (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onAbrirNovoAtendimento}
          disabled={abrindoNovoAtendimento}
        >
          <MessageCirclePlus className={iconeDeAcao} aria-hidden />
          {textos.novoAtendimento}
        </Button>
      ),
    });
  }
  acoes.push({ id: "separador", fixo: true, inline: <span className="mx-1 h-5 w-px bg-border" aria-hidden /> });
  acoes.push({
    id: "buscar",
    prioridade: PRIORIDADE_BUSCAR,
    inline: (
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={textos.buscar}
        aria-pressed={buscaAberta}
        onClick={onAlternarBusca}
      >
        <Search className={iconeUtilitario} aria-hidden />
      </Button>
    ),
    itemDeMenu: (
      <DropdownMenuCheckboxItem checked={buscaAberta} onCheckedChange={() => onAlternarBusca()} closeOnClick>
        <Search className={iconeUtilitario} aria-hidden />
        {textos.buscar}
      </DropdownMenuCheckboxItem>
    ),
  });
  acoes.push({
    id: "tags",
    prioridade: PRIORIDADE_TAGS,
    inline: <AtalhoTags leadId={conversa.leadId} />,
    itemDeMenu: (
      <DropdownMenuItem onClick={() => setTagsAberto(true)}>
        <Tag className={iconeUtilitario} aria-hidden />
        {catalogo.painelLead.tags.titulo}
      </DropdownMenuItem>
    ),
  });
  if (telefone) {
    acoes.push({
      id: "telefone",
      prioridade: PRIORIDADE_TELEFONE,
      inline: (
        <a
          href={hrefTelefone}
          aria-label={rotuloTelefone}
          className={cn(buttonVariants({ variant: "ghost", size: "icon" }))}
        >
          <Phone className={iconeUtilitario} aria-hidden />
        </a>
      ),
      itemDeMenu: (
        <DropdownMenuItem render={<a href={hrefTelefone} />}>
          <Phone className={iconeUtilitario} aria-hidden />
          {rotuloTelefone}
        </DropdownMenuItem>
      ),
    });
  }
  if (!painelDetalhesAberto) {
    acoes.push({
      id: "reabrir-painel",
      prioridade: PRIORIDADE_REABRIR_PAINEL,
      inline: (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onAlternarPainelDetalhes}
          aria-expanded="false"
          aria-controls="painel-detalhes-lead"
          aria-label={catalogo.atendimentos.painel.reabrir}
          title={catalogo.atendimentos.painel.reabrir}
        >
          <PanelRightOpen className={iconeUtilitario} aria-hidden />
        </Button>
      ),
      itemDeMenu: (
        <DropdownMenuItem onClick={onAlternarPainelDetalhes}>
          <PanelRightOpen className={iconeUtilitario} aria-hidden />
          {catalogo.atendimentos.painel.reabrir}
        </DropdownMenuItem>
      ),
    });
  }

  return (
    <div
      ref={cabecalhoRef}
      className="flex min-h-[72px] shrink-0 flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-border bg-background px-3 py-2 sm:px-5"
      data-slot="cabecalho-conversa"
    >
      <div className="flex min-w-0 flex-1 items-center gap-3">
        {onVoltar && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={textos.voltar}
            onClick={onVoltar}
          >
            <ArrowLeft className="size-[calc(var(--tamanho-icone-interface)*1.25)]" aria-hidden />
          </Button>
        )}
        <AvatarIniciais
          id={conversa.leadId}
          nome={nomeDoLead}
          fotoUrl={conversa.leadFotoUrl}
          fotoAlt={nomeDoLead}
          className="flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white"
        />
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="truncate font-bold text-foreground" title={nomeDoLead}>
              {nomeDoLead}
            </p>
            {canal && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-md bg-cor-sucesso/10 px-2 py-0.5 text-[0.7rem] font-semibold text-cor-sucesso">
                <MessageCircleMore className="size-[calc(var(--tamanho-icone-interface)*0.75)]" aria-hidden />
                {canal}
              </span>
            )}
          </div>
          <p className="truncate text-xs text-muted-foreground">{subtitulo}</p>
          {participantes.length > 0 && (
            <div className="mt-1 flex min-w-0 items-center gap-1 text-[0.65rem] text-muted-foreground" aria-label={textos.participantes}>
              <span className="shrink-0 font-medium">{textos.participantes}:</span>
              <span className="min-w-0 truncate">
                {participantes.map((participante) => participante.nome).join(", ")}
              </span>
              <div className="flex shrink-0 items-center gap-1" aria-hidden="true">
              {participantes.map((participante) => (
                <AvatarIniciais key={participante.usuarioId} id={participante.usuarioId} nome={participante.nome} fotoUrl={participante.fotoUrl} className="flex size-5 items-center justify-center rounded-full text-[9px] font-bold text-white" />
              ))}
              </div>
            </div>
          )}
          {estaDentro && (
            <span className="mt-1 inline-flex w-fit rounded-full bg-primary/10 px-2 py-0.5 text-[0.65rem] font-medium text-primary">
              {textos.participando}
            </span>
          )}
          {!estaDentro && !ehResponsavel && !finalizado && (
            <p className="mt-1 truncate text-[0.65rem] text-muted-foreground">{textos.avisoEnviarAssume}</p>
          )}
        </div>
      </div>

      <BarraDeAcoesComTransbordo
        acoes={acoes}
        rotuloMenu={textos.maisAcoes ?? textos.outros}
        containerRef={cabecalhoRef}
        larguraReservada={LARGURA_MINIMA_DA_IDENTIFICACAO_PX}
      />

      <DialogoTransferir
        atendimentoId={conversa.atendimentoId}
        aberto={transferirAberto}
        onFechar={() => setTransferirAberto(false)}
      />
      <DialogoConvidar
        atendimentoId={conversa.atendimentoId}
        participantes={participantes}
        aberto={convidarAberto}
        onFechar={() => setConvidarAberto(false)}
        onSucesso={() => setFeedbackParticipacao({ tipo: "sucesso", texto: textos.convidarSucesso })}
      />
      <DialogoTagsDoLead
        leadId={conversa.leadId}
        aberto={tagsAberto}
        onFechar={() => setTagsAberto(false)}
      />
    </div>
  );
}

function formatarHorario(iso: string): string {
  const data = new Date(iso);
  if (Number.isNaN(data.getTime())) return "—";
  return new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(data);
}

function mensagemDeErroParticipacao(
  erro: unknown,
  textos: {
    erroSemPermissao: string;
    erroPedidoExpirado: string;
    erroParticipacaoNaoEncontrada: string;
    erroParticipacao: string;
  },
): string {
  const status = erro instanceof ErroDeApi ? erro.status : undefined;
  const detalhe = erro instanceof Error ? erro.message.toLocaleLowerCase("pt-BR") : "";
  if (status === 403 || detalhe.includes("permiss") || detalhe.includes("alçada")) return textos.erroSemPermissao;
  if (detalhe.includes("expirad")) return textos.erroPedidoExpirado;
  if (status === 404 || detalhe.includes("participa")) return textos.erroParticipacaoNaoEncontrada;
  return textos.erroParticipacao;
}
