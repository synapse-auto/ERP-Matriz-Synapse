"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

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
import {
  ProvedorDeAberturaDeConversa,
  type AberturaDeConversaDoContato,
} from "@/lib/atendimento/abrir-conversa-do-contato";
import {
  atualizarPainelDeAtendimentos,
  pedidoDaNotificacao,
} from "@/lib/atendimento/atualizacao-do-painel";
import { atualizarReacoesDoChatInterno, substituirReacoesDoHistorico } from "@/lib/atendimento/reacoes-cache";
import {
  abrirAtendimentoParaLead,
  definirReacao,
  iniciarNovoContato,
  marcarAtendimentoComoLido,
  obterEstadoAtendimento,
  removerReacao,
} from "@/lib/atendimento/api";
import {
  ehFalhaTransitoria,
  mensagemDaFalhaDeAbertura,
  registrarDiagnosticoDeAbertura,
  statusHttpDoErro,
} from "@/lib/atendimento/abertura-atendimento";
import { TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
import { motivoDaFalhaDeMidia, type FalhaDeEnvioMidia } from "@/lib/atendimento/falhas-de-midia";
import { janelaTextoLivreAberta } from "@/lib/atendimento/janela-24h";
import { ReconciliadorEstadoAtendimento } from "@/lib/atendimento/reconciliar-estado-atendimento";
import type {
  EstadoAtendimentoSelecionado,
  EventoCanonicoAtendimentoTempoReal,
  ItemInbox,
  MensagemResposta,
  NotificacaoTempoReal,
  VisaoAtendimento,
} from "@/lib/atendimento/types";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useEnviarMidia } from "@/lib/atendimento/use-enviar-midia";
import { useConfiguracaoComposer } from "@/lib/atendimento/use-configuracao-composer";
import { useMensagens } from "@/lib/atendimento/use-mensagens";
import { invalidarParticipacao } from "@/lib/atendimento/use-participacao";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { apiFetch } from "@/lib/api/http-client";
import { listarContatosChat, abrirConversaDireta, criarGrupoChat } from "@/lib/chat-interno/api";
import { useConversaEmTelaCheia } from "@/lib/navegacao/conversa-em-tela-cheia";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn } from "@/lib/utils";

interface Props {
  leadInicialId: string | null;
  atendimentoInicialId: string | null;
  visaoInicial: VisaoAtendimento | null;
}

type NotificacaoDeAtendimento = Exclude<
  NotificacaoTempoReal,
  { tipo: "CHAT_INTERNO_MENSAGEM" } | { tipo: "CHAT_INTERNO_REACAO" } | { tipo: "CHAT_INTERNO_MENSAGEM_REMOVIDA" } | { tipo: "CHAT_INTERNO_MENSAGEM_EDITADA" }
>;

/**
 * O contrato ainda não possui um id de evento. A ocorrência, junto do recurso e do tipo, é a
 * identidade estável disponível: uma nova transição do mesmo atendimento continua distinta pelo
 * `ocorridoEm`, enquanto a repetição do mesmo frame após reconexão mantém a mesma chave.
 */
function chaveDaNotificacao(notificacao: NotificacaoDeAtendimento): string {
  const ocorridoEm = "ocorridoEm" in notificacao.dados
    ? notificacao.dados.ocorridoEm
    : notificacao.dados.enviadoEm;
  return [
    notificacao.tipo,
    notificacao.dados.atendimentoId,
    notificacao.dados.leadId,
    ocorridoEm,
  ].join(":");
}

/**
 * Um clique seleciona o atendimento e reassina o socket existente (RN-CRM-05). A ficha acompanha
 * a conversa no painel da direita, sem um overlay intermediário.
 */
