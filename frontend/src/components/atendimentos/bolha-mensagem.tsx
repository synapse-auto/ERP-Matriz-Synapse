"use client";

import { useState } from "react";
import { FileText, Maximize2 } from "lucide-react";

import { useTextos } from "@/lib/config/textos-provider";
import { cn, urlSegura } from "@/lib/utils";
import type { MensagemResposta } from "@/lib/atendimento/types";

import { InteracaoMensagem } from "@/components/mensagens/interacao-mensagem";

import { CitacaoMensagemVisual } from "./citacao-mensagem";
import { StatusEntregaIcone } from "./status-entrega";
import { PlayerAudio } from "./player-audio";
import { VisualizadorMidia, type ItemDoVisualizador } from "./visualizador-midia";

interface MidiaMetadados {
  nome?: string;
  mimetype?: string;
  tamanho?: number;
  legenda?: string;
}

interface OpcaoInterativa {
  id?: string;
  titulo?: string;
  descricao?: string;
}

function metadadosDaMidia(json: string | null): MidiaMetadados {
  if (!json) {
    return {};
  }
  try {
    return JSON.parse(json) as MidiaMetadados;
  } catch {
    return {};
  }
}

function opcoesInterativas(json: string | null): OpcaoInterativa[] {
  if (!json) return [];
  try {
    const valor: unknown = JSON.parse(json);
    if (!Array.isArray(valor)) return [];
    return valor.filter(
      (item): item is OpcaoInterativa =>
        typeof item === "object" && item !== null &&
        (typeof (item as OpcaoInterativa).titulo === "string" ||
          typeof (item as OpcaoInterativa).id === "string"),
    );
  } catch {
    return [];
  }
}

type Props = {
  mensagem: MensagemResposta;
  leadId?: string;
  onReenviar?: () => void;
  nomeDoRemetente?: string | null;
  onDefinirReacao: (emoji: string) => Promise<void>;
  onRemoverReacao: () => Promise<void>;
  onResponder?: () => void;
  onEncaminhar?: () => void;
};

export function textoCopiavelDaMensagem(mensagem: MensagemResposta): string | null {
  const conteudo = mensagem.conteudo?.trim();
  if (conteudo) return mensagem.conteudo;
  const metadados = metadadosDaMidia(mensagem.midiaMetadados);
  const legenda = metadados.legenda?.trim();
  return legenda ? metadados.legenda! : null;
}

