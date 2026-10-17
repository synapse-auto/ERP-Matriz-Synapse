"use client";

import { useRef, useState } from "react";
import { Download, FileText, Image as ImageIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { apiFetchArquivo } from "@/lib/api/http-client";
import type { MidiaDoLead } from "@/lib/lead/types";
import { TIPOS_MIDIAS_DA_FICHA, TIPOS_DOCUMENTOS_DA_FICHA, useMidiasDoLead } from "@/lib/lead/use-painel-lead";
import { useTextos } from "@/lib/config/textos-provider";
import { baixarBlobComoArquivo } from "@/lib/midia/baixar-arquivo";

import { MidiaComUrlAssinada } from "./midia-com-url-assinada";
import { VisualizadorMidia, type ItemDoVisualizador } from "./visualizador-midia";

export function ListaDeMidiasDoLead({ leadId, categoria = "midias" }: {
  leadId: string;
  categoria?: "midias" | "documentos";
}) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.painel;
  const vis = catalogo.atendimentos.media.visualizador;
  const midias = useMidiasDoLead(leadId,
    categoria === "midias" ? TIPOS_MIDIAS_DA_FICHA : TIPOS_DOCUMENTOS_DA_FICHA);
  const itens = midias.data?.pages.flat() ?? [];
  const [mensagemAberta, setMensagemAberta] = useState<string | null>(null);

  if (midias.isLoading) {
    return <p role="status" className="p-2 text-xs text-muted-foreground">{textos.carregandoArquivos}</p>;
  }
  if (midias.isError && itens.length === 0) {
    return (
      <p role="alert" className="p-2 text-xs text-destructive">
        {textos.erroArquivos}
      </p>
    );
  }
  if (itens.length === 0) {
    return (
      <p className="p-2 text-center text-xs text-muted-foreground">
        {categoria === "midias" ? textos.vazioMidiasDaFicha : textos.vazioDocumentos}
      </p>
    );
  }

  const disponiveis = itens.filter((item) => !item.indisponivel);
  const indiceAberto = disponiveis.findIndex((item) => item.mensagemId === mensagemAberta);
  const itensDoVisualizador: ItemDoVisualizador[] = disponiveis.map((item) => ({
    id: item.mensagemId,
    nome: item.nome,
    mimetype: item.mimetype,
    tamanho: item.tamanho,
    enviadoEm: item.enviadoEm,
    tipoMensagem: item.tipo,
    origem: { tipo: "mensagem", leadId, mensagemId: item.mensagemId },
  }));

  return (
    <div className="space-y-1.5">
      {itens.map((item) => (
        <ItemDeMidia
          key={item.mensagemId}
          leadId={leadId}
          item={item}
          rotuloAbrir={vis.abrirMidia.replace("{nome}", item.nome ?? item.tipo)}
          onAbrir={() => setMensagemAberta(item.mensagemId)}
        />
      ))}
      {midias.isError && <p role="alert" className="p-2 text-xs text-destructive">{textos.erroArquivos}</p>}
      {midias.hasNextPage && (
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="w-full"
          onClick={() => void midias.fetchNextPage()}
          disabled={midias.isFetchingNextPage}
        >
          {textos.carregarMaisMidias ?? textos.adicionar}
        </Button>
      )}
      {indiceAberto >= 0 && (
        <VisualizadorMidia
          aberto
          itens={itensDoVisualizador}
          indice={indiceAberto}
          onIndiceChange={(indice) => setMensagemAberta(disponiveis[indice]?.mensagemId ?? null)}
          onFechar={() => setMensagemAberta(null)}
        />
      )}
    </div>
  );
}

