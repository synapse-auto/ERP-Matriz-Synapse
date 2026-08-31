"use client";

import { useState } from "react";
import { Download, FileText, Image as ImageIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { emitirUrlAssinadaDaMidia } from "@/lib/lead/api";
import type { MidiaDoLead } from "@/lib/lead/types";
import { useMidiasDoLead } from "@/lib/lead/use-painel-lead";
import { useTextos } from "@/lib/config/textos-provider";
import { baixarUrlAssinada } from "@/lib/midia/baixar-url-assinada";

import { MidiaComUrlAssinada } from "./midia-com-url-assinada";

export function ListaDeMidiasDoLead({ leadId }: { leadId: string }) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.painel;
  const midias = useMidiasDoLead(leadId);
  const itens = midias.data?.pages.flat() ?? [];

  if (midias.isLoading) {
    return <p className="p-2 text-xs text-muted-foreground">{textos.carregandoMidias}</p>;
  }
  if (midias.isError) {
    return (
      <p role="alert" className="p-2 text-xs text-destructive">
        {textos.erroMidias ?? textos.erroOperacao}
      </p>
    );
  }
  if (itens.length === 0) {
    return (
      <p className="p-2 text-center text-xs text-muted-foreground">
        {textos.vazioMidias ?? textos.vazioLembretes}
      </p>
    );
  }
  return (
    <div className="space-y-1.5">
      {itens.map((item) => (
        <ItemDeMidia key={item.mensagemId} leadId={leadId} item={item} />
      ))}
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
    </div>
  );
}

export function ItemDeMidia({ leadId, item }: { leadId: string; item: MidiaDoLead }) {
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
        <span className="min-w-0 flex-1 truncate text-xs font-medium">{item.nome ?? item.tipo}</span>
        <BotaoBaixarMidia leadId={leadId} mensagemId={item.mensagemId} rotulo={rotuloBaixar} />
      </div>
      <p className="mt-1 text-[0.65rem] text-muted-foreground">
        {item.tipo}
        {item.origem ? ` · ${textos.origemMidia}: ${item.origem}` : ""}
        {item.tamanho ? ` · ${Math.ceil(item.tamanho / 1024)} KB` : ""} ·{" "}
        {new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(item.enviadoEm))}
      </p>
      {(imagem || audio) && (
        <MidiaComUrlAssinada
          leadId={leadId}
          mensagemId={item.mensagemId}
          tipo={imagem ? "IMAGEM" : "AUDIO"}
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
  rotulo,
}: {
  leadId: string;
  mensagemId: string;
  rotulo: string;
}) {
  const [pendente, setPendente] = useState(false);

  async function baixar() {
    setPendente(true);
    try {
      const { url } = await emitirUrlAssinadaDaMidia(leadId, mensagemId);
      baixarUrlAssinada(url);
    } finally {
      setPendente(false);
    }
  }

  return (
    <button
      type="button"
      className="inline-flex size-7 items-center justify-center rounded-md hover:bg-muted"
      aria-label={rotulo}
      disabled={pendente}
      onClick={() => void baixar()}
    >
      <Download className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
    </button>
  );
}