/** Texto, imagem, áudio, vídeo ou documento — a bolha renderiza os tipos que o backend entrega. */
export function BolhaMensagem({
  mensagem,
  leadId,
  onReenviar,
  nomeDoRemetente,
  onDefinirReacao,
  onRemoverReacao,
  onResponder,
  onEncaminhar,
}: Props) {
  const catalogo = useTextos().atendimentos;
  const textos = catalogo.media;
  const [visualizadorAberto, setVisualizadorAberto] = useState(false);
  const doAtendente = mensagem.remetenteTipo !== "LEAD";
  const metadados = metadadosDaMidia(mensagem.midiaMetadados);
  const opcoes = opcoesInterativas(mensagem.opcoes);
  const midiaUrl = urlSegura(mensagem.midiaUrl);
  const itemDoVisualizador = itemDaBolha(leadId, mensagem, metadados);
  const podeAbrir = Boolean(itemDoVisualizador);
  const hora = new Date(mensagem.enviadoEm).toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });

  return (
    <>
    <InteracaoMensagem
      alinhadaADireita={doAtendente}
      textoCopiavel={textoCopiavelDaMensagem(mensagem)}
      reacoes={mensagem.reacoes ?? []}
      textos={catalogo.mensagem.acoes}
      onDefinirReacao={onDefinirReacao}
      onRemoverReacao={onRemoverReacao}
      onResponder={onResponder}
      onEncaminhar={onEncaminhar}
    >
      <div
        className={cn(
          "w-fit max-w-full rounded-2xl px-3.5 py-3 text-sm font-normal",
          doAtendente
            ? "rounded-tr-md bg-primary text-primary-foreground"
            : "rounded-tl-md border border-border bg-muted text-foreground shadow-sm",
        )}
      >
        {doAtendente && nomeDoRemetente && (
          <p className="mb-1 text-xs font-bold text-primary-foreground/80">
            {nomeDoRemetente}
          </p>
        )}

        {mensagem.citacao && (
          <CitacaoMensagemVisual citacao={mensagem.citacao} textos={catalogo.mensagem.citacao} />
        )}

        {mensagem.tipo === "IMAGEM" && (
          <div className="space-y-1.5 rounded-lg border border-border bg-background/50 p-1.5 shadow-sm">
            {midiaUrl && (
              podeAbrir ? (
                <button
                  type="button"
                  className="block w-full cursor-pointer"
                  aria-label={preencher(textos.visualizador.abrirMidia, { nome: metadados.nome ?? metadados.legenda ?? textos.imagem })}
                  onClick={() => setVisualizadorAberto(true)}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element -- mídia externa do provedor, sem domínio fixo para o loader do Next */}
                  <img
                    src={midiaUrl}
                    alt={metadados.legenda ?? textos.imagem}
                    className="max-h-64 w-full rounded-md object-cover"
                  />
                </button>
              ) : (
                // eslint-disable-next-line @next/next/no-img-element -- mídia externa do provedor, sem domínio fixo para o loader do Next
                <img
                  src={midiaUrl}
                  alt={metadados.legenda ?? textos.imagem}
                  className="max-h-64 w-full rounded-md object-cover"
                />
              )
            )}
            {metadados.legenda && <p>{metadados.legenda}</p>}
          </div>
        )}

        {mensagem.tipo === "AUDIO" &&
          (midiaUrl ? (
            <PlayerAudio
              src={midiaUrl}
              rotulo={textos.audio}
              reproduzir={textos.reproduzir}
              pausar={textos.pausar}
              posicao={textos.posicao}
            />
          ) : (
            <p>{textos.audio}</p>
          ))}

        {mensagem.tipo === "VIDEO" && (
          <div className="space-y-1.5 rounded-lg border border-border bg-background/50 p-1.5 shadow-sm">
            {midiaUrl ? (
              <div className="relative">
                <video
                  controls
                  preload="metadata"
                  src={midiaUrl}
                  aria-label={metadados.nome ?? textos.visualizador.video}
                  className="max-h-64 w-full rounded-md object-contain"
                />
                {podeAbrir && (
                  <button
                    type="button"
                    className="absolute right-2 top-2 inline-flex size-8 items-center justify-center rounded-md bg-background/80 text-foreground shadow-sm backdrop-blur-sm hover:bg-background"
                    aria-label={preencher(textos.visualizador.abrirMidia, {
                      nome: metadados.nome ?? textos.visualizador.video,
                    })}
                    onClick={() => setVisualizadorAberto(true)}
                  >
                    <Maximize2 className="size-4" aria-hidden />
                  </button>
                )}
              </div>
            ) : (
              <p>{textos.visualizador.video}</p>
            )}
            {metadados.legenda && <p>{metadados.legenda}</p>}
          </div>
        )}

        {mensagem.tipo === "DOCUMENTO" && (
          <button
            type="button"
            disabled={!podeAbrir}
            onClick={() => podeAbrir && setVisualizadorAberto(true)}
            title={preencher(textos.visualizador.abrirMidia, { nome: metadados.nome ?? textos.documento })}
            aria-label={preencher(textos.visualizador.abrirMidia, { nome: metadados.nome ?? textos.documento })}
            className="flex min-w-64 items-center gap-3 rounded-lg bg-background/10 p-2.5 text-left"
          >
            <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-background/15">
              <FileText className="size-5" aria-hidden />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-semibold">
                {metadados.nome ?? textos.documento}
              </span>
              {metadados.tamanho !== undefined && (
                <span className="block text-xs opacity-75">
                  {tamanhoLegivel(metadados.tamanho)}
                </span>
              )}
              {metadados.legenda && (
                <span className="mt-0.5 block text-xs opacity-85">
                  {metadados.legenda}
                </span>
              )}
            </span>
          </button>
        )}

        {mensagem.tipo === "TEXTO" && (
          <p className="whitespace-pre-wrap break-words">{mensagem.conteudo}</p>
        )}

        {(mensagem.tipo === "BOTOES" || mensagem.tipo === "LISTA") && (
          <div className="space-y-2">
            {mensagem.conteudo && (
              <p className="whitespace-pre-wrap break-words">{mensagem.conteudo}</p>
            )}
            <p className="text-xs font-semibold opacity-80">
              {mensagem.tipo === "BOTOES" ? textos.botoes : textos.lista}
            </p>
            <div className="space-y-1.5" aria-label={mensagem.tipo === "BOTOES" ? textos.botoes : textos.lista}>
              {opcoes.map((opcao, indice) => (
                <div key={opcao.id ?? `${opcao.titulo}-${indice}`} className="rounded-md bg-background/10 px-2.5 py-1.5">
                  <div className="font-medium">{opcao.titulo ?? opcao.id}</div>
                  {opcao.descricao && <div className="text-xs opacity-75">{opcao.descricao}</div>}
                </div>
              ))}
            </div>
          </div>
        )}

        <div
          className={cn(
            "mt-1 flex items-center gap-1.5 text-[0.7rem]",
            doAtendente
              ? "justify-end text-primary-foreground/70"
              : "text-muted-foreground",
          )}
        >
          <span>{hora}</span>
          {doAtendente && (
            <StatusEntregaIcone
              status={mensagem.statusEntrega}
              onReenviar={onReenviar}
            />
          )}
        </div>
      </div>
    </InteracaoMensagem>
    {itemDoVisualizador && (
      <VisualizadorMidia
        aberto={visualizadorAberto}
        onFechar={() => setVisualizadorAberto(false)}
        itens={[itemDoVisualizador]}
        indice={0}
      />
    )}
    </>
  );
}

function preencher(modelo: string, valores: Record<string, string>): string {
  return Object.entries(valores).reduce(
    (texto, [chave, valor]) => texto.replaceAll(`{${chave}}`, valor),
    modelo,
  );
}

function itemDaBolha(
  leadId: string | undefined,
  mensagem: MensagemResposta,
  metadados: MidiaMetadados,
): ItemDoVisualizador | null {
  if (!leadId) return null;
  if (
    mensagem.tipo !== "IMAGEM" &&
    mensagem.tipo !== "DOCUMENTO" &&
    mensagem.tipo !== "AUDIO" &&
    mensagem.tipo !== "VIDEO"
  ) {
    return null;
  }
  return {
    id: mensagem.id,
    nome: metadados.nome ?? metadados.legenda ?? null,
    mimetype: metadados.mimetype ?? null,
    tamanho: metadados.tamanho ?? null,
    enviadoEm: mensagem.enviadoEm,
    tipoMensagem: mensagem.tipo,
    origem: { tipo: "mensagem", leadId, mensagemId: mensagem.id },
  };
}

function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
