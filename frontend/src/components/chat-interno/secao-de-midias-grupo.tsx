"use client";

import { useState } from "react";
import { Download, ExternalLink, FileText, Image as ImageIcon, Music, Video } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ContadorDoPainel } from "@/components/ui/contador-do-painel";
import { emitirUrlAssinadaDaMidiaChat } from "@/lib/chat-interno/api";
import type { MidiaDoGrupo, TipoMidiaChatInterno } from "@/lib/chat-interno/types";
import { useMidiasDoGrupo, useUrlAssinadaDaMidiaGrupo } from "@/lib/chat-interno/use-midias-do-grupo";
import { baixarUrlAssinada } from "@/lib/midia/baixar-url-assinada";
import { urlSegura } from "@/lib/utils";
import type { Textos } from "@/lib/config/schema";

type TextosChat = Textos["chatInterno"];

function rotuloNome(item: MidiaDoGrupo): string {
  return item.nome?.trim() || item.tipo;
}

function IconeDaMidia({ tipo }: { tipo: TipoMidiaChatInterno }) {
  if (tipo === "IMAGEM") return <ImageIcon className="size-(--tamanho-icone-interface) text-primary" aria-hidden />;
  if (tipo === "AUDIO") return <Music className="size-(--tamanho-icone-interface) text-primary" aria-hidden />;
  if (tipo === "VIDEO") return <Video className="size-(--tamanho-icone-interface) text-primary" aria-hidden />;
  return <FileText className="size-(--tamanho-icone-interface) text-muted-foreground" aria-hidden />;
}

function MidiaGrupoItem({ conversaId, item, textos }: { conversaId: string; item: MidiaDoGrupo; textos: TextosChat["midias"] }) {
  const [pendente, setPendente] = useState(false);
  const visualizavel = item.tipo === "IMAGEM" || item.tipo === "AUDIO" || item.tipo === "VIDEO";
  const url = useUrlAssinadaDaMidiaGrupo(conversaId, item.mensagemId, visualizavel);
  const segura = urlSegura(url.data?.url);
  const nome = rotuloNome(item);
  const rotuloAbrir = textos.abrir.replace("{nome}", nome);
  const rotuloBaixar = textos.baixar.replace("{nome}", nome);

  async function abrirOuBaixar() {
    setPendente(true);
    try {
      const resposta = visualizavel && segura ? { url: segura } : await emitirUrlAssinadaDaMidiaChat(conversaId, item.mensagemId);
      baixarUrlAssinada(resposta.url);
    } finally {
      setPendente(false);
    }
  }

  return (
    <article className="rounded-lg border border-border bg-muted/30 p-2" data-slot="midia-grupo-item">
      <div className="flex items-center gap-2">
        <IconeDaMidia tipo={item.tipo} />
        <span className="min-w-0 flex-1 truncate text-xs font-medium" title={nome}>{nome}</span>
        <Button type="button" variant="ghost" size="icon" aria-label={rotuloAbrir} title={rotuloAbrir} disabled={pendente} onClick={() => void abrirOuBaixar()}>
          <ExternalLink className="size-(--tamanho-icone-interface)" aria-hidden />
        </Button>
        <Button type="button" variant="ghost" size="icon" aria-label={rotuloBaixar} title={rotuloBaixar} disabled={pendente} onClick={() => void abrirOuBaixar()}>
          <Download className="size-(--tamanho-icone-interface)" aria-hidden />
        </Button>
      </div>
      {item.tipo === "IMAGEM" && segura && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={segura} alt={item.legenda || nome} className="mt-2 max-h-40 w-full rounded-md object-cover" />
      )}
      {item.tipo === "AUDIO" && segura && <audio className="mt-2 w-full" controls src={segura} />}
      {item.tipo === "VIDEO" && segura && <video className="mt-2 max-h-40 w-full rounded-md" controls src={segura} />}
      {item.legenda && <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{item.legenda}</p>}
      <p className="mt-1 text-[0.65rem] text-muted-foreground">
        {item.tipo} {item.tamanho > 0 ? `· ${Math.ceil(item.tamanho / 1024)} KB` : ""} · {new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(item.enviadoEm))}
      </p>
    </article>
  );
}

export function ListaDeMidiasDoGrupo({ conversaId, textos: textosChat }: { conversaId: string; textos: TextosChat }) {
  const textos = textosChat.midias;
  const midias = useMidiasDoGrupo(conversaId);
  const itens = midias.data?.pages.flat() ?? [];

  return (
    <section aria-labelledby="midias-grupo-titulo" className="space-y-2" data-slot="secao-midias-grupo">
      <h3 id="midias-grupo-titulo" className="flex items-center justify-between px-0.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">
        <span>{textos.titulo}</span>
      </h3>
      <ContadorDoPainel valor={itens.length} rotulo={textos.titulo} />
      {midias.isLoading && <p className="p-2 text-xs text-muted-foreground">{textos.carregando}</p>}
      {midias.isError && <p role="alert" className="p-2 text-xs text-destructive">{textos.erro}</p>}
      {!midias.isLoading && !midias.isError && itens.length === 0 && (
        <p className="p-2 text-center text-xs text-muted-foreground">{textos.vazio}</p>
      )}
      {itens.length > 0 && (
        <div className="space-y-1.5" aria-labelledby="midias-grupo-titulo">
          {itens.map((item) => <MidiaGrupoItem key={item.mensagemId} conversaId={conversaId} item={item} textos={textos} />)}
        </div>
      )}
      {midias.hasNextPage && (
        <Button type="button" size="sm" variant="outline" className="w-full" onClick={() => void midias.fetchNextPage()} disabled={midias.isFetchingNextPage}>
          {textos.carregarMais}
        </Button>
      )}
    </section>
  );
}
