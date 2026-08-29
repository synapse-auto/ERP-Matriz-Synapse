"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { LayoutTemplate } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import type { TomDePill } from "@/components/ui/pill-de-status";
import { Seletor } from "@/components/ui/seletor";
import { Textarea } from "@/components/ui/textarea";
import { criarTemplateWhatsApp, listarTemplatesWhatsApp } from "@/lib/atendimento/api";
import {
  analisarVariaveisDoCorpo,
  interpolarCatalogo,
  rotulosDasVariaveis,
} from "@/lib/atendimento/variaveis-do-template";
import type {
  CategoriaTemplateWhatsApp,
  StatusTemplateWhatsApp,
} from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

const TOM_DO_STATUS: Record<StatusTemplateWhatsApp, TomDePill> = {
  APROVADO: "sucesso",
  PENDENTE: "atencao",
  REJEITADO: "erro",
  PAUSADO: "neutro",
  DESCONHECIDO: "neutro",
};

export function PaginaTemplatesWhatsApp() {
  const t = useTextos().templatesWhatsApp;
  const cache = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  const consulta = useQuery({
    queryKey: ["whatsapp-templates"],
    queryFn: listarTemplatesWhatsApp,
    retry: 1,
  });
  const criar = useMutation({
    mutationFn: criarTemplateWhatsApp,
    onSuccess: (criado) => {
      void cache.invalidateQueries({ queryKey: ["whatsapp-templates"] });
      setAberto(false);
      if (criado.status !== "APROVADO") {
        setAviso(t.avisoPendente);
      }
    },
  });

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <Button onClick={() => setAberto(true)}>{t.novo}</Button>
      </header>

      <div className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/15 p-3">
        <LayoutTemplate className="size-5 shrink-0 text-primary" />
        <p className="text-sm text-primary">{t.dica}</p>
      </div>

      {aviso && (
        <p className="rounded-lg border border-cor-atencao/40 bg-cor-atencao/10 px-3 py-2 text-sm text-cor-atencao">
          {aviso}
        </p>
      )}

      {consulta.isLoading ? (
        <p>{t.carregando}</p>
      ) : consulta.isError ? (
        <ErroDeCarregamento
          mensagem={t.erro}
          onTentarNovamente={() => consulta.refetch()}
        />
      ) : !consulta.data?.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <ul className="space-y-2">
          {consulta.data.map((template) => (
            <li
              key={`${template.nome}:${template.idioma}`}
              className="rounded-xl border border-border bg-card p-4 shadow-sm"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-semibold">{template.nome}</p>
                <div className="flex gap-1.5">
                  <PillDeStatus tom="info">
                    {t.categorias[template.categoria]}
                  </PillDeStatus>
                  <PillDeStatus tom={TOM_DO_STATUS[template.status]}>
                    {t.status[template.status]}
                  </PillDeStatus>
                </div>
              </div>
              <p className="mt-2 text-sm text-muted-foreground">{template.corpo}</p>
              <p className="mt-1 text-xs text-muted-foreground">{template.idioma}</p>
            </li>
          ))}
        </ul>
      )}

      <FormularioTemplate
        aberto={aberto}
        salvando={criar.isPending}
        erro={
          criar.isError
            ? criar.error instanceof Error && criar.error.message
              ? criar.error.message
              : t.formulario.erro
            : null
        }
        textos={t}
        onFechar={() => setAberto(false)}
        onSalvar={(pedido) => criar.mutate(pedido)}
      />
    </div>
  );
}

function FormularioTemplate({
  aberto,
  salvando,
  erro,
  textos,
  onFechar,
  onSalvar,
}: {
  aberto: boolean;
  salvando: boolean;
  erro: string | null;
  textos: ReturnType<typeof useTextos>["templatesWhatsApp"];
  onFechar: () => void;
  onSalvar: (pedido: {
    nome: string;
    idioma: string;
    categoria: CategoriaTemplateWhatsApp;
    corpo: string;
  }) => void;
}) {
  const [nome, setNome] = useState("");
  const [idioma, setIdioma] = useState("pt_BR");
  const [categoria, setCategoria] = useState<CategoriaTemplateWhatsApp>("UTILIDADE");
  const [corpo, setCorpo] = useState("");
  const analise = analisarVariaveisDoCorpo(corpo);
  const ajudaDasVariaveis = analise.erro
    ? interpolarCatalogo(
        analise.erro.tipo === "ausente"
          ? textos.formulario.variavelAusente
          : textos.formulario.variavelInvalida,
        { marcador: `{{${analise.erro.indice}}}` },
      )
    : analise.indices.length > 0
      ? interpolarCatalogo(textos.formulario.variaveisDetectadas, {
          lista: rotulosDasVariaveis(analise.indices),
        })
      : null;

  return (
    <Dialog open={aberto} onOpenChange={(abertoAgora) => !abertoAgora && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.formulario.criarTitulo}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={(evento) => {
            evento.preventDefault();
            if (analise.erro) {
              return;
            }
            onSalvar({ nome, idioma, categoria, corpo });
          }}
        >
          <label className="block text-sm" htmlFor="template-whatsapp-nome">
            {textos.formulario.nome}
            <Input
              id="template-whatsapp-nome"
              className="mt-1"
              value={nome}
              onChange={(evento) => setNome(evento.target.value)}
              required
            />
            <span className="mt-1 block text-xs text-muted-foreground">
              {textos.formulario.nomeAjuda}
            </span>
          </label>
          <label className="block text-sm">
            {textos.formulario.idioma}
            <Input
              className="mt-1"
              value={idioma}
              onChange={(evento) => setIdioma(evento.target.value)}
              required
            />
          </label>
          <label className="block text-sm">
            {textos.formulario.categoria}
            <Seletor
              className="mt-1"
              valor={categoria}
              placeholder={textos.formulario.categoria}
              opcoes={[
                { valor: "UTILIDADE", rotulo: textos.categorias.UTILIDADE },
                { valor: "MARKETING", rotulo: textos.categorias.MARKETING },
              ]}
              onChange={(valor) => setCategoria(valor as CategoriaTemplateWhatsApp)}
            />
          </label>
          <label className="block text-sm" htmlFor="template-whatsapp-corpo">
            {textos.formulario.corpo}
            <Textarea
              id="template-whatsapp-corpo"
              className="mt-1"
              value={corpo}
              onChange={(evento) => setCorpo(evento.target.value)}
              required
            />
            <span className="mt-1 block text-xs text-muted-foreground">
              {ajudaDasVariaveis ? `${ajudaDasVariaveis}. ` : null}
              {textos.formulario.corpoAjuda}
            </span>
          </label>
          {(analise.erro || erro) && (
            <p role="alert" className="text-sm text-destructive">
              {analise.erro ? ajudaDasVariaveis : erro}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar}>
              {textos.formulario.cancelar}
            </Button>
            <Button type="submit" disabled={salvando || Boolean(analise.erro)}>
              {textos.formulario.salvar}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
