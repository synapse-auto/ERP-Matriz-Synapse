"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Download, FileText, XIcon } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { apiFetchBlob } from "@/lib/api/http-client";
import { useTextos } from "@/lib/config/textos-provider";
import { emitirUrlAssinadaDaMidia } from "@/lib/lead/api";
import { classificarMidiaVisual } from "@/lib/midia/classificar-midia-visual";
import { baixarUrlAssinada } from "@/lib/midia/baixar-url-assinada";
import { classificarOrigemDeRecursoVisual } from "@/lib/midia/origem-de-recurso-visual";
import { urlSegura } from "@/lib/utils";

import { PlayerAudio } from "./player-audio";

export type OrigemDoVisualizador =
  | { tipo: "mensagem"; leadId: string; mensagemId: string }
  | { tipo: "foto"; fotoUrl: string };

export type ItemDoVisualizador = {
  id: string;
  nome: string | null;
  mimetype: string | null;
  tamanho: number | null;
  enviadoEm: string | null;
  tipoMensagem: string | null;
  origem: OrigemDoVisualizador;
};

type Props = {
  aberto: boolean;
  onFechar: () => void;
  itens: ItemDoVisualizador[];
  indice: number;
  onIndiceChange?: (indice: number) => void;
};

function preencher(modelo: string, valores: Record<string, string>): string {
  return Object.entries(valores).reduce(
    (texto, [chave, valor]) => texto.replaceAll(`{${chave}}`, valor),
    modelo,
  );
}

function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function dataLegivel(iso: string): string {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(
    new Date(iso),
  );
}

function baixarBlob(blob: Blob, nome: string) {
  const url = URL.createObjectURL(blob);
  const ancora = document.createElement("a");
  ancora.href = url;
  ancora.download = nome;
  ancora.rel = "noopener noreferrer";
  document.body.appendChild(ancora);
  ancora.click();
  ancora.remove();
  URL.revokeObjectURL(url);
}

/**
 * Overlay único para imagem, vídeo, PDF, áudio e documento. A URL assinada é emitida
 * no instante em que o overlay abre — nunca reusa a URL da listagem de mensagens.
 */
