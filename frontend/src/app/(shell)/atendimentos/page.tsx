"use client";

import { useState } from "react";

import { CabecalhoConversa } from "@/components/atendimentos/cabecalho-conversa";
import { Composer } from "@/components/atendimentos/composer";
import { ListaConversas } from "@/components/atendimentos/lista-conversas";
import { ListaMensagens } from "@/components/atendimentos/lista-mensagens";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useMensagens } from "@/lib/atendimento/use-mensagens";
import type { CartaoAtendimento, MensagemResposta } from "@/lib/atendimento/types";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

/**
 * Uma conexão de tempo real por sessão de tela (não por conversa) e um único atendimento aberto por
 * vez — trocar de conversa reassina o mesmo socket, nunca reconecta do zero (RNF-CRM-01: nada aqui
 * pode travar o caminho de mensagem).
 */
export default function PaginaAtendimentos() {
  const textosGerais = useTextos();
  const textos = textosGerais.atendimentos;
  const [conversa, setConversa] = useState<CartaoAtendimento | null>(null);
  const [avisoRevogacao, setAvisoRevogacao] = useState(false);

  const { conexao, estado } = useConexaoTempoReal(
    () => useAuthStore.getState().accessToken,
    (atendimentoRevogado) => {
      setConversa((atual) => {
        if (atual?.atendimentoId !== atendimentoRevogado) {
          return atual;
        }
        setAvisoRevogacao(true);
        return null;
      });
    },
  );

  const { data: mensagens, isLoading: carregandoMensagens } = useMensagens(
    conversa?.atendimentoId ?? null,
    conexao,
    estado,
  );
  const enviar = useEnviarMensagem();

  function selecionarConversa(cartao: CartaoAtendimento) {
    setAvisoRevogacao(false);
    setConversa(cartao);
  }

  function reenviar(mensagem: MensagemResposta) {
    if (!conversa || !mensagem.conteudo) {
      return;
    }
    enviar.mutate({
      atendimentoId: conversa.atendimentoId,
      leadId: conversa.leadId,
      conteudo: mensagem.conteudo,
    });
  }

  return (
    <div className="grid h-full grid-cols-[320px_1fr] overflow-hidden">
      <ListaConversas selecionadoId={conversa?.atendimentoId ?? null} onSelecionar={selecionarConversa} />

      <div className="flex h-full min-h-0 flex-col">
        {estado === "reconectando" && (
          <div className="bg-cor-atencao/10 px-3 py-1 text-center text-xs text-cor-atencao">
            {textos.tempoReal.reconectando}
          </div>
        )}
        {avisoRevogacao && (
          <div className="bg-destructive/10 px-3 py-1 text-center text-xs text-destructive">
            {textos.tempoReal.conversaEncerrada}
          </div>
        )}

        {conversa ? (
          <>
            <CabecalhoConversa conversa={conversa} />
            <ListaMensagens
              mensagens={mensagens ?? []}
              carregando={carregandoMensagens}
              onReenviar={reenviar}
            />
            <Composer conversa={conversa} />
          </>
        ) : (
          <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
            {textosGerais.estados.vazio}
          </div>
        )}
      </div>
    </div>
  );
}
