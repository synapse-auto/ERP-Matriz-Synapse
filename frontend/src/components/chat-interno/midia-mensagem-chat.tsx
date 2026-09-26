"use client";

import { useRef, useState } from "react";
import { Download, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PlayerAudio } from "@/components/atendimentos/player-audio";
import { VisualizadorMidia } from "@/components/atendimentos/visualizador-midia";
import { useTextos } from "@/lib/config/textos-provider";
import { emitirUrlAssinadaDaMidiaChat } from "@/lib/chat-interno/api";
import { baixarArquivoChat, metadadosDaMidiaInterna } from "@/lib/chat-interno/midia";
import type { ChatMensagem } from "@/lib/chat-interno/types";
import { urlSegura } from "@/lib/utils";
import { TextoComLinks } from "@/components/mensagens/texto-com-links";

export function MidiaMensagemChat({ mensagem }: { mensagem: ChatMensagem }) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.media;
  const midias = catalogo.chatInterno.midias;
  const meta = metadadosDaMidiaInterna(mensagem);
  const [aberto, setAberto] = useState(false);
  const [baixando, setBaixando] = useState(false);
  const [erroDownload, setErroDownload] = useState(false);
  const [erroPreview, setErroPreview] = useState(false);
  const [renovando, setRenovando] = useState(false);
  const [renovada, setRenovada] = useState<{ origem: string | null | undefined; url: string } | null>(null);
  const emCurso = useRef(false);
  const src = urlSegura(renovada?.origem === mensagem.midiaUrl ? renovada?.url : mensagem.midiaUrl);
  const nome = meta.nome || (mensagem.tipo === "IMAGEM" ? textos.imagem : mensagem.tipo === "AUDIO" ? textos.audio : mensagem.tipo === "VIDEO" ? textos.visualizador?.video : textos.documento);
  const abrir = midias.abrir.replaceAll("{nome}", nome);

  async function renovar() {
    setRenovando(true);
    try {
      const resposta = await emitirUrlAssinadaDaMidiaChat(mensagem.conversaId, mensagem.id);
      if (!urlSegura(resposta.url)) throw new Error();
      setRenovada({ origem: mensagem.midiaUrl, url: resposta.url });
      setErroPreview(false);
    } catch { setErroPreview(true); } finally { setRenovando(false); }
  }

  async function baixar() {
    if (emCurso.current) return;
    emCurso.current = true;
    setBaixando(true);
    setErroDownload(false);
    try { await baixarArquivoChat(mensagem.conversaId, mensagem.id, meta.nome); }
    catch { setErroDownload(true); }
    finally { emCurso.current = false; setBaixando(false); }
  }

  return (
    <div className="min-w-0 max-w-sm space-y-2" data-slot="midia-mensagem-chat">
      {src && !erroPreview ? (
        <>
          {mensagem.tipo === "IMAGEM" && (
            <button type="button" className="block w-full rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={abrir} onClick={() => setAberto(true)}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={src} alt={meta.legenda || nome} className="max-h-64 max-w-full rounded-md object-contain" onError={() => setErroPreview(true)} />
            </button>
          )}
          {mensagem.tipo === "AUDIO" && <PlayerAudio src={src} rotulo={textos.audio} reproduzir={textos.reproduzir} pausar={textos.pausar} posicao={textos.posicao} onError={() => setErroPreview(true)} />}
          {mensagem.tipo === "VIDEO" && <video controls preload="metadata" src={src} aria-label={nome} className="max-h-64 max-w-full rounded-md" onError={() => setErroPreview(true)} />}
        </>
      ) : mensagem.tipo !== "DOCUMENTO" && (
        <div className="space-y-1">
          <p role="alert" className="text-xs">{midias.erro}</p>
          <Button type="button" variant="outline" size="sm" disabled={renovando} onClick={() => void renovar()}>{renovando ? midias.carregando : catalogo.chatInterno.tentarNovamente}</Button>
        </div>
      )}
      {mensagem.tipo === "DOCUMENTO" && <button type="button" onClick={() => setAberto(true)} className="flex max-w-full items-center gap-2 rounded-md p-1 text-left focus-visible:ring-2 focus-visible:ring-ring" aria-label={abrir}><FileText aria-hidden className="size-5 shrink-0" /><span className="break-all font-semibold">{nome}</span></button>}
      {meta.legenda && <p className="whitespace-pre-wrap break-words"><TextoComLinks texto={meta.legenda} rotuloAbrir={midias.abrir} /></p>}
      <div className="flex flex-wrap items-center gap-2">
        {meta.tamanho !== null && <span className="text-xs opacity-75">{Math.ceil(meta.tamanho / 1024)} KB</span>}
        <Button type="button" size="sm" variant="ghost" disabled={baixando} aria-busy={baixando} aria-label={midias.baixar.replaceAll("{nome}", nome)} onClick={() => void baixar()}>
          <Download className="size-4" aria-hidden />{baixando ? midias.carregando : textos.baixar}
        </Button>
      </div>
      {erroDownload && <p role="alert" className="text-xs">{midias.erro}</p>}
      {aberto && <VisualizadorMidia aberto onFechar={() => setAberto(false)} indice={0} itens={[{ id: mensagem.id, nome: meta.nome, mimetype: meta.mimetype, tamanho: meta.tamanho, enviadoEm: mensagem.enviadoEm, tipoMensagem: mensagem.tipo ?? null, origem: { tipo: "chat-interno", conversaId: mensagem.conversaId, mensagemId: mensagem.id } }]} />}
    </div>
  );
}
