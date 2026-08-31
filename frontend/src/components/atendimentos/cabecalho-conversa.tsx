"use client";

import { useState } from "react";
import {
  ArrowLeft,
  ArrowLeftRight,
  CheckCheck,
  MessageCircleMore,
  MessageCirclePlus,
  PanelRightOpen,
  Phone,
  Search,
  Star,
} from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button, buttonVariants } from "@/components/ui/button";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useFinalizarAtendimento } from "@/lib/atendimento/use-transferir-finalizar";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { useLead } from "@/lib/lead/use-painel-lead";
import { useParticipantes } from "@/lib/atendimento/use-participantes";
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
import { DialogoAvaliacao } from "./dialogo-avaliacao";
import { AtalhoTags } from "./atalho-tags";

type Props = {
  conversa: CartaoAtendimento;
  onAlternarBusca: () => void;
  buscaAberta: boolean;
  painelDetalhesAberto: boolean;
  onAlternarPainelDetalhes: () => void;
  onVoltar?: () => void;
  onAbrirNovoAtendimento?: () => void;
  abrindoNovoAtendimento?: boolean;
};

/** Identificação da conversa, tags persistidas e ações operacionais. */
export function CabecalhoConversa({
  conversa,
  onAlternarBusca,
  buscaAberta,
  painelDetalhesAberto,
  onAlternarPainelDetalhes,
  onVoltar,
  onAbrirNovoAtendimento,
  abrindoNovoAtendimento = false,
}: Props) {
  const catalogo = useTextos();
  const textos = {
    ...catalogo.atendimentos.cabecalho,
    pedirEntrada: catalogo.atendimentos.cabecalho.pedirEntrada ?? catalogo.atendimentos.cabecalho.transferir,
    pedidoPendente: catalogo.atendimentos.cabecalho.pedidoPendente ?? catalogo.atendimentos.cabecalho.transferir,
    entrar: catalogo.atendimentos.cabecalho.entrar ?? catalogo.atendimentos.cabecalho.transferir,
    sair: catalogo.atendimentos.cabecalho.sair ?? catalogo.atendimentos.cabecalho.finalizar,
    recusado: catalogo.atendimentos.cabecalho.recusado ?? catalogo.atendimentos.cabecalho.transferir,
    aprovarEntrada: catalogo.atendimentos.cabecalho.aprovarEntrada ?? catalogo.atendimentos.cabecalho.finalizar,
    recusarEntrada: catalogo.atendimentos.cabecalho.recusarEntrada ?? catalogo.atendimentos.cabecalho.transferir,
    voltar: catalogo.atendimentos.cabecalho.voltar,
  };
  const [transferirAberto, setTransferirAberto] = useState(false);
  const [avaliacaoAberta, setAvaliacaoAberta] = useState(false);
  const finalizar = useFinalizarAtendimento();
  const token = useAuthStore((estado) => estado.accessToken);
  const papel = useAuthStore((estado) => estado.papel);
  const lead = useLead(conversa.leadId);
  const participantes = useParticipantes(conversa.atendimentoId);
  const meuPedido = useMeuPedido(conversa.atendimentoId);
  const pedidosPendentes = usePedidosPendentes(conversa.atendimentoId);
  const [estadoLocal, setEstadoLocal] = useState<"SEM_PEDIDO" | "PENDENTE" | "DENTRO" | "RECUSADO">("SEM_PEDIDO");
  const [processandoParticipacao, setProcessandoParticipacao] = useState(false);
  const finalizado = conversa.status === "FINALIZADO";
  const telefone = lead.data?.telefone ?? null;
  const nomeDoLead = lead.data?.nome ?? conversa.leadNome;
  const canal =
    conversa.canalTipo === "WHATSAPP"
      ? catalogo.atendimentos.canais.whatsapp
      : conversa.canalTipo;

  const usuarioId = idDoToken(token);
  const estaDentro = participantes.data.some((participante) => participante.usuarioId === usuarioId);
  const estadoPersistido = estaDentro
    ? "DENTRO"
    : meuPedido?.status === "PENDENTE"
      ? "PENDENTE"
      : meuPedido?.status === "RECUSADO"
        ? "RECUSADO"
        : estadoLocal;
  const podeEntrarDireto = papel !== "ATENDENTE" && !estaDentro;

  async function executarParticipacao(acao: () => Promise<unknown>, proximo: "SEM_PEDIDO" | "PENDENTE" | "DENTRO" | "RECUSADO") {
    setProcessandoParticipacao(true);
    try {
      await acao();
      setEstadoLocal(proximo);
      invalidarParticipacao(conversa.atendimentoId);
      await participantes.recarregar();
    } finally { setProcessandoParticipacao(false); }
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

  return (
    <div
      className="flex h-[72px] shrink-0 items-center justify-between border-b border-border bg-background px-5"
      data-slot="cabecalho-conversa"
    >
      <div className="flex min-w-0 items-center gap-3">
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
            <p className="truncate font-bold text-foreground">
              {nomeDoLead}
            </p>
            {canal && (
              <span className="inline-flex items-center gap-1 rounded-md bg-cor-sucesso/10 px-2 py-0.5 text-[0.7rem] font-semibold text-cor-sucesso">
                <MessageCircleMore className="size-[calc(var(--tamanho-icone-interface)*0.75)]" aria-hidden />
                {canal}
              </span>
            )}
          </div>
          <p className="truncate text-xs text-muted-foreground">{subtitulo}</p>
          {participantes.data && participantes.data.length > 0 && (
            <div className="mt-1 flex items-center gap-1" aria-label={textos.atendidoPor}>
              {participantes.data.map((participante) => (
                <AvatarIniciais key={participante.usuarioId} id={participante.usuarioId} nome={participante.nome} fotoUrl={participante.fotoUrl} className="flex size-5 items-center justify-center rounded-full text-[9px] font-bold text-white" />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {!finalizado && estadoPersistido === "SEM_PEDIDO" && (
          <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => podeEntrarDireto ? entrarAtendimento(conversa.atendimentoId) : pedirEntrada(conversa.atendimentoId), podeEntrarDireto ? "DENTRO" : "PENDENTE")} disabled={processandoParticipacao}>
            {podeEntrarDireto ? textos.entrar : textos.pedirEntrada}
          </Button>
        )}
        {!finalizado && estadoPersistido === "PENDENTE" && <Button type="button" variant="outline" size="sm" disabled>{textos.pedidoPendente}</Button>}
        {!finalizado && estadoPersistido === "RECUSADO" && (
          <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => pedirEntrada(conversa.atendimentoId), "PENDENTE")} disabled={processandoParticipacao}>{textos.recusado}</Button>
        )}
        {!finalizado && estadoPersistido === "DENTRO" && (
          <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => sairAtendimento(conversa.atendimentoId), "SEM_PEDIDO")} disabled={processandoParticipacao}>{textos.sair}</Button>
        )}
        {!finalizado && pedidosPendentes.length > 0 && conversa.atendenteId === usuarioId && pedidosPendentes.map((pedido) => (
          <span key={pedido.id} className="flex items-center gap-1">
            <Button type="button" variant="outline" size="sm" onClick={() => executarParticipacao(() => aprovarPedido(pedido.id), "SEM_PEDIDO")} disabled={processandoParticipacao}>{textos.aprovarEntrada}</Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => executarParticipacao(() => recusarPedido(pedido.id), "SEM_PEDIDO")} disabled={processandoParticipacao}>{textos.recusarEntrada}</Button>
          </span>
        ))}
        {!finalizado && (
          <>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setTransferirAberto(true)}
            >
              <ArrowLeftRight className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
              {textos.transferir}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="border-cor-sucesso/25 bg-cor-sucesso/10 text-cor-sucesso hover:bg-cor-sucesso/15 hover:text-cor-sucesso"
              onClick={() =>
                finalizar.mutate(conversa.atendimentoId, {
                  onSuccess: () => {
                    if (conversa.atendenteId) setAvaliacaoAberta(true);
                  },
                })
              }
              disabled={finalizar.isPending}
            >
              <CheckCheck className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
              {textos.finalizar}
            </Button>
          </>
        )}
        {finalizado && conversa.atendenteId && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setAvaliacaoAberta(true)}
          >
            <Star className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
            {catalogo.atendimentos.avaliacao.registrar}
          </Button>
        )}
        {finalizado && onAbrirNovoAtendimento && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onAbrirNovoAtendimento}
            disabled={abrindoNovoAtendimento}
          >
            <MessageCirclePlus className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
            {textos.novoAtendimento}
          </Button>
        )}
        <span className="mx-1 h-5 w-px bg-border" aria-hidden />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={textos.buscar}
          aria-pressed={buscaAberta}
          onClick={onAlternarBusca}
        >
          <Search className="size-(--tamanho-icone-interface)" aria-hidden />
        </Button>
        <AtalhoTags leadId={conversa.leadId} />
        {telefone && (
          <a
            href={`tel:${telefone.replace(/[^+\d]/g, "")}`}
            aria-label={`${catalogo.painelLead.dados.telefone}: ${telefone}`}
            className={cn(buttonVariants({ variant: "ghost", size: "icon" }))}
          >
            <Phone className="size-(--tamanho-icone-interface)" aria-hidden />
          </a>
        )}
        {!painelDetalhesAberto && (
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
            <PanelRightOpen className="size-(--tamanho-icone-interface)" aria-hidden />
          </Button>
        )}
      </div>

      <DialogoTransferir
        atendimentoId={conversa.atendimentoId}
        aberto={transferirAberto}
        onFechar={() => setTransferirAberto(false)}
      />
      <DialogoAvaliacao
        atendimentoId={conversa.atendimentoId}
        aberto={avaliacaoAberta}
        onFechar={() => setAvaliacaoAberta(false)}
      />
    </div>
  );
}

function idDoToken(token: string | null): string | null {
  if (!token) return null;
  try {
    const parte = token.split(".")[1];
    return JSON.parse(atob(parte.replace(/-/g, "+").replace(/_/g, "/"))).sub ?? null;
  } catch { return null; }
}
