"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { ArrowLeft, X } from "lucide-react";

import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import { CabecalhoConversa } from "@/components/atendimentos/cabecalho-conversa";
import { Composer, type ComposerHandle } from "@/components/atendimentos/composer";
import { DialogoEncaminhar } from "@/components/atendimentos/dialogo-encaminhar";
import { DialogoNovoContato } from "@/components/atendimentos/dialogo-novo-contato";
import { ListaConversas } from "@/components/atendimentos/lista-conversas";
import { ListaMensagens } from "@/components/atendimentos/lista-mensagens";
import { PainelDaConversa } from "@/components/atendimentos/painel-da-conversa";
import { ZonaSoltarArquivos } from "@/components/atendimentos/zona-soltar-arquivos";
import { PainelConversaInterna } from "@/components/chat-interno/painel-conversa-interna";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import { atualizarReacoesDoChatInterno, substituirReacoesDoHistorico } from "@/lib/atendimento/reacoes-cache";
import { abrirAtendimentoParaLead, definirReacao, iniciarNovoContato, marcarAtendimentoComoLido, removerReacao } from "@/lib/atendimento/api";
import { TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
import { janelaTextoLivreAberta } from "@/lib/atendimento/janela-24h";
import type {
  CartaoAtendimento,
  ItemInbox,
  MensagemResposta,
  NotificacaoTempoReal,
  VisaoAtendimento,
} from "@/lib/atendimento/types";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useConfiguracaoComposer } from "@/lib/atendimento/use-configuracao-composer";
import { useMensagens } from "@/lib/atendimento/use-mensagens";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { apiFetch } from "@/lib/api/http-client";
import { listarContatosChat, abrirConversaDireta } from "@/lib/chat-interno/api";
import { useConversaEmTelaCheia } from "@/lib/navegacao/conversa-em-tela-cheia";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn } from "@/lib/utils";

interface Props {
  leadInicialId: string | null;
  visaoInicial: VisaoAtendimento | null;
}

type NotificacaoDeAtendimento = Exclude<
  NotificacaoTempoReal,
  { tipo: "CHAT_INTERNO_MENSAGEM" } | { tipo: "CHAT_INTERNO_REACAO" }
>;

/**
 * O contrato ainda não possui um id de evento. A ocorrência, junto do recurso e do tipo, é a
 * identidade estável disponível: uma nova transição do mesmo atendimento continua distinta pelo
 * `ocorridoEm`, enquanto a repetição do mesmo frame após reconexão mantém a mesma chave.
 */
