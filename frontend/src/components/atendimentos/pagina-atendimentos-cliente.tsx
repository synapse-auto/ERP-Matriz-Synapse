"use client";

import { useState } from "react";

import { CabecalhoConversa } from "@/components/atendimentos/cabecalho-conversa";
import { Composer } from "@/components/atendimentos/composer";
import { ListaConversas } from "@/components/atendimentos/lista-conversas";
import { ListaMensagens } from "@/components/atendimentos/lista-mensagens";
import { PainelLateralLead } from "@/components/leads/painel-lateral-lead";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import type { CartaoAtendimento, MensagemResposta, VisaoAtendimento } from "@/lib/atendimento/types";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useMensagens } from "@/lib/atendimento/use-mensagens";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

interface Props {
  leadInicialId: string | null;
  visaoInicial: VisaoAtendimento | null;
}

/**
 * Um clique consulta a ficha sem invalidar a lista; o duplo clique seleciona o atendimento e
 * reassina o socket existente (RN-CRM-05).
 */
export function PaginaAtendimentosCliente({ leadInicialId, visaoInicial }: Props) {
  const textosGerais = useTextos();
  const textos = textosGerais.atendimentos;
  const [conversa, setConversa] = useState<CartaoAtendimento | null>(null);
  const [leadNoPainel, setLeadNoPainel] = useState<string | null>(null);
  const [avisoRevogacao, setAvisoRevogacao] = useState(false);

  const { conexao, estado } = useConexaoTempoReal(
    () => useAuthStore.getState().accessToken,
    (atendimentoRevogado) => {
      setConversa((atual) => {
        if (atual?.atendimentoId !== atendimentoRevogado) {
          return atual;
        }
        setLeadNoPainel(null);
        setAvisoRevogacao(true);
        return null;
      });
    },
  );

  const mensagensQuery = useMensagens(
    conversa?.atendimentoId ?? null,
    conexao,
    estado,
  );
  const enviar = useEnviarMensagem();

  function abrirAtendimento(cartao: CartaoAtendimento) {
    setAvisoRevogacao(false);
    setLeadNoPainel(null);
    setConversa(cartao);
  }

  function reenviar(mensagem: MensagemResposta) {
    if (!conversa || !mensagem.conteudo) return;
    enviar.mutate({
      atendimentoId: conversa.atendimentoId,
      leadId: conversa.leadId,
      conteudo: mensagem.conteudo,
    });
  }

  return (
    <div className="grid h-full grid-cols-[320px_1fr] overflow-hidden">
      <ListaConversas
        selecionadoId={conversa?.atendimentoId ?? null}
        leadInicialId={leadInicialId}
        visaoInicial={visaoInicial}
        onAbrirPainel={(cartao) => setLeadNoPainel(cartao.leadId)}
        onAbrirAtendimento={abrirAtendimento}
      />

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
              mensagens={mensagensQuery.data}
              carregando={mensagensQuery.isLoading}
              onReenviar={reenviar}
              temMais={mensagensQuery.hasNextPage}
              carregandoMais={mensagensQuery.isFetchingNextPage}
              onCarregarMais={() => void mensagensQuery.fetchNextPage()}
            />
            <Composer conversa={conversa} />
          </>
        ) : (
          <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
            {textosGerais.estados.vazio}
          </div>
        )}
      </div>

      {leadNoPainel && (
        <PainelLateralLead leadId={leadNoPainel} onFechar={() => setLeadNoPainel(null)} />
      )}
    </div>
  );
}
