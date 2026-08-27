"use client";

import { useEffect, useState } from "react";
import { Send, Users } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { listarConversasChat, listarMensagensChat, enviarMensagemChat, marcarChatComoLido } from "@/lib/chat-interno/api";
import { useTextos } from "@/lib/config/textos-provider";

export function PainelConversaInterna({ conversaId }: { conversaId: string }) {
  const textos = useTextos().chatInterno;
  const cache = useQueryClient();
  const [texto, setTexto] = useState("");
  const conversas = useQuery({ queryKey: ["chat-interno", "conversas"], queryFn: listarConversasChat });
  const mensagens = useQuery({ queryKey: ["chat-interno", "mensagens", conversaId], queryFn: () => listarMensagensChat(conversaId) });
  const conversa = conversas.data?.find((item) => item.id === conversaId);
  const enviar = useMutation({
    mutationFn: () => enviarMensagemChat(conversaId, texto.trim()),
    onSuccess: () => { setTexto(""); void cache.invalidateQueries({ queryKey: ["chat-interno"] }); },
  });
  useEffect(() => { void marcarChatComoLido(conversaId).catch(() => undefined); }, [conversaId]);
  const enviarTexto = () => { if (texto.trim() && !enviar.isPending) enviar.mutate(); };
  if (mensagens.isError) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void mensagens.refetch()} />;
  }
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex items-center gap-3 border-b border-border bg-background px-5 py-4">
        <AvatarIniciais id={conversaId} nome={conversa?.participantes || textos.titulo} />
        <div><h2 className="flex items-center gap-2 font-semibold"><Users className="size-4" aria-hidden />{conversa?.participantes || textos.titulo}</h2><p className="text-xs text-muted-foreground">{textos.titulo}</p></div>
      </header>
      <div className="flex-1 space-y-3 overflow-y-auto bg-muted/20 p-5">
        {mensagens.data?.mensagens.length ? mensagens.data.mensagens.map((mensagem) => (
          <div key={mensagem.id} className="rounded-xl bg-background px-3 py-2 text-sm shadow-sm"><p>{mensagem.conteudo}</p><time className="mt-1 block text-[10px] text-muted-foreground">{new Date(mensagem.enviadoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</time></div>
        )) : <p className="text-center text-sm text-muted-foreground">{textos.semMensagens}</p>}
      </div>
      <div className="flex items-end gap-2 border-t border-border bg-background p-4">
        <Textarea value={texto} onChange={(evento) => setTexto(evento.target.value)} placeholder={textos.placeholder} rows={2} onKeyDown={(evento) => { if (evento.key === "Enter" && !evento.shiftKey) { evento.preventDefault(); enviarTexto(); } }} />
        <Button onClick={enviarTexto} disabled={!texto.trim() || enviar.isPending}><Send className="size-4" aria-hidden />{textos.enviar}</Button>
      </div>
    </div>
  );
}