export function PaginaAtendimentosCliente({
  leadInicialId,
  atendimentoInicialId,
  visaoInicial,
}: Props) {
  const textosGerais = useTextos();
  const textos = textosGerais.atendimentos;
  const cache = useQueryClient();
  const [atendimentoSelecionadoId, setAtendimentoSelecionadoId] = useState<string | null>(null);
  const [atendimentoParaAbrirId, setAtendimentoParaAbrirId] = useState(atendimentoInicialId);
  const [erroDeAbertura, setErroDeAbertura] = useState<string | null>(null);
  const [atendimentos, setAtendimentos] = useState<ItemInbox[]>([]);
  const [visaoAtendimento, setVisaoAtendimento] = useState<VisaoAtendimento | null>(null);
  const [conversaInternaId, setConversaInternaId] = useState<string | null>(null);
  const [leadParaAbrir, setLeadParaAbrir] = useState(leadInicialId);
  const [leadParaAbrirGatilho, setLeadParaAbrirGatilho] = useState(0);
  const [notificacao, setNotificacao] = useState<NotificacaoTempoReal | null>(null);
  const [falhasDeMidia, setFalhasDeMidia] = useState<FalhaDeEnvioMidia[]>([]);
  const notificacoesProcessadas = useRef(new Set<string>());
  // Enquanto o POST de leitura nao confirma, uma invalidacao ampla pode devolver o contador
  // anterior. O mapa mantem a barreira ate que todas as leituras iniciadas para cada lead terminem.
  const leiturasEmVoo = useRef(new Map<string, number>());
  const [reconciliador] = useState(() => new ReconciliadorEstadoAtendimento(cache));
  const [sincronizacaoLiberada, setSincronizacaoLiberada] = useState<{
    atendimentoId: string;
    ciclo: number;
  } | null>(null);
  const aberturaProcessada = useRef<string | null>(null);
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
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
  const criarGrupoInterno = useMutation({
    mutationFn: ({ nome, participantes }: { nome: string; participantes: string[] }) =>
      criarGrupoChat(nome, participantes),
    onSuccess: (resposta) => {
      setConversaInternaId(resposta.id);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
  });
  const [novoContatoAberto, setNovoContatoAberto] = useState(false);
  const [novoContatoInicial, setNovoContatoInicial] = useState<{ nome: string; telefone: string } | null>(null);
  const sessao = useAuthStore.getState();
  const marcarLeituraDaConversa = useCallback(
    (atendimentoId: string, leadId: string) => {
      const quantidadeAtual = leiturasEmVoo.current.get(leadId) ?? 0;
      leiturasEmVoo.current.set(leadId, quantidadeAtual + 1);
      // Cancela um GET amplo que possa ter começado antes do clique; seu retorno também pode ser
      // anterior à leitura e sobrescrever o zero otimista.
      void cache.cancelQueries({ queryKey: ["atendimentos"] });
      zerarNaoLidasDoLead(cache, leadId);
      void marcarAtendimentoComoLido(atendimentoId)
        .catch(() => {
          // Leitura e auxiliar: falhar nao pode impedir que a conversa seja aberta.
        })
        .finally(() => {
          const quantidadeRestante = (leiturasEmVoo.current.get(leadId) ?? 1) - 1;
          if (quantidadeRestante > 0) {
            leiturasEmVoo.current.set(leadId, quantidadeRestante);
          } else {
            leiturasEmVoo.current.delete(leadId);
          }
          if (leiturasEmVoo.current.size === 0) {
            atualizarPainelDeAtendimentos(cache, { atendimentoId });
          }
        });
    },
    [cache],
  );

  const selecionarAtendimento = useCallback(
    (cartao: ItemInbox, origem: "lista" | "rota" = "lista") => {
      if (cartao.tipo === "EQUIPE_INTERNA") {
        setConversaInternaId(cartao.conversaId);
        setAtendimentoSelecionadoId(null);
        setSincronizacaoLiberada(null);
        setAvisoRevogacao(false);
        setBuscaAberta(false);
        return;
      }
      const idParaAbrir = cartao.atendimentoAtivoId ?? cartao.atendimentoId;
      setAvisoRevogacao(false);
      setConversaInternaId(null);
      setBuscaAberta(false);
      setErroDeAbertura(null);
      setAtendimentoSelecionadoId(idParaAbrir);
      setSincronizacaoLiberada(null);
      marcarLeituraDaConversa(idParaAbrir, cartao.leadId);
      registrarDiagnosticoDeAbertura({
        origem,
        etapa: "cartao_resolvido",
        leadId: cartao.leadId,
        atendimentoId: idParaAbrir,
        usuarioId: sessao.usuarioId,
        papel: sessao.papel,
        visao: visaoAtendimento,
        cartaoSelecionadoId: idParaAbrir,
      });
    },
    [marcarLeituraDaConversa, sessao.papel, sessao.usuarioId, visaoAtendimento],
  );

  /**
   * Depois de iniciar/reativar: o lead cai em Ativos. Sem trocar a visão, a lista atual
   * (Pendentes/Potenciais/Finalizados) não contém o cartão e o chat nunca abre.
   */
  const focarAtendimentoIniciado = useCallback(
    (resposta: { leadId: string; atendimentoId: string }) => {
      setAvisoRevogacao(false);
      setConversaInternaId(null);
      setVisaoAtendimento("ATIVOS");
      setAtendimentoSelecionadoId(null);
      setSincronizacaoLiberada(null);
      setErroDeAbertura(null);
      aberturaProcessada.current = null;
      setAtendimentoParaAbrirId(resposta.atendimentoId);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
    [cache],
  );

  const iniciarContato = useMutation({
    // Uma chave nasce por clique no diálogo e acompanha qualquer repetição desse POST.
    mutationFn: (pedido: Parameters<typeof iniciarNovoContato>[0]) =>
      iniciarNovoContato(pedido, crypto.randomUUID()),
    onSuccess: (resposta) => {
      setNovoContatoAberto(false);
      focarAtendimentoIniciado(resposta);
    },
  });
  // E211: o card de contato compartilhado abre o cartão que o backend já autorizou pela mesma seleção
  // da lista, ou só abre o diálogo de novo contato preenchido — confirmar continua com o usuário.
  const aberturaDeConversaDoContato = useMemo<AberturaDeConversaDoContato>(() => ({
    abrirCartao: (cartao) => selecionarAtendimento(cartao),
    iniciarNovoContato: (dados) => {
      iniciarContato.reset();
      setNovoContatoInicial(dados);
      setNovoContatoAberto(true);
    },
  }), [iniciarContato, selecionarAtendimento]);
  const abrirNovoAtendimento = useMutation({
    mutationFn: abrirAtendimentoParaLead,
    onSuccess: (resposta) => {
      focarAtendimentoIniciado(resposta);
    },
  });

  useEffect(() => {
    let cancelado = false;
    queueMicrotask(() => {
      if (!cancelado) setAtendimentoParaAbrirId(atendimentoInicialId);
    });
    return () => {
      cancelado = true;
    };
  }, [atendimentoInicialId]);

  const cartaoDaAbertura = useQuery({
    queryKey: ["atendimentos", "estado", atendimentoParaAbrirId],
    queryFn: () => obterEstadoAtendimento(atendimentoParaAbrirId as string),
    enabled: atendimentoParaAbrirId != null,
    retry: false,
  });

  useEffect(() => {
    if (
      !atendimentoParaAbrirId
      || (!cartaoDaAbertura.data && !cartaoDaAbertura.isError)
      || aberturaProcessada.current === atendimentoParaAbrirId
    ) return;
    aberturaProcessada.current = atendimentoParaAbrirId;
    let cancelado = false;
    queueMicrotask(() => {
      if (cancelado) return;
      if (cartaoDaAbertura.data) {
        reconciliador.registrarSnapshot(cartaoDaAbertura.data);
        const cartaoNaLista = atendimentos.some(
          (item) =>
            item.tipo !== "EQUIPE_INTERNA"
            && (item.atendimentoId === atendimentoParaAbrirId
              || item.atendimentoAtivoId === atendimentoParaAbrirId),
        );
        selecionarAtendimento(cartaoDaAbertura.data.cartao, "rota");
        registrarDiagnosticoDeAbertura({
          origem: "rota",
          etapa: "confirmada",
          leadId: cartaoDaAbertura.data.cartao.leadId,
          atendimentoId: atendimentoParaAbrirId,
          usuarioId: sessao.usuarioId,
          papel: sessao.papel,
          visao: visaoAtendimento,
          cartaoNaLista,
          cartaoSelecionadoId: atendimentoParaAbrirId,
          httpStatus: 200,
        });
        setAtendimentoParaAbrirId(null);
        return;
      }
      setErroDeAbertura(mensagemDaFalhaDeAbertura(cartaoDaAbertura.error, textos.abertura));
      registrarDiagnosticoDeAbertura({
        origem: "rota",
        etapa: "falhou",
        atendimentoId: atendimentoParaAbrirId,
        usuarioId: sessao.usuarioId,
        papel: sessao.papel,
        visao: visaoAtendimento,
        httpStatus: statusHttpDoErro(cartaoDaAbertura.error),
      });
      setAtendimentoParaAbrirId(null);
    });
    return () => {
      cancelado = true;
    };
  }, [
    atendimentoParaAbrirId,
    atendimentos,
    cartaoDaAbertura.data,
    cartaoDaAbertura.error,
    cartaoDaAbertura.isError,
    reconciliador,
    selecionarAtendimento,
    sessao.papel,
    sessao.usuarioId,
    textos.abertura,
    visaoAtendimento,
  ]);

  // Notificações são efêmeras: o evento continua persistido no backend, mas o aviso de trabalho
  // não pode ocupar a tela indefinidamente. O timer é apenas apresentação (não regra de negócio)
  // e é cancelado quando chega um evento novo ou quando a tela desmonta.
  useEffect(() => {
    if (!notificacao) return;
    const segundos = configuracao?.tempoNotificacaoSegundos ?? 8;
    const timer = window.setTimeout(() => setNotificacao(null), segundos * 1000);
    return () => window.clearTimeout(timer);
  }, [notificacao, configuracao?.tempoNotificacaoSegundos]);

  const estadoSelecionadoQuery = useQuery({
    queryKey: ["atendimentos", "estado", atendimentoSelecionadoId],
    queryFn: () => obterEstadoAtendimento(atendimentoSelecionadoId as string),
    enabled: atendimentoSelecionadoId != null,
    retry: false,
  });

  useEffect(() => {
    if (estadoSelecionadoQuery.data) {
      reconciliador.registrarSnapshot(estadoSelecionadoQuery.data);
    }
  }, [estadoSelecionadoQuery.data, reconciliador]);

  const indisponibilizarAtendimento = useCallback(
    (atendimentoId: string) => {
      if (atendimentoSelecionadoId !== atendimentoId) return;
      setAvisoRevogacao(true);
      setSincronizacaoLiberada(null);
      setAtendimentoSelecionadoId(null);
    },
    [atendimentoSelecionadoId],
  );

  const processarEventoCanonico = useCallback(
    (evento: EventoCanonicoAtendimentoTempoReal) => {
      const selecionado = evento.dados.atendimentoId === atendimentoSelecionadoId;
      if (!selecionado) {
        reconciliador.registrarSemSnapshot(evento);
        return;
      }
      const cicloJaSincronizado = sincronizacaoLiberada?.ciclo;
      if (reconciliador.deveReconciliar(evento)) setSincronizacaoLiberada(null);
      invalidarParticipacao(evento.dados.atendimentoId);
      void reconciliador.receber(evento)
        .then((snapshot) => {
          if (snapshot?.cartao.atendimentoId === atendimentoSelecionadoId) {
            setSincronizacaoLiberada({
              atendimentoId: snapshot.cartao.atendimentoId,
              ciclo: cicloJaSincronizado ?? -1,
            });
          }
        })
        .catch((erro) => {
          if (statusHttpDoErro(erro) === 404) {
            indisponibilizarAtendimento(evento.dados.atendimentoId);
          }
        });
    },
    [
      atendimentoSelecionadoId,
      indisponibilizarAtendimento,
      reconciliador,
      sincronizacaoLiberada?.ciclo,
    ],
  );

  const { conexao, estado, ciclo } = useConexaoTempoReal(
    () => useAuthStore.getState().accessToken,
    (atendimentoRevogado) => {
      registrarDiagnosticoDeAbertura({
        origem: "rota",
        etapa: "evento_websocket",
        atendimentoId: atendimentoRevogado,
        usuarioId: sessao.usuarioId,
        papel: sessao.papel,
        visao: visaoAtendimento,
        cartaoSelecionadoId: atendimentoSelecionadoId,
        evento: "REVOGACAO",
      });
      indisponibilizarAtendimento(atendimentoRevogado);
      // Proteção de visibilidade nunca espera a janela de coalescência (E209).
      atualizarPainelDeAtendimentos(cache, { atendimentoId: atendimentoRevogado, urgente: true });
    },
    (evento) => {
      registrarDiagnosticoDeAbertura({
        origem: "rota",
        etapa: "evento_websocket",
        leadId: "leadId" in evento.dados ? evento.dados.leadId : null,
        atendimentoId: "atendimentoId" in evento.dados ? evento.dados.atendimentoId : null,
        usuarioId: sessao.usuarioId,
        papel: sessao.papel,
        visao: visaoAtendimento,
        cartaoSelecionadoId: atendimentoSelecionadoId,
        evento: evento.tipo,
      });
      if (evento.tipo === "ATENDIMENTO_ESTADO") {
        processarEventoCanonico(evento);
        return;
      }
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
      // Uma leitura iniciada localmente tem precedencia sobre o GET amplo: aguarde o POST de
      // leitura terminar para nao reintroduzir no cache um contador anterior ao que o usuario viu.
      if (evento.tipo !== "CHAT_INTERNO_REACAO" && leiturasEmVoo.current.size === 0) {
        // E209: mesmo pedido (mesma chave) que o ouvinte global gera para este evento — os dois
        // viram um refetch só, e uma rajada vira no máximo um por janela.
        atualizarPainelDeAtendimentos(cache, pedidoDaNotificacao(evento));
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

  // Governa só a aplicação de frames incrementais (snapshot antes de incremental, a cada ciclo de
  // conexão). Nunca a permissão de envio: essa vem do snapshot REST, que independe do WebSocket.
  const incrementaisLiberados = estado === "conectado"
    && sincronizacaoLiberada?.atendimentoId === atendimentoSelecionadoId
    && sincronizacaoLiberada.ciclo === ciclo;

  useEffect(() => {
    if (estado !== "conectado" || !atendimentoSelecionadoId) return;
    let cancelado = false;
    const cicloDaSincronizacao = ciclo;
    void reconciliador.sincronizar(atendimentoSelecionadoId)
      .then((snapshot) => {
        if (!cancelado && snapshot.cartao.atendimentoId === atendimentoSelecionadoId) {
          setSincronizacaoLiberada({
            atendimentoId: snapshot.cartao.atendimentoId,
            ciclo: cicloDaSincronizacao,
          });
        }
      })
      .catch((erro) => {
        if (!cancelado && statusHttpDoErro(erro) === 404) {
          indisponibilizarAtendimento(atendimentoSelecionadoId);
        }
      });
    return () => {
      cancelado = true;
    };
  }, [atendimentoSelecionadoId, ciclo, estado, indisponibilizarAtendimento, reconciliador]);

  useEffect(() => {
    if (!atendimentoSelecionadoId
      || !estadoSelecionadoQuery.isError
      || statusHttpDoErro(estadoSelecionadoQuery.error) !== 404) return;
    let cancelado = false;
    queueMicrotask(() => {
      if (!cancelado) indisponibilizarAtendimento(atendimentoSelecionadoId);
    });
    return () => {
      cancelado = true;
    };
  }, [
    atendimentoSelecionadoId,
    estadoSelecionadoQuery.error,
    estadoSelecionadoQuery.isError,
    indisponibilizarAtendimento,
  ]);

  const estadoSelecionado = estadoSelecionadoQuery.data?.cartao.atendimentoId
    === atendimentoSelecionadoId
    ? estadoSelecionadoQuery.data
    : null;
  const conversa = estadoSelecionado?.cartao ?? null;
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
  // O id selecionado nunca e trocado implicitamente por outro ciclo do mesmo lead.
  const atendimentoAtivo = estadoSelecionado?.podeEnviar ? conversa : null;
  const atendimentoParaLeitura = conversa?.atendimentoId ?? null;
  const marcarConversaAbertaComoLida = useCallback(() => {
    if (!atendimentoParaLeitura || !conversa) return;
    marcarLeituraDaConversa(atendimentoParaLeitura, conversa.leadId);
  }, [atendimentoParaLeitura, conversa, marcarLeituraDaConversa]);
  const mensagensQuery = useMensagens(
    conversa?.atendimentoId ?? null,
    conexao,
    estado,
    marcarConversaAbertaComoLida,
    conversa?.atendimentoId ?? null,
    processarEventoCanonico,
    (evento) => {
      registrarDiagnosticoDeAbertura({
        origem: "rota",
        etapa: "evento_websocket",
        leadId: evento.tipo === "REACAO" ? null : evento.dados.leadId,
        atendimentoId: evento.dados.atendimentoId,
        usuarioId: sessao.usuarioId,
        papel: sessao.papel,
        visao: visaoAtendimento,
        cartaoSelecionadoId: atendimentoSelecionadoId,
        evento: evento.tipo,
      });
      if (evento.tipo === "RESUMO_IA_STATUS") {
        void cache.invalidateQueries({ queryKey: ["lead", evento.dados.leadId] });
        void cache.invalidateQueries({ queryKey: ["resumo-ia", evento.dados.atendimentoId] });
        return;
      }
    },
    incrementaisLiberados,
  );
  const enviar = useEnviarMensagem();
  const reenviarMidia = useEnviarMidia();
  const aposMensagemEnviada = useCallback(() => {
    // PR #71: só PENDENTES → ATIVOS após envio bem-sucedido. FINALIZADOS (e as demais
    // visões) permanecem — o usuário não é expulso da lista de finalizados por um envio.
    if (visaoAtendimento === "PENDENTES") {
      setVisaoAtendimento("ATIVOS");
    }
  }, [visaoAtendimento]);
  const aposAtendimentoFinalizado = useCallback((resumo: { id: string }) => {
    setSincronizacaoLiberada(null);
    void reconciliador.sincronizar(resumo.id)
      .catch((erro) => {
        if (statusHttpDoErro(erro) === 404) indisponibilizarAtendimento(resumo.id);
      });
  }, [indisponibilizarAtendimento, reconciliador]);

  /**
   * Rede de segurança no instante do envio: rebusca o estado canônico e recusa ciclo finalizado ou
   * substituído. Falha transitória do `/estado` não trava o atendente — vale o último snapshot
   * aceito, e o backend continua recusando envio para atendimento que não está aberto.
   */
  const revalidarEnvio = useCallback(async (): Promise<boolean> => {
    const atendimentoId = atendimentoSelecionadoId;
    if (!atendimentoId) return false;
    try {
      const snapshot = await reconciliador.sincronizar(atendimentoId);
      const permitido = snapshot.cartao.atendimentoId === atendimentoId && snapshot.podeEnviar;
      setSincronizacaoLiberada(permitido ? { atendimentoId, ciclo } : null);
      return permitido;
    } catch (erro) {
      if (ehFalhaTransitoria(erro)) {
        return reconciliador.ultimoSnapshot(atendimentoId)?.podeEnviar ?? false;
      }
      setSincronizacaoLiberada(null);
      if (statusHttpDoErro(erro) === 404) indisponibilizarAtendimento(atendimentoId);
      return false;
    }
  }, [atendimentoSelecionadoId, ciclo, indisponibilizarAtendimento, reconciliador]);

  const registrarFalhasDeMidia = useCallback((falhas: FalhaDeEnvioMidia[]) => {
    setFalhasDeMidia((atuais) => [...atuais, ...falhas]);
  }, []);

  const reenviarFalhasDeMidia = useCallback(async () => {
    if (!await revalidarEnvio()) return;
    const atuais = falhasDeMidia;
    const idsEmReenvio = new Set(atuais.map((falha) => falha.id));
    const restantes: FalhaDeEnvioMidia[] = [];
    for (const falha of atuais) {
      try {
        await reenviarMidia.mutateAsync({
          atendimentoId: falha.atendimentoId,
          leadId: falha.leadId,
          arquivo: falha.arquivo,
          legenda: falha.legenda,
          resposta: falha.resposta,
          citacao: falha.citacao,
          idempotencyKey: falha.idempotencyKey,
        });
      } catch (erro) {
        restantes.push({
          ...falha,
          motivo: motivoDaFalhaDeMidia(erro, textos.composer.anexoErro),
        });
      }
    }
    setFalhasDeMidia((correntes) => [
      ...correntes.filter((falha) => !idsEmReenvio.has(falha.id)),
      ...restantes,
    ]);
  }, [falhasDeMidia, revalidarEnvio, reenviarMidia, textos.composer.anexoErro]);
  const atualizarAtendimentos = useCallback((cartoes: ItemInbox[]) => {
    setAtendimentos(cartoes);
  }, []);

  async function reenviar(mensagem: MensagemResposta) {
    if (!atendimentoAtivo || !mensagem.conteudo) return;
    if (!await revalidarEnvio()) return;
    enviar.mutate(
      {
        atendimentoId: atendimentoAtivo.atendimentoId,
        leadId: atendimentoAtivo.leadId,
        conteudo: mensagem.conteudo,
        idempotencyKey: mensagem.idempotencyKey ?? undefined,
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
    <ProvedorDeAberturaDeConversa valor={aberturaDeConversaDoContato}>
    <div
      className={`relative grid h-full min-h-0 flex-1 ${colunasDoPainel} grid-rows-[minmax(0,1fr)] overflow-hidden`}
    >
      {(falhasDeMidia.length > 0 || notificacao || erroDeAbertura) && (
        <div className="pointer-events-none absolute right-4 top-4 z-30 flex w-80 max-w-[calc(100%-2rem)] flex-col gap-2">
          {erroDeAbertura && (
            <div className="pointer-events-auto relative rounded-xl border border-destructive/30 bg-background p-4 shadow-lg" role="alert">
              <button
                type="button"
                className="absolute right-2 top-2 rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                aria-label={textos.tempoReal.fechar}
                onClick={() => setErroDeAbertura(null)}
              >
                <X className="size-(--tamanho-icone-interface)" aria-hidden />
              </button>
              <p className="pr-6 text-sm text-foreground">{erroDeAbertura}</p>
            </div>
          )}
          {falhasDeMidia.length > 0 && (
            <div className="pointer-events-auto relative rounded-xl border border-destructive/30 bg-background p-4 shadow-lg" role="alert">
              <button
                type="button"
                className="absolute right-2 top-2 rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                aria-label={textos.tempoReal.fechar}
                onClick={() => setFalhasDeMidia([])}
              >
                <X className="size-(--tamanho-icone-interface)" aria-hidden />
              </button>
              <p className="pr-6 font-semibold text-foreground">
                {textos.composer.anexoFalhasTitulo}
              </p>
              <ul className="mt-2 space-y-1 text-sm text-muted-foreground">
                {falhasDeMidia.map((falha) => (
                  <li key={falha.id} className="break-words">
                    {textos.composer.anexoFalhaItem
                      .replace("{nome}", falha.arquivo.name)
                      .replace("{motivo}", falha.motivo)}
                  </li>
                ))}
              </ul>
              <button
                type="button"
                className="mt-3 text-sm font-medium text-primary underline-offset-4 hover:underline disabled:cursor-not-allowed disabled:opacity-60"
                onClick={() => void reenviarFalhasDeMidia()}
                disabled={reenviarMidia.isPending}
              >
                {textos.composer.anexoReenviarFalhas}
              </button>
            </div>
          )}
          {notificacao && (
            <div
              className="pointer-events-auto relative rounded-xl border border-border bg-background p-4 shadow-lg"
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
            {notificacao.tipo === "CONVITE_ATENDIMENTO"
              ? textos.tempoReal.conviteRecebido
              : notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA"
              ? textos.tempoReal.atendimentoDevolvidoParaIa
              : textos.tempoReal.transferenciaRecebida}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {notificacao.tipo === "CONVITE_ATENDIMENTO"
              ? textos.tempoReal.conviteRecebidoDescricao
              : notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA"
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
          {(notificacao.tipo === "TRANSFERENCIA_RECEBIDA" || notificacao.tipo === "CONVITE_ATENDIMENTO") && (
            <button
              type="button"
              className="mt-3 text-sm font-medium text-primary underline-offset-4 hover:underline"
              onClick={() => {
                if (notificacao.tipo === "CONVITE_ATENDIMENTO") {
                  setAtendimentoParaAbrirId(notificacao.dados.atendimentoId);
                } else {
                  setLeadParaAbrir(notificacao.dados.leadId);
                  setLeadParaAbrirGatilho((atual) => atual + 1);
                }
                setNotificacao(null);
              }}
            >
              {notificacao.tipo === "CONVITE_ATENDIMENTO"
                ? textos.tempoReal.abrirConvite
                : textos.tempoReal.abrirTransferencia}
            </button>
          )}
            </div>
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
        onAbrirAtendimento={selecionarAtendimento}
        chatInternoHabilitado={chatInternoHabilitado}
        contatosInternos={contatosInternos.data ?? []}
        contatosInternosCarregando={contatosInternos.isLoading || contatosInternos.isFetching}
        contatosInternosErro={contatosInternos.isError}
        onRecarregarContatos={() => void contatosInternos.refetch()}
        onCriarConversaInterna={(usuarioId) => abrirConversaInterna.mutateAsync(usuarioId)}
        onCriarGrupoInterno={(nome, participantes) => criarGrupoInterno.mutateAsync({ nome, participantes })}
        onNovoContato={() => {
          iniciarContato.reset();
          setNovoContatoInicial(null);
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
              conversa={conversa}
              estado={estadoSelecionado as EstadoAtendimentoSelecionado}
              onReconciliarEstado={() => revalidarEnvio().then(() => undefined)}
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
                      setAtendimentoSelecionadoId(null);
                      setSincronizacaoLiberada(null);
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
                atendimentoId={conversa.atendimentoId}
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
                  onFalhasDeMidia={registrarFalhasDeMidia}
                  podeEnviar={Boolean(estadoSelecionado?.podeEnviar)}
                  onRevalidarEnvio={revalidarEnvio}
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
            atendimentoId={conversa.atendimentoId}
            responsavelNome={conversa.atendenteNome}
            onRetrair={() => setPainelDetalhesAberto(false)}
          />
        </div>
      )}

      <DialogoNovoContato
        aberto={novoContatoAberto}
        onFechar={() => setNovoContatoAberto(false)}
        onConfirmar={(pedido) => iniciarContato.mutate(pedido)}
        valoresIniciais={novoContatoInicial}
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
    </ProvedorDeAberturaDeConversa>
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
