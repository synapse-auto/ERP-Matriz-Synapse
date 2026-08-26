"use client";

import { useCallback, useEffect, useState } from "react";

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
  NotificacaoTempoReal,
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
  const [leadSelecionadoId, setLeadSelecionadoId] = useState<string | null>(null);
  const [atendimentos, setAtendimentos] = useState<CartaoAtendimento[]>([]);
  const [leadParaAbrir, setLeadParaAbrir] = useState(leadInicialId);
  const [leadParaAbrirGatilho, setLeadParaAbrirGatilho] = useState(0);
  const [notificacao, setNotificacao] = useState<NotificacaoTempoReal | null>(null);
  const [buscaAberta, setBuscaAberta] = useState(false);
  const [avisoRevogacao, setAvisoRevogacao] = useState(false);

  const { conexao, estado } = useConexaoTempoReal(
    () => useAuthStore.getState().accessToken,
    (atendimentoRevogado) => {
      setLeadSelecionadoId((atual) => {
        const selecionado = atendimentos.find((item) => item.leadId === atual);
        const ativoId = selecionado?.atendimentoAtivoId
          ?? (selecionado?.status !== "FINALIZADO" ? selecionado?.atendimentoId : null);
        if (ativoId !== atendimentoRevogado) {
          return atual;
        }
        setAvisoRevogacao(true);
        return null;
      });
    },
    (evento) => {
      if (
        evento.tipo === "TRANSFERENCIA_RECEBIDA" ||
        evento.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA"
      )
        setNotificacao(evento);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  );

  useEffect(() => {
    if (estado === "conectado") {
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    }
  }, [cache, estado]);

  const conversa = atendimentos.find((atendimento) => atendimento.leadId === leadSelecionadoId) ?? null;
  /** Atendimento operacional do lead; histórico e cartão continuam ancorados no cartão mais recente. */
  const atendimentoAtivoId = conversa
    ? conversa.atendimentoAtivoId
      ?? (conversa.status !== "FINALIZADO" ? conversa.atendimentoId : null)
    : null;
  const atendimentoAtivo = atendimentoAtivoId
    ? { ...conversa!, atendimentoId: atendimentoAtivoId, status: "EM_ATENDIMENTO" as const }
    : null;
  const atendimentoAtivoIdParaLeitura = atendimentoAtivo?.atendimentoId ?? null;
  const marcarConversaAbertaComoLida = useCallback(() => {
    if (!atendimentoAtivoIdParaLeitura) return;
    void marcarAtendimentoComoLido(atendimentoAtivoIdParaLeitura)
      .catch(() => {
        // Leitura e auxiliar: falhar nao pode interromper o fluxo de mensagens.
      })
      .finally(() => {
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      });
  }, [atendimentoAtivoIdParaLeitura, cache]);
  const mensagensQuery = useMensagens(
    conversa?.atendimentoId ?? null,
    conexao,
    estado,
    marcarConversaAbertaComoLida,
    atendimentoAtivo?.atendimentoId ?? null,
  );
  const enviar = useEnviarMensagem();

  function abrirAtendimento(cartao: CartaoAtendimento) {
    setAvisoRevogacao(false);
    setBuscaAberta(false);
    setLeadSelecionadoId(cartao.leadId);
    const ativoId = cartao.atendimentoAtivoId
      ?? (cartao.status !== "FINALIZADO" ? cartao.atendimentoId : null);
    if (!ativoId) return;
    void marcarAtendimentoComoLido(ativoId)
      .catch(() => {
        // Leitura e auxiliar: falhar nao pode impedir que o responsavel abra a conversa.
      })
      .finally(() => {
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      });
  }

  function reenviar(mensagem: MensagemResposta) {
    if (!atendimentoAtivo || !mensagem.conteudo) return;
    enviar.mutate({
      atendimentoId: atendimentoAtivo.atendimentoId,
      leadId: atendimentoAtivo.leadId,
      conteudo: mensagem.conteudo,
    });
  }

  return (
    <div
      className={
        conversa
          ? "relative grid h-full grid-cols-[346px_1fr_344px] overflow-hidden"
          : "relative grid h-full grid-cols-[346px_1fr] overflow-hidden"
      }
    >
      {notificacao && (
        <div
          className="pointer-events-auto absolute right-4 top-4 z-30 w-80 rounded-xl border border-border bg-background p-4 shadow-lg"
          role="status"
        >
          <p className="font-semibold text-foreground">
            {notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA"
              ? textos.tempoReal.atendimentoDevolvidoParaIa
              : textos.tempoReal.transferenciaRecebida}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA"
              ? textos.tempoReal.atendimentoDevolvidoParaIaDescricao.replace(
                  "{nome}",
                  notificacao.dados.leadNome,
                )
              : notificacao.tipo === "TRANSFERENCIA_RECEBIDA"
                ? textos.tempoReal.transferenciaRecebidaDescricao.replace(
                    "{nome}",
                    notificacao.dados.leadNome,
                  )
                : null}
          </p>
          {notificacao.tipo === "TRANSFERENCIA_RECEBIDA" && (
            <button
              type="button"
              className="mt-3 text-sm font-medium text-primary underline-offset-4 hover:underline"
              onClick={() => {
                setLeadParaAbrir(notificacao.dados.leadId);
                setLeadParaAbrirGatilho((atual) => atual + 1);
                setNotificacao(null);
              }}
            >
              {textos.tempoReal.abrirTransferencia}
            </button>
          )}
        </div>
      )}
      <ListaConversas
        selecionadoId={conversa?.leadId ?? null}
        leadInicialId={leadParaAbrir}
        leadInicialGatilho={leadParaAbrirGatilho}
        visaoInicial={visaoInicial}
        onAtendimentosAtualizados={setAtendimentos}
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
              conversa={atendimentoAtivo ?? { ...conversa, status: "FINALIZADO" as const }}
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
            {atendimentoAtivo ? (
              <Composer conversa={atendimentoAtivo} />
            ) : (
              <div className="bg-background px-4 pb-4 pt-3">
                <div className="mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 text-center text-sm text-muted-foreground">
                  {textos.finalizar.sucesso}
                </div>
              </div>
            )}
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
