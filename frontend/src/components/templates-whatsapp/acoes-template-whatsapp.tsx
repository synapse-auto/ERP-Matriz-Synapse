"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import type { TemplateWhatsApp } from "@/lib/atendimento/types";
import {
  analisarVariaveisDoCorpo,
  interpolarCatalogo,
} from "@/lib/atendimento/variaveis-do-template";
import type { Textos } from "@/lib/config/schema";

type TextosTemplatesWhatsApp = Textos["templatesWhatsApp"];

export function FormularioEdicaoTemplate({
  template,
  salvando,
  erro,
  textos,
  onFechar,
  onSalvar,
}: {
  template: TemplateWhatsApp | null;
  salvando: boolean;
  erro: string | null;
  textos: TextosTemplatesWhatsApp;
  onFechar: () => void;
  onSalvar: (corpo: string) => void;
}) {
  const [corpo, setCorpo] = useState(() => template?.corpo ?? "");
  const analise = analisarVariaveisDoCorpo(corpo);

  if (!template) return null;

  return (
    <Dialog open={Boolean(template)} onOpenChange={(abertoAgora) => !abertoAgora && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.formulario.editarTitulo}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={(evento) => {
            evento.preventDefault();
            if (!analise.erro && corpo.trim()) onSalvar(corpo);
          }}
        >
          <p className="text-sm text-muted-foreground">{template?.nome}</p>
          <label className="block text-sm">
            {textos.formulario.corpo}
            <Textarea value={corpo} onChange={(evento) => setCorpo(evento.target.value)} className="mt-1" required />
          </label>
          {(analise.erro || erro) && (
            <p role="alert" className="text-sm text-destructive">
              {erro ?? textos.formulario.variavelInvalida}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar}>
              {textos.formulario.cancelar}
            </Button>
            <Button
              type="submit"
              disabled={salvando || Boolean(analise.erro) || !corpo.trim()}
            >
              {textos.formulario.salvarEdicao}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function DialogoConfirmacaoExclusaoTemplate({
  template,
  excluindo,
  textos,
  onFechar,
  onConfirmar,
}: {
  template: TemplateWhatsApp | null;
  excluindo: boolean;
  textos: TextosTemplatesWhatsApp;
  onFechar: () => void;
  onConfirmar: () => void;
}) {
  if (!template) return null;

  return (
    <Dialog open={Boolean(template)} onOpenChange={(abertoAgora) => !abertoAgora && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.confirmacaoExclusao.titulo}</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          {interpolarCatalogo(textos.confirmacaoExclusao.descricao, { nome: template.nome })}
        </p>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onFechar}>
            {textos.confirmacaoExclusao.cancelar}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={excluindo || !template}
            onClick={onConfirmar}
          >
            {textos.confirmacaoExclusao.confirmar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
