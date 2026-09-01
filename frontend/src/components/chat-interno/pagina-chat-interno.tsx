"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { MessageCircle, Plus, UsersRound } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import {
  listarContatosChat,
  listarConversasChat,
  listarMensagensChat,
  abrirConversaDireta,
  criarGrupoChat,
  enviarMensagemChat,
  enviarMidiaChat,
  marcarChatComoLido,
  definirReacaoChat,
  removerReacaoChat,
} from "@/lib/chat-interno/api";
import { previewUltimaMensagem } from "@/lib/chat-interno/mensagem-sistema";
import { atualizarReacoesDoChatInterno, substituirReacoesDoChatInterno } from "@/lib/atendimento/reacoes-cache";
import { TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
import { ZonaSoltarArquivos } from "@/components/atendimentos/zona-soltar-arquivos";
import { CabecalhoChatInterno, ComposerChatInterno, ListaMensagensChatInterno, type ComposerChatHandle } from "./componentes-chat-interno";
import { DialogoSelecionarPessoa } from "./dialogo-selecionar-pessoa";
import { DialogoCriarGrupo } from "./dialogo-criar-grupo";
import { PainelParticipantesGrupo } from "./painel-participantes-grupo";

export function PaginaChatInterno() {
  const catalogo = useTextos();
  const textos = catalogo.chatInterno;
  const usuarioAtual = useAuthStore((s) => s.usuarioId);
  const cache = useQueryClient();
  const composerRef = useRef<ComposerChatHandle>(null);
  const conversas = useQuery({ queryKey: ["chat-interno", "conversas"], queryFn: listarConversasChat });
  const contatos = useQuery({ queryKey: ["chat-interno", "contatos"], queryFn: listarContatosChat });
  const [conversaId, setConversaId] = useState<string | null>(null);
  const [dialogoDireta, setDialogoDireta] = useState(false);
  const [dialogoGrupo, setDialogoGrupo] = useState(false);
  const [painelGrupo, setPainelGrupo] = useState(false);
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
  const abrir = useMutation({
    mutationFn: abrirConversaDireta,
    onSuccess: (r) => { setConversaId(r.id); setDialogoDireta(false); atualizar(); },
  });
  const criarGrupo = useMutation({
    mutationFn: ({ nome, participantes }: { nome: string; participantes: string[] }) =>
      criarGrupoChat(nome, participantes),
    onSuccess: (r) => { setConversaId(r.id); setDialogoGrupo(false); atualizar(); },
  });
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
  return (
    <div className="flex h-full min-h-0 flex-col gap-5 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <MessageCircle className="size-[calc(var(--tamanho-icone-interface)*1.5)]" />
          {textos.titulo}
        </h1>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => { void contatos.refetch(); setDialogoDireta(true); }}
          >
            <Plus className="size-(--tamanho-icone-interface)" aria-hidden />
            {textos.novaConversa}
          </Button>
          <Button
            type="button"
            onClick={() => { void contatos.refetch(); setDialogoGrupo(true); }}
          >
            <UsersRound className="size-(--tamanho-icone-interface)" aria-hidden />
            {textos.novoGrupo}
          </Button>
        </div>
      </header>
      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[300px_1fr]">
        <Card className="min-h-0">
          <CardHeader>
            <CardTitle>{textos.conversas}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 overflow-y-auto">
            {conversas.isLoading && <p className="text-sm text-muted-foreground">{textos.carregando}</p>}
            {!conversas.isLoading && !conversas.data?.length && (
              <p className="text-sm text-muted-foreground">{textos.semConversas}</p>
            )}
            {conversas.data?.map((c) => {
              const nome = c.participantes || textos.titulo;
              const preview = previewUltimaMensagem(c.ultimaMensagem, textos.sistema) || textos.semMensagens;
              return (
                <button
                  key={c.id}
                  type="button"
                  aria-current={c.id === conversaId ? "true" : undefined}
                  className="flex w-full items-center gap-3 rounded-xl border border-transparent p-3 text-left hover:bg-muted aria-[current=true]:border-primary/20 aria-[current=true]:bg-primary/10"
                  onClick={() => setConversaId(c.id)}
                >
                  {c.tipo === "GRUPO" ? (
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary" aria-hidden>
                      <UsersRound className="size-[calc(var(--tamanho-icone-interface)*1.1)]" />
                    </span>
                  ) : (
                    <AvatarIniciais
                      id={c.id}
                      nome={nome}
                      fotoUrl={c.fotoUrl}
                      className="flex size-10 shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white"
                    />
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{nome}</span>
                    <span className="block truncate text-xs font-normal text-muted-foreground">{preview}</span>
                  </span>
                  {c.naoLidas > 0 && (
                    <span className="rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">{c.naoLidas}</span>
                  )}
                </button>
              );
            })}
          </CardContent>
        </Card>
        <Card className="min-h-0">
          <CardHeader className="p-0">
            <CardTitle className="sr-only">{conversaAtual?.participantes ?? textos.selecioneConversa}</CardTitle>
            {conversaAtual && (
              <CabecalhoChatInterno
                conversa={conversaAtual}
                textos={textos}
                onGerenciarGrupo={conversaAtual.tipo === "GRUPO" ? () => setPainelGrupo(true) : undefined}
              />
            )}
          </CardHeader>
          <CardContent className="flex min-h-0 flex-1 flex-col gap-3 p-0">
            {!conversaId && (
              <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
                {textos.selecioneConversa}
              </div>
            )}
            {conversaId && (
              <ZonaSoltarArquivos
                accept={TIPOS_DE_ANEXO_ACEITOS}
                disabled={enviar.isPending || enviarMidia.isPending}
                rotulo={catalogo.atendimentos.composer.anexoSoltar}
                onArquivos={({ aceitos, rejeitados }) =>
                  composerRef.current?.adicionarArquivos([...aceitos, ...rejeitados])
                }
              >
                {mensagens.isLoading ? (
                  <p className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.carregando}</p>
                ) : (
                  <ListaMensagensChatInterno
                    mensagens={mensagens.data?.mensagens ?? []}
                    usuarioAtual={usuarioAtual}
                    textos={textos}
                    onDefinirReacao={definirReacaoDaMensagem}
                    onRemoverReacao={removerReacaoDaMensagem}
                  />
                )}
                <ComposerChatInterno
                  ref={composerRef}
                  textos={textos}
                  enviando={enviar.isPending || enviarMidia.isPending}
                  erro={enviar.isError || enviarMidia.isError}
                  onEnviar={(conteudo) => enviar.mutateAsync({ id: conversaId, conteudo })}
                  onEnviarMidia={(arquivo, legenda) => enviarMidia.mutateAsync({ id: conversaId, arquivo, legenda })}
                />
              </ZonaSoltarArquivos>
            )}
          </CardContent>
        </Card>
      </div>

      <DialogoSelecionarPessoa
        aberto={dialogoDireta}
        onFechar={() => setDialogoDireta(false)}
        contatos={contatos.data ?? []}
        carregando={contatos.isLoading}
        erro={contatos.isError}
        onTentarNovamente={() => void contatos.refetch()}
        onSelecionar={(id) => abrir.mutateAsync(id)}
        textos={textos}
      />
      <DialogoCriarGrupo
        aberto={dialogoGrupo}
        onFechar={() => setDialogoGrupo(false)}
        contatos={contatos.data ?? []}
        onCriar={(nome, participantes) => criarGrupo.mutateAsync({ nome, participantes })}
        textos={textos}
      />
      {conversaId && conversaAtual?.tipo === "GRUPO" && (
        <PainelParticipantesGrupo
          aberto={painelGrupo}
          onFechar={() => setPainelGrupo(false)}
          conversaId={conversaId}
          nomeAtual={conversaAtual.participantes}
          usuarioAtual={usuarioAtual}
          textos={textos}
          onSaiu={() => setConversaId(null)}
        />
      )}
    </div>
  );
}
