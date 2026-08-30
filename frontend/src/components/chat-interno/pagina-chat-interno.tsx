"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { MessageCircle, Plus } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import { listarContatosChat, listarConversasChat, listarMensagensChat, abrirConversaDireta, enviarMensagemChat, enviarMidiaChat, marcarChatComoLido, definirReacaoChat, removerReacaoChat } from "@/lib/chat-interno/api";
import { atualizarReacoesDoChatInterno, substituirReacoesDoChatInterno } from "@/lib/atendimento/reacoes-cache";
import { TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
import { ZonaSoltarArquivos } from "@/components/atendimentos/zona-soltar-arquivos";
import { CabecalhoChatInterno, ComposerChatInterno, ListaMensagensChatInterno, type ComposerChatHandle } from "./componentes-chat-interno";

export function PaginaChatInterno() {
  const catalogo = useTextos();
  const textos = catalogo.chatInterno;
  const usuarioAtual = useAuthStore((s) => s.usuarioId);
  const cache = useQueryClient();
  const composerRef = useRef<ComposerChatHandle>(null);
  const conversas = useQuery({ queryKey: ["chat-interno", "conversas"], queryFn: listarConversasChat });
  const contatos = useQuery({ queryKey: ["chat-interno", "contatos"], queryFn: listarContatosChat });
  const [conversaId, setConversaId] = useState<string | null>(null);
  const [contatoId, setContatoId] = useState("");
  const mensagens = useQuery({
    queryKey: ["chat-interno", "mensagens", conversaId],
    queryFn: () => listarMensagensChat(conversaId!),
    enabled: Boolean(conversaId),
  });
  const atualizar = useCallback(() => {
    void cache.invalidateQueries({ queryKey: ["chat-interno"] });
  }, [cache]);
  useConexaoTempoReal(() => useAuthStore.getState().accessToken, undefined, (evento) => {
    if (evento.tipo === "CHAT_INTERNO_MENSAGEM") atualizar();
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
  });
  useEffect(() => { if (conversaId) { void marcarChatComoLido(conversaId); } }, [conversaId]);
  const abrir = useMutation({ mutationFn: abrirConversaDireta, onSuccess: (r) => { setConversaId(r.id); setContatoId(""); atualizar(); } });
  const enviar = useMutation({ mutationFn: ({ id, conteudo }: { id: string; conteudo: string }) => enviarMensagemChat(id, conteudo), onSuccess: atualizar });
  const enviarMidia = useMutation({ mutationFn: ({ id, arquivo, legenda }: { id: string; arquivo: File; legenda?: string }) => enviarMidiaChat(id, arquivo, legenda), onSuccess: atualizar });
  async function definirReacaoDaMensagem(mensagem: { id: string }, emoji: string) {
    if (!conversaId) return;
    const resposta = await definirReacaoChat(conversaId, mensagem.id, emoji);
    substituirReacoesDoChatInterno(cache, conversaId, mensagem.id, resposta.reacoes ?? []);
  }
  async function removerReacaoDaMensagem(mensagem: { id: string }) {
    if (!conversaId) return;
    const resposta = await removerReacaoChat(conversaId, mensagem.id);
    substituirReacoesDoChatInterno(cache, conversaId, mensagem.id, resposta.reacoes ?? []);
  }
  const conversaAtual = useMemo(() => conversas.data?.find((c) => c.id === conversaId), [conversas.data, conversaId]);
  if (conversas.isError) return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void conversas.refetch()} />;
  return <div className="flex h-full min-h-0 flex-col gap-5 p-6">
    <header><h1 className="flex items-center gap-2 text-2xl font-bold"><MessageCircle className="size-[calc(var(--tamanho-icone-interface)*1.5)]" />{textos.titulo}</h1></header>
    <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[300px_1fr]">
      <Card className="min-h-0"><CardHeader className="flex flex-row items-center justify-between"><CardTitle>{textos.conversas}</CardTitle><Button size="icon" variant="outline" aria-label={textos.novaConversa} onClick={() => contatos.refetch()}><Plus /></Button></CardHeader><CardContent className="space-y-2 overflow-y-auto">
        {conversas.isLoading && <p className="text-sm text-muted-foreground">{textos.carregando}</p>}
        {!conversas.isLoading && !conversas.data?.length && <p className="text-sm text-muted-foreground">{textos.semConversas}</p>}
        {conversas.data?.map((c) => <button key={c.id} type="button" aria-current={c.id === conversaId ? "true" : undefined} className="flex w-full items-center gap-3 rounded-xl border border-transparent p-3 text-left hover:bg-muted aria-[current=true]:border-primary/20 aria-[current=true]:bg-primary/10" onClick={() => setConversaId(c.id)}><AvatarIniciais id={c.id} nome={c.participantes || textos.titulo} fotoUrl={c.fotoUrl} className="flex size-10 shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white" /><span className="min-w-0 flex-1"><span className="block truncate font-medium">{c.participantes || textos.titulo}</span><span className="block truncate text-xs font-normal text-muted-foreground">{c.ultimaMensagem ?? textos.semMensagens}</span></span>{c.naoLidas > 0 && <span className="rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">{c.naoLidas}</span>}</button>)}
      </CardContent></Card>
      <Card className="min-h-0"><CardHeader className="p-0"><CardTitle className="sr-only">{conversaAtual?.participantes ?? textos.selecioneConversa}</CardTitle>{conversaAtual && <CabecalhoChatInterno conversa={conversaAtual} textos={textos} />}</CardHeader><CardContent className="flex min-h-0 flex-1 flex-col gap-3 p-0">
        {!conversaId && <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.selecioneConversa}</div>}
        {conversaId && (
          <ZonaSoltarArquivos
            accept={TIPOS_DE_ANEXO_ACEITOS}
            disabled={enviar.isPending || enviarMidia.isPending}
            rotulo={catalogo.atendimentos.composer.anexoSoltar}
            onArquivos={({ aceitos, rejeitados }) =>
              composerRef.current?.adicionarArquivos([...aceitos, ...rejeitados])
            }
          >
            {mensagens.isLoading ? <p className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.carregando}</p> : <ListaMensagensChatInterno mensagens={mensagens.data?.mensagens ?? []} usuarioAtual={usuarioAtual} textos={textos} onDefinirReacao={definirReacaoDaMensagem} onRemoverReacao={removerReacaoDaMensagem} />}
            <ComposerChatInterno ref={composerRef} textos={textos} enviando={enviar.isPending || enviarMidia.isPending} erro={enviar.isError || enviarMidia.isError} onEnviar={(conteudo) => enviar.mutateAsync({ id: conversaId, conteudo })} onEnviarMidia={(arquivo, legenda) => enviarMidia.mutateAsync({ id: conversaId, arquivo, legenda })} />
          </ZonaSoltarArquivos>
        )}
      </CardContent></Card>
    </div>
    {contatos.data && contatos.data.length > 0 && !conversaId && <div className="flex max-w-md gap-2"><Select value={contatoId} onValueChange={(v) => setContatoId(v ?? "")}><SelectTrigger className="flex-1"><SelectValue placeholder={textos.selecionarPessoa} /></SelectTrigger><SelectContent>{contatos.data.map((c) => <SelectItem key={c.id} value={c.id}>{c.nome}</SelectItem>)}</SelectContent></Select><Button disabled={!contatoId || abrir.isPending} onClick={() => abrir.mutate(contatoId)}>{textos.novaConversa}</Button></div>}
  </div>;
}
