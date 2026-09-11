"use client";

import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Download, FileUp, Upload } from "lucide-react";

import {
  confirmarImportacaoLeads,
  visualizarImportacaoLeads,
  type ResultadoImportacaoLeads,
} from "@/lib/agenda/api";
import type { Textos } from "@/lib/config/schema";
import { cn } from "@/lib/utils";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

type TextosImportacao = Textos["agenda"]["importacao"];

interface DialogoImportacaoLeadsProps {
  aberto: boolean;
  onAbertoChange: (aberto: boolean) => void;
  textos: TextosImportacao;
}

function nomeDoArquivo(arquivo: File): string {
  return arquivo.name.toLocaleLowerCase().endsWith(".csv") ? arquivo.name : `${arquivo.name}.csv`;
}

function mensagemDoErro(erro: unknown, fallback: string): string {
  return erro instanceof Error && erro.message ? erro.message : fallback;
}

function LinhaDaPrevie({ titulo, valor }: { titulo: string; valor: number }) {
  return (
    <div className="rounded-lg border border-border bg-muted/30 px-3 py-2">
      <p className="text-xs text-muted-foreground">{titulo}</p>
      <p className="text-lg font-semibold text-foreground">{valor}</p>
    </div>
  );
}

export function DialogoImportacaoLeads({
  aberto,
  onAbertoChange,
  textos,
}: DialogoImportacaoLeadsProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [previa, setPrevia] = useState<ResultadoImportacaoLeads | null>(null);
  const [arrastando, setArrastando] = useState(false);
  const [erroArquivo, setErroArquivo] = useState<string | null>(null);

  const preview = useMutation({
    mutationFn: visualizarImportacaoLeads,
    onSuccess: setPrevia,
  });
  const confirmar = useMutation({
    mutationFn: confirmarImportacaoLeads,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["agenda"] });
      fechar();
    },
  });

  function escolher(novoArquivo: File | undefined) {
    if (!novoArquivo) return;
    if (!novoArquivo.name.toLocaleLowerCase().endsWith(".csv") && novoArquivo.type !== "text/csv") {
      setArquivo(null);
      setPrevia(null);
      setErroArquivo(textos.arquivoInvalido);
      preview.reset();
      confirmar.reset();
      return;
    }
    setArquivo(novoArquivo);
    setPrevia(null);
    setErroArquivo(null);
    preview.reset();
    confirmar.reset();
    preview.mutate(novoArquivo);
  }

  function fechar() {
    if (preview.isPending || confirmar.isPending) return;
    onAbertoChange(false);
    setArquivo(null);
    setPrevia(null);
    setErroArquivo(null);
    preview.reset();
    confirmar.reset();
  }

  function baixarModelo() {
    const blob = new Blob(["nome,empresa,telefone,cnpj/cpf,cidade,etapa,tags\n"], {
      type: "text/csv;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = textos.modeloArquivo;
    link.click();
    URL.revokeObjectURL(url);
  }

  function confirmarImportacao() {
    if (arquivo && previa && previa.validas > 0) confirmar.mutate(arquivo);
  }

  const erro = erroArquivo ?? (preview.isError
    ? mensagemDoErro(preview.error, textos.erro)
    : confirmar.isError
      ? mensagemDoErro(confirmar.error, textos.erroImportar)
      : null);

  return (
    <Dialog open={aberto} onOpenChange={(novoEstado) => (novoEstado ? onAbertoChange(true) : fechar())}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{textos.titulo}</DialogTitle>
          <DialogDescription>{textos.descricao}</DialogDescription>
        </DialogHeader>

        <div
          role="button"
          tabIndex={0}
          className={cn(
            "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-border bg-muted/20 p-6 text-center transition-colors",
            arrastando && "border-primary bg-primary/5",
          )}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(evento) => {
            if (evento.key === "Enter" || evento.key === " ") inputRef.current?.click();
          }}
          onDragEnter={(evento) => {
            evento.preventDefault();
            setArrastando(true);
          }}
          onDragOver={(evento) => evento.preventDefault()}
          onDragLeave={() => setArrastando(false)}
          onDrop={(evento) => {
            evento.preventDefault();
            setArrastando(false);
            escolher(evento.dataTransfer.files.item(0) ?? undefined);
          }}
        >
          <Upload className="text-muted-foreground" aria-hidden="true" />
          <p className="text-sm font-medium text-foreground">{textos.arraste}</p>
          <p className="text-xs text-muted-foreground">{textos.colunas}</p>
          <Button type="button" variant="outline" size="sm" onClick={(evento) => { evento.stopPropagation(); inputRef.current?.click(); }}>
            <FileUp aria-hidden="true" />
            {textos.selecionarArquivo}
          </Button>
          <input
            ref={inputRef}
            className="sr-only"
            type="file"
            accept=".csv,text/csv"
            onChange={(evento) => escolher(evento.target.files?.[0])}
          />
        </div>

        {arquivo && <p className="truncate text-sm text-foreground">{nomeDoArquivo(arquivo)}</p>}
        {preview.isPending && <p className="text-sm text-muted-foreground">{textos.preview}...</p>}
        {erro && <p role="alert" className="text-sm text-destructive">{erro}</p>}

        {previa && (
          <section aria-label={textos.preview} className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">{textos.preview}</h2>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <LinhaDaPrevie titulo={textos.linhas} valor={previa.totalDeLinhas} />
              <LinhaDaPrevie titulo={textos.validas} valor={previa.validas} />
              <LinhaDaPrevie titulo={textos.jaExistiam} valor={previa.jaExistiam} />
              <LinhaDaPrevie titulo={textos.recusadas} valor={previa.recusadas.length} />
            </div>
            {previa.recusadas.length > 0 && (
              <ul className="max-h-28 space-y-1 overflow-y-auto rounded-lg border border-border p-2 text-xs text-muted-foreground">
                {previa.recusadas.map((recusa) => <li key={`${recusa.linha}-${recusa.motivo}`}>{recusa.linha}: {recusa.motivo}</li>)}
              </ul>
            )}
          </section>
        )}

        <Button type="button" variant="link" className="w-fit px-0" onClick={baixarModelo}>
          <Download aria-hidden="true" />
          {textos.baixarModelo}
        </Button>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={fechar} disabled={preview.isPending || confirmar.isPending}>
            {textos.cancelar}
          </Button>
          <Button type="button" onClick={confirmarImportacao} disabled={!previa || previa.validas === 0 || confirmar.isPending}>
            {confirmar.isPending ? textos.importando : textos.importar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