function chaveDaNotificacao(notificacao: NotificacaoDeAtendimento): string {
  return [
    notificacao.tipo,
    notificacao.dados.atendimentoId,
    notificacao.dados.leadId,
    notificacao.dados.ocorridoEm,
  ].join(":");
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
  const [atendimentos, setAtendimentos] = useState<ItemInbox[]>([]);
  const [cartaoSelecionado, setCartaoSelecionado] = useState<CartaoAtendimento | null>(null);
  const [visaoAtendimento, setVisaoAtendimento] = useState<VisaoAtendimento | null>(null);
  const [conversaInternaId, setConversaInternaId] = useState<string | null>(null);
  const [leadParaAbrir, setLeadParaAbrir] = useState(leadInicialId);
  const [leadParaAbrirGatilho, setLeadParaAbrirGatilho] = useState(0);
  const [notificacao, setNotificacao] = useState<NotificacaoTempoReal | null>(null);
  const notificacoesProcessadas = useRef(new Set<string>());
  const composerRef = useRef<ComposerHandle>(null);
  const [buscaAberta, setBuscaAberta] = useState(false);
  const [painelDetalhesAberto, setPainelDetalhesAberto] = useState<boolean | null>(null);
  const [respostaAlvo, setRespostaAlvo] = useState<{
    leadId: string;
    mensagem: MensagemResposta;
  } | null>(null);
  const [encaminharAlvo, setEncaminharAlvo] = useState<{
    leadId: string;
    mensagem: MensagemResposta;
  } | null>(null);
  const [avisoRevogacao, setAvisoRevogacao] = useState(false);
  const telaEstreita = useTelaEstreita();
  const { definir: definirConversaEmTelaCheia } = useConversaEmTelaCheia();
  const { data: configuracao } = useConfiguracaoComposer();
  const { data: flags } = useQuery({ queryKey: ["config", "features"], queryFn: () => apiFetch<string[]>("/api/v1/config/features") });
  const chatInternoHabilitado = flags?.includes("chat_interno") ?? false;
  const contatosInternos = useQuery({ queryKey: ["chat-interno", "contatos"], queryFn: listarContatosChat, enabled: chatInternoHabilitado });
  const abrirConversaInterna = useMutation({
    mutationFn: abrirConversaDireta,
    onSuccess: (resposta) => {
      setConversaInternaId(resposta.id);
      setLeadSelecionadoId(null);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
  const criarGrupoInterno = useMutation({
    mutationFn: ({ nome, participantes }: { nome: string; participantes: string[] }) => criarGrupoChat(nome, participantes),
    onSuccess: (resposta) => {
      setConversaInternaId(resposta.id);
      setLeadSelecionadoId(null);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
  const [novoContatoAberto, setNovoContatoAberto] = useState(false);

  /**
   * Depois de iniciar/reativar: o lead cai em Ativos. Sem trocar a visão, a lista atual
   * (Pendentes/Potenciais/Finalizados) não contém o cartão e o chat nunca abre.
   */
  const focarAtendimentoIniciado = useCallback(
    (leadId: string) => {
      setAvisoRevogacao(false);
      setConversaInternaId(null);
      setVisaoAtendimento("ATIVOS");
      setLeadSelecionadoId(leadId);
      setLeadParaAbrir(leadId);
      setLeadParaAbrirGatilho((atual) => atual + 1);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
    [cache],
  );

  const iniciarContato = useMutation({
    mutationFn: iniciarNovoContato,
    onSuccess: (resposta) => {
      setNovoContatoAberto(false);
      focarAtendimentoIniciado(resposta.leadId);
    },
  });
  const abrirNovoAtendimento = useMutation({
    mutationFn: abrirAtendimentoParaLead,
    onSuccess: (resposta) => {
      focarAtendimentoIniciado(resposta.leadId);
    },
  });

  // Notificações são efêmeras: o evento continua persistido no backend, mas o aviso de trabalho
  // não pode ocupar a tela indefinidamente. O timer é apenas apresentação (não regra de negócio)
  // e é cancelado quando chega um evento novo ou quando a tela desmonta.
  useEffect(() => {
    if (!notificacao) return;
    const segundos = configuracao?.tempoNotificacaoSegundos ?? 8;
    const timer = window.setTimeout(() => setNotificacao(null), segundos * 1000);
    return () => window.clearTimeout(timer);
  }, [notificacao, configuracao?.tempoNotificacaoSegundos]);

  const { conexao, estado } = useConexaoTempoReal(
    () => useAuthStore.getState().accessToken,
    (atendimentoRevogado) => {
      setLeadSelecionadoId((atual) => {
        const selecionado = atendimentos.find((item) => item.tipo !== "EQUIPE_INTERNA" && item.leadId === atual) as CartaoAtendimento | undefined;
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
      ) {
        const chave = chaveDaNotificacao(evento);
        if (!notificacoesProcessadas.current.has(chave)) {
          notificacoesProcessadas.current.add(chave);
          setNotificacao(evento);
        }
      }
      if (evento.tipo !== "CHAT_INTERNO_REACAO") {
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      }
      if (evento.tipo === "CHAT_INTERNO_MENSAGEM") {
        void cache.invalidateQueries({ queryKey: ["chat-interno", "mensagens", evento.dados.conversaId] });
      }
      if (evento.tipo === "CHAT_INTERNO_REACAO") {
        atualizarReacoesDoChatInterno(
          cache,
          evento.dados.conversaId,
          evento.dados.mensagemId,
          evento.dados.reacoes,
          { atorId: evento.dados.atorId, emojiDoAtor: evento.dados.emojiDoAtor },
          useAuthStore.getState().usuarioId,
        );
      }
    },
  );

  useEffect(() => {
    if (estado === "conectado") {
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    }
  }, [cache, estado]);

  const conversaDaLista = atendimentos.find(
    (atendimento) => atendimento.tipo !== "EQUIPE_INTERNA" && atendimento.leadId === leadSelecionadoId,
  ) as CartaoAtendimento | undefined;
  const snapshotFinalizado = cartaoSelecionado?.leadId === leadSelecionadoId
    && cartaoSelecionado.status === "FINALIZADO"
    && cartaoSelecionado.atendimentoAtivoId === null
    ? cartaoSelecionado
    : null;
  const conversa = snapshotFinalizado ?? conversaDaLista
    ?? (cartaoSelecionado?.leadId === leadSelecionadoId ? cartaoSelecionado : null);
  const conversaAberta = Boolean(conversa || conversaInternaId);
  const respostaDaTela =
    conversa && respostaAlvo?.leadId === conversa.leadId ? respostaAlvo.mensagem : null;
  const encaminharDaTela =
    conversa && encaminharAlvo?.leadId === conversa.leadId ? encaminharAlvo.mensagem : null;
  const painelVisivel = Boolean(conversa) && (painelDetalhesAberto ?? !telaEstreita);
  useEffect(() => {
    definirConversaEmTelaCheia(telaEstreita && conversaAberta);
    return () => definirConversaEmTelaCheia(false);
  }, [conversaAberta, definirConversaEmTelaCheia, telaEstreita]);
  /** Atendimento operacional do lead; histórico e cartão continuam ancorados no cartão mais recente. */
  const atendimentoAtivoId = conversa
    ? conversa.atendimentoAtivoId
      ?? (conversa.status !== "FINALIZADO" ? conversa.atendimentoId : null)
    : null;
  const atendimentoAtivo = atendimentoAtivoId
    ? { ...conversa!, atendimentoId: atendimentoAtivoId, status: "EM_ATENDIMENTO" as const }
    : null;
  const atendimentoParaLeitura =
    atendimentoAtivo?.atendimentoId ?? conversa?.atendimentoId ?? null;
  const marcarConversaAbertaComoLida = useCallback(() => {
    if (!atendimentoParaLeitura || !conversa) return;
    zerarNaoLidasDoLead(cache, conversa.leadId);
    void marcarAtendimentoComoLido(atendimentoParaLeitura)
      .catch(() => {
        // Leitura e auxiliar: falhar nao pode interromper o fluxo de mensagens.
      })
      .finally(() => {
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      });
  }, [atendimentoParaLeitura, cache, conversa]);
  const mensagensQuery = useMensagens(
    conversa?.atendimentoId ?? null,
    conexao,
    estado,
    marcarConversaAbertaComoLida,
    atendimentoAtivo?.atendimentoId ?? null,
  );
  const enviar = useEnviarMensagem();
  const aposMensagemEnviada = useCallback(() => {
    // PR #71: só PENDENTES → ATIVOS após envio bem-sucedido. FINALIZADOS (e as demais
    // visões) permanecem — o usuário não é expulso da lista de finalizados por um envio.
    if (visaoAtendimento === "PENDENTES") {
      setVisaoAtendimento("ATIVOS");
    }
  }, [visaoAtendimento]);
  const aposAtendimentoFinalizado = useCallback(() => {
    setCartaoSelecionado((atual) =>
      atual
        ? { ...atual, status: "FINALIZADO", atendimentoAtivoId: null }
        : null,
    );
  }, []);
  const atualizarAtendimentos = useCallback((cartoes: ItemInbox[]) => {
    setAtendimentos(cartoes);
    setCartaoSelecionado((atual) => {
      if (!atual) return null;
      const atualizado = cartoes.find(
        (item) => item.tipo !== "EQUIPE_INTERNA" && item.leadId === atual.leadId,
      ) as CartaoAtendimento | undefined;
      return atualizado ?? atual;
    });
  }, []);

  function abrirAtendimento(cartao: ItemInbox) {
    if (cartao.tipo === "EQUIPE_INTERNA") {
      setConversaInternaId(cartao.conversaId);
      setLeadSelecionadoId(null);
      setCartaoSelecionado(null);
      setAvisoRevogacao(false);
      setBuscaAberta(false);
      return;
    }
    setAvisoRevogacao(false);
    setConversaInternaId(null);
    setBuscaAberta(false);
    setLeadSelecionadoId(cartao.leadId);
    setCartaoSelecionado(cartao);
    const idParaLeitura = cartao.atendimentoAtivoId ?? cartao.atendimentoId;
    zerarNaoLidasDoLead(cache, cartao.leadId);
    if (!idParaLeitura) return;
    void marcarAtendimentoComoLido(idParaLeitura)
      .catch(() => {
        // Leitura e auxiliar: falhar nao pode impedir que o responsavel abra a conversa.
      })
      .finally(() => {
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      });
  }

  function reenviar(mensagem: MensagemResposta) {
    if (!atendimentoAtivo || !mensagem.conteudo) return;
    enviar.mutate(
      {
        atendimentoId: atendimentoAtivo.atendimentoId,
        leadId: atendimentoAtivo.leadId,
        conteudo: mensagem.conteudo,
      },
      { onSuccess: aposMensagemEnviada },
    );
  }

  const historicoId = conversa?.atendimentoId ?? null;

  async function definirReacaoDaMensagem(mensagem: MensagemResposta, emoji: string) {
    const atendimentoId = mensagem.atendimentoId ?? historicoId;
    if (!atendimentoId || !historicoId) return;
    const resposta = await definirReacao(atendimentoId, mensagem.id, mensagem.enviadoEm, emoji);
    substituirReacoesDoHistorico(cache, ["mensagens", historicoId], mensagem.id, resposta.reacoes);
  }

  async function removerReacaoDaMensagem(mensagem: MensagemResposta) {
    const atendimentoId = mensagem.atendimentoId ?? historicoId;
    if (!atendimentoId || !historicoId) return;
    const resposta = await removerReacao(atendimentoId, mensagem.id, mensagem.enviadoEm);
    substituirReacoesDoHistorico(cache, ["mensagens", historicoId], mensagem.id, resposta.reacoes);
  }

  const colunasDoPainel = telaEstreita
    ? "grid-cols-1"
    : conversa && painelVisivel
      ? "grid-cols-[346px_minmax(0,1fr)_344px]"
      : "grid-cols-[346px_minmax(0,1fr)]";

  return (
    <div
      className={`relative grid h-full min-h-0 flex-1 ${colunasDoPainel} grid-rows-[minmax(0,1fr)] overflow-hidden`}
    >
      {notificacao && (
        <div
          className="pointer-events-auto absolute right-4 top-4 z-30 w-80 rounded-xl border border-border bg-background p-4 shadow-lg"
          role="status"
        >
          <button
            type="button"
            className="absolute right-2 top-2 rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label={textos.tempoReal.fechar}
            onClick={() => setNotificacao(null)}
          >
            <X className="size-(--tamanho-icone-interface)" aria-hidden />
          </button>
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
        selecionadoId={conversa?.leadId ?? conversaInternaId}
        leadInicialId={leadParaAbrir}
        leadInicialGatilho={leadParaAbrirGatilho}
        visaoInicial={visaoInicial}
        visaoAtual={visaoAtendimento ?? undefined}
        onVisaoAlterada={setVisaoAtendimento}
        onAtendimentosAtualizados={atualizarAtendimentos}
        onAbrirAtendimento={abrirAtendimento}
        chatInternoHabilitado={chatInternoHabilitado}
        contatosInternos={contatosInternos.data ?? []}
        contatosInternosCarregando={contatosInternos.isLoading || contatosInternos.isFetching}
        contatosInternosErro={contatosInternos.isError}
        onRecarregarContatos={() => void contatosInternos.refetch()}
        onCriarConversaInterna={(usuarioId) => abrirConversaInterna.mutateAsync(usuarioId)}
        onCriarGrupoInterno={(nome, participantes) => criarGrupoInterno.mutateAsync({ nome, participantes })}
        onNovoContato={() => {
          iniciarContato.reset();
          setNovoContatoAberto(true);
        }}
        className={cn(telaEstreita && conversaAberta && "hidden")}
      />

      <div className={cn("flex h-full min-h-0 min-w-0 flex-col overflow-hidden", telaEstreita && !conversaAberta && "hidden")}>
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

        {conversaInternaId ? (
          <>
            {telaEstreita && (
              <div className="flex h-12 shrink-0 items-center border-b border-border px-2">
                <button
                  type="button"
                  className="rounded-md p-2 text-foreground hover:bg-muted"
                  aria-label={textos.cabecalho.voltar}
                  onClick={() => setConversaInternaId(null)}
                >
                  <ArrowLeft className="size-[calc(var(--tamanho-icone-interface)*1.25)]" aria-hidden />
                </button>
              </div>
            )}
            <PainelConversaInterna conversaId={conversaInternaId} />
          </>
        ) : conversa ? (
          <>
            <CabecalhoConversa
              conversa={atendimentoAtivo ?? { ...conversa, status: "FINALIZADO" as const }}
              buscaAberta={buscaAberta}
              onAlternarBusca={() => setBuscaAberta((aberta) => !aberta)}
              painelDetalhesAberto={painelVisivel}
              onAlternarPainelDetalhes={() =>
                setPainelDetalhesAberto(!(painelDetalhesAberto ?? !telaEstreita))
              }
              onAbrirNovoAtendimento={
                atendimentoAtivo
                  ? undefined
                  : () => abrirNovoAtendimento.mutate(conversa.leadId)
              }
              abrindoNovoAtendimento={abrirNovoAtendimento.isPending}
              onAtendimentoFinalizado={aposAtendimentoFinalizado}
              onVoltar={
                telaEstreita
                  ? () => {
                      setLeadSelecionadoId(null);
                      setCartaoSelecionado(null);
                      setConversaInternaId(null);
                    }
                  : undefined
              }
            />
            <ZonaSoltarArquivos
              accept={TIPOS_DE_ANEXO_ACEITOS}
              disabled={
                !atendimentoAtivo
                || !janelaTextoLivreAberta(conversa.ultimaMensagemDoLeadEm)
              }
              rotulo={textos.composer.anexoSoltar}
              onArquivos={({ aceitos, rejeitados }) =>
                composerRef.current?.adicionarArquivos([...aceitos, ...rejeitados])
              }
            >
              <ListaMensagens
                mensagens={mensagensQuery.data}
                carregando={mensagensQuery.isLoading}
                onReenviar={reenviar}
                onDefinirReacao={definirReacaoDaMensagem}
                onRemoverReacao={removerReacaoDaMensagem}
                temMais={mensagensQuery.hasNextPage}
                carregandoMais={mensagensQuery.isFetchingNextPage}
                onCarregarMais={() => void mensagensQuery.fetchNextPage()}
                buscaAberta={buscaAberta}
                canalTipo={conversa.canalTipo}
                atendenteId={conversa.atendenteId}
                atendenteNome={conversa.atendenteNome}
                onResponder={(mensagem) =>
                  setRespostaAlvo({ leadId: conversa.leadId, mensagem })
                }
                onEncaminhar={(mensagem) =>
                  setEncaminharAlvo({ leadId: conversa.leadId, mensagem })
                }
                leadId={conversa.leadId}
                janelaTextoLivreAberta={janelaTextoLivreAberta(
                  conversa.ultimaMensagemDoLeadEm,
                )}
              />
              {atendimentoAtivo ? (
                <Composer
                  ref={composerRef}
                  conversa={atendimentoAtivo}
                  resposta={respostaDaTela}
                  onCancelarResposta={() => setRespostaAlvo(null)}
                  onMensagemEnviada={aposMensagemEnviada}
                />
              ) : (
                <div className="shrink-0 bg-background px-4 pb-4 pt-3">
                  <div className="mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 text-center text-sm text-muted-foreground">
                    {textos.finalizar.sucesso}
                  </div>
                </div>
              )}
            </ZonaSoltarArquivos>
          </>
        ) : (
          <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
            {textosGerais.estados.vazio}
          </div>
        )}
      </div>

      {conversa && painelVisivel && (
        <div className={cn("h-full min-h-0 overflow-hidden", telaEstreita && "absolute inset-0 z-20 bg-background")}>
          <PainelDaConversa
            leadId={conversa.leadId}
            responsavelNome={conversa.atendenteNome}
            onRetrair={() => setPainelDetalhesAberto(false)}
          />
        </div>
      )}

      <DialogoNovoContato
        aberto={novoContatoAberto}
        onFechar={() => setNovoContatoAberto(false)}
        onConfirmar={(pedido) => iniciarContato.mutate(pedido)}
        pendente={iniciarContato.isPending}
        erro={
          iniciarContato.isError
            ? iniciarContato.error instanceof Error
              ? iniciarContato.error.message
              : textos.novoContato.erro
            : null
        }
      />
      {conversa && encaminharDaTela && (
        <DialogoEncaminhar
          origemAtendimentoId={encaminharDaTela.atendimentoId ?? conversa.atendimentoId}
          origemLeadId={conversa.leadId}
          mensagem={encaminharDaTela}
          aberto
          onFechar={() => setEncaminharAlvo(null)}
        />
      )}
    </div>
  );
}

function zerarNaoLidasDoLead(cache: QueryClient, leadId: string) {
  cache.setQueriesData({ queryKey: ["atendimentos"] }, (atual: unknown) => {
    if (!atual || typeof atual !== "object") return atual;
    if (Array.isArray(atual)) {
      return atual.map((item) =>
        item && typeof item === "object" && "leadId" in item && item.leadId === leadId
          ? { ...item, naoLidas: 0 }
          : item,
      );
    }
    if ("pages" in atual && Array.isArray((atual as { pages: unknown }).pages)) {
      const inf = atual as { pages: { itens?: ItemInbox[] }[] };
      return {
        ...inf,
        pages: inf.pages.map((pagina) => ({
          ...pagina,
          itens: (pagina.itens ?? []).map((item) =>
            item.tipo !== "EQUIPE_INTERNA" && item.leadId === leadId
              ? { ...item, naoLidas: 0 }
              : item,
          ),
        })),
      };
    }
    return atual;
  });
}
