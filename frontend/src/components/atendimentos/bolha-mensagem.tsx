"use client";

import { Download, FileText } from "lucide-react";

import { useTextos } from "@/lib/config/textos-provider";
import { cn, urlSegura } from "@/lib/utils";
import type { MensagemResposta } from "@/lib/atendimento/types";

import { StatusEntregaIcone } from "./status-entrega";

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
  onReenviar?: () => void;
  nomeDoRemetente?: string | null;
};

/** Texto, imagem, áudio ou documento — a bolha renderiza os quatro tipos que o backend já entrega. */
export function BolhaMensagem({
  mensagem,
  onReenviar,
  nomeDoRemetente,
}: Props) {
  const textos = useTextos().atendimentos.media;
  const doAtendente = mensagem.remetenteTipo !== "LEAD";
  const metadados = metadadosDaMidia(mensagem.midiaMetadados);
  const opcoes = opcoesInterativas(mensagem.opcoes);
  const midiaUrl = urlSegura(mensagem.midiaUrl);
  const hora = new Date(mensagem.enviadoEm).toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });

  return (
    <div className={cn("flex", doAtendente ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[70%] rounded-lg px-3.5 py-3 text-sm",
          doAtendente
            ? "bg-primary text-primary-foreground"
            : "min-w-[12rem] bg-muted text-foreground shadow-sm",
        )}
      >
        {doAtendente && nomeDoRemetente && (
          <p className="mb-1 text-xs font-bold text-primary-foreground/80">
            {nomeDoRemetente}
          </p>
        )}

        {mensagem.tipo === "IMAGEM" && (
          <div className="space-y-1.5 rounded-lg border border-border bg-background/50 p-1.5 shadow-sm">
            {midiaUrl && (
              // eslint-disable-next-line @next/next/no-img-element -- mídia externa do provedor, sem domínio fixo para o loader do Next
              <img
                src={midiaUrl}
                alt={metadados.legenda ?? textos.imagem}
                className="max-h-64 w-full rounded-md object-cover"
              />
            )}
            {metadados.legenda && <p>{metadados.legenda}</p>}
          </div>
        )}

        {mensagem.tipo === "AUDIO" &&
          (midiaUrl ? (
            <audio controls src={midiaUrl} className="max-w-full" />
          ) : (
            <p>{textos.audio}</p>
          ))}

        {mensagem.tipo === "DOCUMENTO" && (
          <a
            href={midiaUrl ?? "#"}
            target="_blank"
            rel="noopener noreferrer"
            title={textos.baixar}
            aria-label={`${textos.baixar}: ${metadados.nome ?? textos.documento}`}
            className="flex min-w-64 items-center gap-3 rounded-lg bg-background/10 p-2.5 no-underline"
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
            <Download className="size-4 shrink-0" aria-hidden />
          </a>
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
    </div>
  );
}

function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
