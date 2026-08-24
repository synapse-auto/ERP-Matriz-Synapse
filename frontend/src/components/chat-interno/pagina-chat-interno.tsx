"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { MessageCircle, Plus, Send } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import { listarContatosChat, listarConversasChat, listarMensagensChat, abrirConversaDireta, enviarMensagemChat, marcarChatComoLido } from "@/lib/chat-interno/api";

export function PaginaChatInterno() {
  const textos = useTextos().chatInterno;
  const usuarioAtual = useAuthStore((s) => s.usuarioId);
  const cache = useQueryClient();
  const conversas = useQuery({ queryKey: ["chat-interno", "conversas"], queryFn: listarConversasChat });
  const contatos = useQuery({ queryKey: ["chat-interno", "contatos"], queryFn: listarContatosChat });
  const [conversaId, setConversaId] = useState<string | null>(null);
  const [contatoId, setContatoId] = useState("");
  const [texto, setTexto] = useState("");
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
  });
  useEffect(() => { if (conversaId) { void marcarChatComoLido(conversaId); } }, [conversaId]);
  const abrir = useMutation({ mutationFn: abrirConversaDireta, onSuccess: (r) => { setConversaId(r.id); setContatoId(""); atualizar(); } });
  const enviar = useMutation({ mutationFn: ({ id, conteudo }: { id: string; conteudo: string }) => enviarMensagemChat(id, conteudo), onSuccess: () => { setTexto(""); atualizar(); } });
  const conversaAtual = useMemo(() => conversas.data?.find((c) => c.id === conversaId), [conversas.data, conversaId]);
  const enviarTexto = () => { if (conversaId && texto.trim()) enviar.mutate({ id: conversaId, conteudo: texto }); };

  if (conversas.isError) return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void conversas.refetch()} />;
  return <div className="flex h-full min-h-0 flex-col gap-5 p-6">
    <header><h1 className="flex items-center gap-2 text-2xl font-bold"><MessageCircle className="size-6" />{textos.titulo}</h1></header>
    <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[300px_1fr]">
      <Card className="min-h-0"><CardHeader className="flex flex-row items-center justify-between"><CardTitle>{textos.conversas}</CardTitle><Button size="icon" variant="outline" aria-label={textos.novaConversa} onClick={() => contatos.refetch()}><Plus /></Button></CardHeader><CardContent className="space-y-2 overflow-y-auto">
        {conversas.isLoading && <p className="text-sm text-muted-foreground">{textos.carregando}</p>}
        {!conversas.isLoading && !conversas.data?.length && <p className="text-sm text-muted-foreground">{textos.semConversas}</p>}
        {conversas.data?.map((c) => <button key={c.id} type="button" className="flex w-full items-center gap-3 rounded-lg p-2 text-left hover:bg-muted" onClick={() => setConversaId(c.id)}><AvatarIniciais id={c.id} nome={c.participantes || "Chat"} /><span className="min-w-0 flex-1"><span className="block truncate font-medium">{c.participantes}</span><span className="block truncate text-xs text-muted-foreground">{c.ultimaMensagem ?? textos.semMensagens}</span></span>{c.naoLidas > 0 && <span className="rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">{c.naoLidas}</span>}</button>)}
      </CardContent></Card>
      <Card className="min-h-0"><CardHeader><CardTitle>{conversaAtual?.participantes ?? textos.selecioneConversa}</CardTitle></CardHeader><CardContent className="flex min-h-0 flex-1 flex-col gap-3">
        {!conversaId && <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.selecioneConversa}</div>}
        {conversaId && <><div className="flex-1 space-y-3 overflow-y-auto rounded-lg bg-muted/30 p-4">{mensagens.data?.mensagens.length ? mensagens.data.mensagens.map((m) => <div key={m.id} className={`flex ${m.remetenteId === usuarioAtual ? "justify-end" : "justify-start"}`}><div className="max-w-[75%] rounded-xl bg-background px-3 py-2 text-sm shadow-sm"><p>{m.conteudo}</p><time className="mt-1 block text-[10px] text-muted-foreground">{new Date(m.enviadoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</time></div></div>) : <p className="text-center text-sm text-muted-foreground">{textos.semMensagens}</p>}</div><div className="flex items-end gap-2"><Textarea value={texto} onChange={(e) => setTexto(e.target.value)} placeholder={textos.placeholder} rows={2} onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); enviarTexto(); } }} /><Button onClick={enviarTexto} disabled={enviar.isPending || !texto.trim()}><Send />{textos.enviar}</Button></div></>}
      </CardContent></Card>
    </div>
    {contatos.data && contatos.data.length > 0 && !conversaId && <div className="flex max-w-md gap-2"><Select value={contatoId} onValueChange={(v) => setContatoId(v ?? "")}><SelectTrigger className="flex-1"><SelectValue placeholder={textos.selecionarPessoa} /></SelectTrigger><SelectContent>{contatos.data.map((c) => <SelectItem key={c.id} value={c.id}>{c.nome}</SelectItem>)}</SelectContent></Select><Button disabled={!contatoId || abrir.isPending} onClick={() => abrir.mutate(contatoId)}>{textos.novaConversa}</Button></div>}
  </div>;
}