export function ItemDeMidia({
  leadId,
  item,
  rotuloAbrir,
  onAbrir,
}: {
  leadId: string;
  item: MidiaDoLead;
  rotuloAbrir: string;
  onAbrir: () => void;
}) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.painel;
  const imagem = item.tipo === "IMAGEM";
  const audio = item.tipo === "AUDIO";
  const rotuloBaixar = `${imagem ? textos.salvarImagem : catalogo.atendimentos.media.baixar}: ${item.nome ?? item.tipo}`;
  return (
    <div className="rounded-lg border border-border bg-muted/30 p-2">
      <div className="flex items-center gap-2">
        {imagem ? (
          <ImageIcon className="size-(--tamanho-icone-interface) text-primary" aria-hidden />
        ) : (
          <FileText className="size-(--tamanho-icone-interface) text-muted-foreground" aria-hidden />
        )}
        {item.indisponivel ? <p className="min-w-0 flex-1 break-words text-xs font-medium">{item.nome ?? item.tipo}</p> : <button
          type="button"
          className="min-w-0 flex-1 truncate text-left text-xs font-medium"
          aria-label={rotuloAbrir}
          onClick={onAbrir}
        >
          {item.nome ?? item.tipo}
        </button>}
        {!item.indisponivel && <BotaoBaixarMidia leadId={leadId} mensagemId={item.mensagemId} nome={item.nome ?? item.tipo} rotulo={rotuloBaixar} />}
      </div>
      <p className="mt-1 text-[0.65rem] text-muted-foreground">
        {item.tipo}
        {item.origem ? ` · ${textos.origemMidia}: ${item.origem}` : ""}
        {item.tamanho ? ` · ${Math.ceil(item.tamanho / 1024)} KB` : ""} ·{" "}
        {new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(item.enviadoEm))}
      </p>
      {item.indisponivel && <p role="status" className="mt-1 text-xs text-destructive">{catalogo.atendimentos.media.midiaNaoRecebida}</p>}
      {!item.indisponivel && imagem && (
        <button type="button" className="block w-full cursor-pointer text-left" onClick={onAbrir} aria-label={rotuloAbrir}>
          <MidiaComUrlAssinada
            leadId={leadId}
            mensagemId={item.mensagemId}
            tipo="IMAGEM"
            alt={item.legenda ?? item.nome ?? catalogo.atendimentos.media.imagem}
            rotuloAudio={catalogo.atendimentos.media.audio}
          />
        </button>
      )}
      {!item.indisponivel && audio && (
        <MidiaComUrlAssinada
          leadId={leadId}
          mensagemId={item.mensagemId}
          tipo="AUDIO"
          alt={item.legenda ?? item.nome ?? catalogo.atendimentos.media.imagem}
          rotuloAudio={catalogo.atendimentos.media.audio}
        />
      )}
    </div>
  );
}

export function BotaoBaixarMidia({
  leadId,
  mensagemId,
  nome,
  rotulo,
}: {
  leadId: string;
  mensagemId: string;
  nome: string;
  rotulo: string;
}) {
  const catalogo = useTextos();
  const [pendente, setPendente] = useState(false);
  const [erro, setErro] = useState(false);
  const emCurso = useRef(false);

  async function baixar() {
    if (emCurso.current) return;
    emCurso.current = true;
    setPendente(true);
    setErro(false);
    try {
      const arquivo = await apiFetchArquivo(`/api/v1/leads/${encodeURIComponent(leadId)}/midias/${encodeURIComponent(mensagemId)}/download`);
      baixarBlobComoArquivo(arquivo.blob, arquivo.nome ?? nome);
    } catch {
      setErro(true);
    } finally {
      emCurso.current = false;
      setPendente(false);
    }
  }

  return (
    <>
    <button
      type="button"
      className="inline-flex size-7 items-center justify-center rounded-md hover:bg-muted"
      aria-label={rotulo}
      disabled={pendente}
      onClick={() => void baixar()}
    >
      <Download className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
    </button>
    {erro && <span role="alert" className="text-xs text-destructive">{catalogo.atendimentos.media.visualizador.erroAoCarregar}</span>}
    </>
  );
}
