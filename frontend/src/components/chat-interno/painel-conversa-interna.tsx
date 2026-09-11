"use client";

import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { ZonaSoltarArquivos } from "@/components/atendimentos/zona-soltar-arquivos";
import { TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
import { listarConversasChat, listarMensagensChat, enviarMensagemChat, enviarMidiaChat, marcarChatComoLido, definirReacaoChat, removerReacaoChat, responderMensagemChat, encaminharMensagemChat, excluirMensagemChat } from "@/lib/chat-interno/api";
import { atualizarReacoesDoChatInterno, substituirReacoesDoChatInterno } from "@/lib/atendimento/reacoes-cache";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import { definirConversaAtiva } from "@/lib/atendimento/servico-notificacoes-tempo-real";

import { CabecalhoChatInterno, ComposerChatInterno, DialogoEncaminharChatInterno, ListaMensagensChatInterno, type ComposerChatHandle } from "./componentes-chat-interno";

export function PainelConversaInterna({ conversaId }: { conversaId: string }) {
  const catalogo = useTextos();
  const textos = catalogo.chatInterno;
  const cache = useQueryClient();
  const composerRef = useRef<ComposerChatHandle>(null);
  const [respostaAlvo, setRespostaAlvo] = useState<import("@/lib/chat-interno/types").ChatMensagem | null>(null);
  const [encaminharAlvo, setEncaminharAlvo] = useState<import("@/lib/chat-interno/types").ChatMensagem | null>(null);
  const conversas = useQuery({ queryKey: ["chat-interno", "conversas"], queryFn: listarConversasChat });
  const mensagens = useQuery({ queryKey: ["chat-interno", "mensagens", conversaId], queryFn: () => listarMensagensChat(conversaId) });
  const usuarioAtual = useAuthStore((estado) => estado.usuarioId);
  const conversa = conversas.data?.find((item) => item.id === conversaId);
  const enviar = useMutation({
    mutationFn: (conteudo: string) => enviarMensagemChat(conversaId, conteudo),
    onSuccess: () => { void cache.invalidateQueries({ queryKey: ["chat-interno"] }); },
  });
  const enviarMidia = useMutation({
    mutationFn: ({ arquivo, legenda }: { arquivo: File; legenda?: string }) => enviarMidiaChat(conversaId, arquivo, legenda),
    onSuccess: () => { void cache.invalidateQueries({ queryKey: ["chat-interno"] }); },
  });
  const responder = useMutation({
    mutationFn: ({ mensagemId, conteudo }: { mensagemId: string; conteudo: string }) => responderMensagemChat(conversaId, mensagemId, conteudo),
    onSuccess: () => { setRespostaAlvo(null); void cache.invalidateQueries({ queryKey: ["chat-interno"] }); },
  });
  const excluir = useMutation({
    mutationFn: (mensagemId: string) => excluirMensagemChat(conversaId, mensagemId),
    onSuccess: () => { void cache.invalidateQueries({ queryKey: ["chat-interno"] }); },
  });
  const encaminhar = useMutation({
    mutationFn: ({ mensagemId, destinoId }: { mensagemId: string; destinoId: string }) => encaminharMensagemChat(conversaId, mensagemId, destinoId),
    onSuccess: () => { setEncaminharAlvo(null); void cache.invalidateQueries({ queryKey: ["chat-interno"] }); },
  });
  const atualizar = useCallback(() => {
    void cache.invalidateQueries({ queryKey: ["chat-interno"] });
  }, [cache]);
  useConexaoTempoReal(() => useAuthStore.getState().accessToken, undefined, (evento) => {
    if ((evento.tipo === "CHAT_INTERNO_MENSAGEM" || evento.tipo === "CHAT_INTERNO_MENSAGEM_REMOVIDA") && evento.dados.conversaId === conversaId) {
      atualizar();
      if (evento.tipo === "CHAT_INTERNO_MENSAGEM") void marcarChatComoLido(conversaId);
    }
    if (evento.tipo === "CHAT_INTERNO_REACAO" && evento.dados.conversaId === conversaId) {
      atualizarReacoesDoChatInterno(cache, conversaId, evento.dados.mensagemId, evento.dados.reacoes, { atorId: evento.dados.atorId, emojiDoAtor: evento.dados.emojiDoAtor }, useAuthStore.getState().usuarioId);
    }
  });
  useEffect(() => { void marcarChatComoLido(conversaId).catch(() => undefined); }, [conversaId]);
  useEffect(() => {
    definirConversaAtiva({ origem: "CHAT_INTERNO", id: conversaId });
    return () => definirConversaAtiva(null);
  }, [conversaId]);
  async function definirReacaoDaMensagem(mensagem: { id: string }, emoji: string) {
    const resposta = await definirReacaoChat(conversaId, mensagem.id, emoji);
    substituirReacoesDoChatInterno(cache, conversaId, mensagem.id, resposta.reacoes ?? []);
  }
  async function removerReacaoDaMensagem(mensagem: { id: string }) {
    const resposta = await removerReacaoChat(conversaId, mensagem.id);
    substituirReacoesDoChatInterno(cache, conversaId, mensagem.id, resposta.reacoes ?? []);
  }
  async function enviarConteudo(conteudo: string) {
    if (respostaAlvo) {
      await responder.mutateAsync({ mensagemId: respostaAlvo.id, conteudo });
    } else {
      await enviar.mutateAsync(conteudo);
    }
  }
  if (conversas.isError || mensagens.isError) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => { void conversas.refetch(); void mensagens.refetch(); }} />;
  }
  return (
    <Fragment>
      <div className="flex h-full min-h-0 flex-1 flex-col">
      <CabecalhoChatInterno conversa={conversa} textos={textos} />
      <ZonaSoltarArquivos
        accept={TIPOS_DE_ANEXO_ACEITOS}
        disabled={enviar.isPending || enviarMidia.isPending}
        rotulo={catalogo.atendimentos.composer.anexoSoltar}
        onArquivos={({ aceitos, rejeitados }) =>
          composerRef.current?.adicionarArquivos([...aceitos, ...rejeitados])
        }
      >
        {mensagens.isLoading ? <p className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.carregando}</p> : <ListaMensagensChatInterno mensagens={mensagens.data?.mensagens ?? []} usuarioAtual={usuarioAtual} textos={textos} onDefinirReacao={definirReacaoDaMensagem} onRemoverReacao={removerReacaoDaMensagem} onResponder={setRespostaAlvo} onEncaminhar={setEncaminharAlvo} onExcluir={async (mensagem) => { await excluir.mutateAsync(mensagem.id); }} />}
        <ComposerChatInterno ref={composerRef} textos={textos} resposta={respostaAlvo} onCancelarResposta={() => setRespostaAlvo(null)} enviando={enviar.isPending || enviarMidia.isPending || responder.isPending} erro={enviar.isError || enviarMidia.isError || responder.isError} onEnviar={enviarConteudo} onEnviarMidia={(arquivo, legenda) => enviarMidia.mutateAsync({ arquivo, legenda })} />
      </ZonaSoltarArquivos>
      </div>
      <DialogoEncaminharChatInterno aberto={Boolean(encaminharAlvo)} conversaOrigemId={conversaId} conversas={conversas.data ?? []} textos={textos} enviando={encaminhar.isPending} erro={encaminhar.isError} onFechar={() => setEncaminharAlvo(null)} onConfirmar={(destinoId) => encaminhar.mutateAsync({ mensagemId: encaminharAlvo!.id, destinoId })} />
    </Fragment>
  );
}