export function VisualizadorMidia({
  aberto,
  onFechar,
  itens,
  indice,
  onIndiceChange,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.media;
  const vis = textos.visualizador;
  const item = itens[indice] ?? null;
  const ramo = item
    ? classificarMidiaVisual(item.tipoMensagem, item.mimetype, item.nome)
    : "documento";
  const navegavel = Boolean(onIndiceChange) && itens.length > 1;
  const origemDoFoco = useRef<HTMLElement | null>(null);
  const src = useSrcDoVisualizador(aberto, item);

  useEffect(() => {
    if (aberto) {
      const ativo = document.activeElement;
      if (ativo instanceof HTMLElement) origemDoFoco.current = ativo;
    }
  }, [aberto]);

  function fechar() {
    const devolver = origemDoFoco.current;
    onFechar();
    queueMicrotask(() => devolver?.focus());
  }

  function irPara(delta: number) {
    if (!onIndiceChange || itens.length === 0) return;
    const proximo = indice + delta;
    if (proximo < 0 || proximo >= itens.length) return;
    onIndiceChange(proximo);
  }

  async function baixar() {
    if (!item) return;
    if (item.origem.tipo === "mensagem") {
      const { url } = await emitirUrlAssinadaDaMidia(item.origem.leadId, item.origem.mensagemId);
      baixarUrlAssinada(url);
      return;
    }
    const origem = classificarOrigemDeRecursoVisual(item.origem.fotoUrl);
    if (origem.tipo === "absoluta") {
      baixarUrlAssinada(origem.url);
      return;
    }
    if (origem.tipo === "autenticada" && src.blob) {
      baixarBlob(src.blob, item.nome ?? "foto");
    }
  }

  const titulo = item?.nome?.trim()
    || (ramo === "imagem" ? textos.imagem
      : ramo === "audio" ? textos.audio
        : ramo === "video" ? vis.video
          : textos.documento);

  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && fechar()}>
      <DialogContent
        showCloseButton={false}
        aria-label={titulo}
        className="flex max-h-[90vh] w-[min(100%-2rem,56rem)] max-w-none flex-col gap-0 overflow-hidden p-0 sm:max-w-[56rem]"
        onKeyDown={(evento) => {
          if (!navegavel) return;
          if (evento.key === "ArrowLeft") {
            evento.preventDefault();
            irPara(-1);
          }
          if (evento.key === "ArrowRight") {
            evento.preventDefault();
            irPara(1);
          }
        }}
      >
        <DialogHeader className="shrink-0 gap-1 border-b px-4 py-3 pr-12">
          <DialogTitle className="truncate">{titulo}</DialogTitle>
          <DialogDescription>
            {[
              item?.enviadoEm ? dataLegivel(item.enviadoEm) : null,
              item?.mimetype,
              item?.tamanho != null ? tamanhoLegivel(item.tamanho) : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </DialogDescription>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="absolute top-2 right-2"
            aria-label={vis.fechar}
            onClick={fechar}
          >
            <XIcon aria-hidden />
          </Button>
        </DialogHeader>

        <div className="relative flex min-h-0 flex-1 items-center justify-center bg-muted/30 p-4">
          {navegavel && (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="absolute left-2 z-10"
              aria-label={vis.anterior}
              disabled={indice <= 0}
              onClick={() => irPara(-1)}
            >
              <ChevronLeft aria-hidden />
            </Button>
          )}

          <ConteudoDoVisualizador
            ramo={ramo}
            src={src.src}
            carregando={src.carregando}
            erro={src.erro}
            item={item}
            textos={textos}
            vis={vis}
            onBaixar={() => void baixar()}
          />

          {navegavel && (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="absolute right-2 z-10"
              aria-label={vis.proxima}
              disabled={indice >= itens.length - 1}
              onClick={() => irPara(1)}
            >
              <ChevronRight aria-hidden />
            </Button>
          )}
        </div>

        <div className="flex shrink-0 items-center justify-end gap-2 border-t px-4 py-3">
          <Button type="button" variant="outline" onClick={() => void baixar()} disabled={!item}>
            <Download className="size-(--tamanho-icone-interface)" aria-hidden />
            {textos.baixar}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function useSrcDoVisualizador(aberto: boolean, item: ItemDoVisualizador | null) {
  const origemMensagem = item?.origem.tipo === "mensagem" ? item.origem : null;
  const origemFoto = item?.origem.tipo === "foto" ? item.origem.fotoUrl : null;
  const classificacaoFoto = origemFoto ? classificarOrigemDeRecursoVisual(origemFoto) : null;

  const urlAssinada = useQuery({
    queryKey: ["visualizador-midia-url", origemMensagem?.leadId, origemMensagem?.mensagemId],
    queryFn: () => emitirUrlAssinadaDaMidia(origemMensagem!.leadId, origemMensagem!.mensagemId),
    enabled: aberto && Boolean(origemMensagem),
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: "always",
  });

  const blobFoto = useQuery({
    queryKey: ["visualizador-foto", origemFoto],
    queryFn: () => apiFetchBlob(classificacaoFoto && classificacaoFoto.tipo === "autenticada" ? classificacaoFoto.caminho : ""),
    enabled: aberto && classificacaoFoto?.tipo === "autenticada",
    staleTime: 0,
    gcTime: 0,
  });

  const blobUrl = useMemo(
    () => (blobFoto.data ? URL.createObjectURL(blobFoto.data) : null),
    [blobFoto.data],
  );
  useEffect(() => () => { if (blobUrl) URL.revokeObjectURL(blobUrl); }, [blobUrl]);

  if (!aberto || !item) {
    return { src: undefined as string | undefined, carregando: false, erro: false, blob: undefined as Blob | undefined };
  }

  if (origemMensagem) {
    return {
      src: urlSegura(urlAssinada.data?.url),
      carregando: urlAssinada.isLoading || urlAssinada.isFetching,
      erro: urlAssinada.isError,
      blob: undefined,
    };
  }

  if (classificacaoFoto?.tipo === "autenticada") {
    return {
      src: blobUrl ?? undefined,
      carregando: blobFoto.isLoading,
      erro: blobFoto.isError,
      blob: blobFoto.data,
    };
  }

  if (classificacaoFoto?.tipo === "absoluta") {
    return { src: classificacaoFoto.url, carregando: false, erro: false, blob: undefined };
  }

  return { src: undefined, carregando: false, erro: true, blob: undefined };
}

function ConteudoDoVisualizador({
  ramo,
  src,
  carregando,
  erro,
  item,
  textos,
  vis,
  onBaixar,
}: {
  ramo: ReturnType<typeof classificarMidiaVisual>;
  src: string | undefined;
  carregando: boolean;
  erro: boolean;
  item: ItemDoVisualizador | null;
  textos: {
    imagem: string;
    audio: string;
    reproduzir: string;
    pausar: string;
    posicao: string;
    documento: string;
    baixar: string;
  };
  vis: {
    carregando: string;
    pdfIndisponivel: string;
    documentoNaoRenderizavel: string;
    erroAoCarregar: string;
    video: string;
  };
  onBaixar: () => void;
}) {
  if (carregando) {
    return <p className="text-sm text-muted-foreground">{vis.carregando}</p>;
  }
  if (erro || (!src && ramo !== "documento")) {
    return (
      <div className="flex max-w-sm flex-col items-center gap-3 text-center">
        <p className="text-sm text-destructive" role="alert">{vis.erroAoCarregar}</p>
        <Button type="button" variant="outline" onClick={onBaixar}>{textos.baixar}</Button>
      </div>
    );
  }

  if (ramo === "imagem" && src) {
    // eslint-disable-next-line @next/next/no-img-element -- mídia do storage ou blob autenticado
    return <img src={src} alt={item?.nome ?? textos.imagem} className="max-h-[min(70vh,40rem)] max-w-full object-contain" />;
  }

  if (ramo === "video" && src) {
    return (
      <video
        controls
        src={src}
        aria-label={item?.nome ?? vis.video}
        className="max-h-[min(70vh,40rem)] max-w-full"
      />
    );
  }

  if (ramo === "audio" && src) {
    return (
      <PlayerAudio
        src={src}
        rotulo={textos.audio}
        reproduzir={textos.reproduzir}
        pausar={textos.pausar}
        posicao={textos.posicao}
      />
    );
  }

  if (ramo === "pdf" && src) {
    return (
      <object
        data={src}
        type="application/pdf"
        aria-label={item?.nome ?? textos.documento}
        className="h-[min(70vh,40rem)] w-full"
      >
        <div className="flex flex-col items-center gap-3 p-6 text-center">
          <p className="text-sm text-muted-foreground">{vis.pdfIndisponivel}</p>
          <Button type="button" variant="outline" onClick={onBaixar}>{textos.baixar}</Button>
        </div>
      </object>
    );
  }

  return (
    <div className="flex max-w-sm flex-col items-center gap-3 text-center">
      <span className="flex size-14 items-center justify-center rounded-xl bg-muted">
        <FileText className="size-7 text-muted-foreground" aria-hidden />
      </span>
      <p className="font-medium">{item?.nome ?? textos.documento}</p>
      <p className="text-sm text-muted-foreground">{vis.documentoNaoRenderizavel}</p>
      {item?.mimetype && <p className="text-xs text-muted-foreground">{item.mimetype}</p>}
      {item?.tamanho != null && <p className="text-xs text-muted-foreground">{tamanhoLegivel(item.tamanho)}</p>}
      <Button type="button" onClick={onBaixar}>{textos.baixar}</Button>
    </div>
  );
}

export function FotoDoLeadClicavel({
  id,
  nome,
  fotoUrl,
  className,
}: {
  id: string;
  nome: string;
  fotoUrl: string | null;
  className?: string;
}) {
  if (!fotoUrl) {
    return (
      <AvatarIniciais
        id={id}
        nome={nome}
        fotoAlt={nome}
        className={className}
      />
    );
  }

  return (
    <FotoDoLeadComVisualizador
      id={id}
      nome={nome}
      fotoUrl={fotoUrl}
      className={className}
    />
  );
}

function FotoDoLeadComVisualizador({
  id,
  nome,
  fotoUrl,
  className,
}: {
  id: string;
  nome: string;
  fotoUrl: string;
  className?: string;
}) {
  const [aberto, setAberto] = useState(false);
  const vis = useTextos().atendimentos.media.visualizador;

  return (
    <>
      <button
        type="button"
        className="cursor-pointer rounded-full"
        aria-label={preencher(vis.abrirFoto, { nome })}
        onClick={() => setAberto(true)}
      >
        <AvatarIniciais
          id={id}
          nome={nome}
          fotoUrl={fotoUrl}
          fotoAlt={nome}
          className={className}
        />
      </button>
      <VisualizadorMidia
        aberto={aberto}
        onFechar={() => setAberto(false)}
        itens={[
          {
            id: `foto-${id}`,
            nome,
            mimetype: "image/*",
            tamanho: null,
            enviadoEm: null,
            tipoMensagem: "IMAGEM",
            origem: { tipo: "foto", fotoUrl },
          },
        ]}
        indice={0}
      />
    </>
  );
}
