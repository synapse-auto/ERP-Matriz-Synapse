"use client";

import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { listarConversasChat, listarMensagensChat, enviarMensagemChat, enviarMidiaChat, marcarChatComoLido, definirReacaoChat, removerReacaoChat } from "@/lib/chat-interno/api";
import { substituirReacoesDoChatInterno } from "@/lib/atendimento/reacoes-cache";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";

import { CabecalhoChatInterno, ComposerChatInterno, ListaMensagensChatInterno } from "./componentes-chat-interno";

export function PainelConversaInterna({ conversaId }: { conversaId: string }) {
  const textos = useTextos().chatInterno;
  const cache = useQueryClient();
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
  useEffect(() => { void marcarChatComoLido(conversaId).catch(() => undefined); }, [conversaId]);
  async function definirReacaoDaMensagem(mensagem: { id: string }, emoji: string) {
    const resposta = await definirReacaoChat(conversaId, mensagem.id, emoji);
    substituirReacoesDoChatInterno(cache, conversaId, mensagem.id, resposta.reacoes ?? []);
  }
  async function removerReacaoDaMensagem(mensagem: { id: string }) {
    const resposta = await removerReacaoChat(conversaId, mensagem.id);
    substituirReacoesDoChatInterno(cache, conversaId, mensagem.id, resposta.reacoes ?? []);
  }
  if (conversas.isError || mensagens.isError) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => { void conversas.refetch(); void mensagens.refetch(); }} />;
  }
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <CabecalhoChatInterno conversa={conversa} textos={textos} />
      {mensagens.isLoading ? <p className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.carregando}</p> : <ListaMensagensChatInterno mensagens={mensagens.data?.mensagens ?? []} usuarioAtual={usuarioAtual} textos={textos} onDefinirReacao={definirReacaoDaMensagem} onRemoverReacao={removerReacaoDaMensagem} />}
      <ComposerChatInterno textos={textos} enviando={enviar.isPending || enviarMidia.isPending} erro={enviar.isError || enviarMidia.isError} onEnviar={(conteudo) => enviar.mutateAsync(conteudo)} onEnviarMidia={(arquivo, legenda) => enviarMidia.mutateAsync({ arquivo, legenda })} />
    </div>
  );
}
