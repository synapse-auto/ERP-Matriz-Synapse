"use client";

import { useState } from "react";

import { useQueryClient } from "@tanstack/react-query";

import { CabecalhoConversa } from "@/components/atendimentos/cabecalho-conversa";
import { Composer } from "@/components/atendimentos/composer";
import { ListaConversas } from "@/components/atendimentos/lista-conversas";
import { ListaMensagens } from "@/components/atendimentos/lista-mensagens";
import { PainelDaConversa } from "@/components/atendimentos/painel-da-conversa";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import { marcarAtendimentoComoLido } from "@/lib/atendimento/api";
import type {
  CartaoAtendimento,
  MensagemResposta,
  VisaoAtendimento,
} from "@/lib/atendimento/types";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useMensagens } from "@/lib/atendimento/use-mensagens";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

interface Props {
  leadInicialId: string | null;
  visaoInicial: VisaoAtendimento | null;
}

/**
 * Um clique seleciona o atendimento e reassina o socket existente (RN-CRM-05). A ficha acompanha
 * a conversa no painel da direita, sem um overlay intermediário.
 */
export function PaginaAtendimentosCliente({
  leadInicialId,
  visaoInicial,
}: Props) {
  const textosGerais = useTextos();
  const textos = textosGerais.atendimentos;
  const cache = useQueryClient();
  const [conversa, setConversa] = useState<CartaoAtendimento | null>(null);
  const [buscaAberta, setBuscaAberta] = useState(false);
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

  const mensagensQuery = useMensagens(
    conversa?.atendimentoId ?? null,
    conexao,
    estado,
  );
  const enviar = useEnviarMensagem();

  function abrirAtendimento(cartao: CartaoAtendimento) {
    setAvisoRevogacao(false);
    setBuscaAberta(false);
    setConversa(cartao);
    void marcarAtendimentoComoLido(cartao.atendimentoId)
      .catch(() => {
        // Leitura e auxiliar: falhar nao pode impedir que o responsavel abra a conversa.
      })
      .finally(() => {
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      });
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
    <div
      className={
        conversa
          ? "grid h-full grid-cols-[346px_1fr_344px] overflow-hidden"
          : "grid h-full grid-cols-[346px_1fr] overflow-hidden"
      }
    >
      <ListaConversas
        selecionadoId={conversa?.atendimentoId ?? null}
        leadInicialId={leadInicialId}
        visaoInicial={visaoInicial}
        onAbrirAtendimento={abrirAtendimento}
      />

      <div className="flex h-full min-h-0 min-w-0 flex-col">
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
            <CabecalhoConversa
              conversa={conversa}
              buscaAberta={buscaAberta}
              onAlternarBusca={() => setBuscaAberta((aberta) => !aberta)}
            />
            <ListaMensagens
              mensagens={mensagensQuery.data}
              carregando={mensagensQuery.isLoading}
              onReenviar={reenviar}
              temMais={mensagensQuery.hasNextPage}
              carregandoMais={mensagensQuery.isFetchingNextPage}
              onCarregarMais={() => void mensagensQuery.fetchNextPage()}
              buscaAberta={buscaAberta}
              canalTipo={conversa.canalTipo}
              atendenteId={conversa.atendenteId}
              atendenteNome={conversa.atendenteNome}
            />
            <Composer conversa={conversa} />
          </>
        ) : (
          <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
            {textosGerais.estados.vazio}
          </div>
        )}
      </div>

      {conversa && (
        <PainelDaConversa
          leadId={conversa.leadId}
          responsavelNome={conversa.atendenteNome}
        />
      )}
    </div>
  );
}
