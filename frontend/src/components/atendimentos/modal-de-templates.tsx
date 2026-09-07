"use client";

import { useState } from "react";
import Link from "next/link";
import { Pencil, Search, Trash2 } from "lucide-react";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import type { CategoriaTemplateWhatsApp, TemplateWhatsApp } from "@/lib/atendimento/types";
import {
  interpolarCatalogo,
  interpolarPreviaDoTemplate,
  parametrosDoTemplatePreenchidos,
} from "@/lib/atendimento/variaveis-do-template";
import type { Textos } from "@/lib/config/schema";
import { editarTemplateWhatsApp, excluirTemplateWhatsApp } from "@/lib/atendimento/api";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { podeGerenciarTemplates } from "@/lib/navegacao/visibilidade-do-menu";
import { cn } from "@/lib/utils";

import {
  DialogoConfirmacaoExclusaoTemplate,
  FormularioEdicaoTemplate,
} from "../templates-whatsapp/acoes-template-whatsapp";

type TextosComposer = Textos["atendimentos"]["composer"];
type RotulosDeCategoria = Textos["templatesWhatsApp"]["categorias"];
type RotulosDeStatus = Textos["templatesWhatsApp"]["status"];

type Props = {
  aberto: boolean;
  onAbertoChange: (aberto: boolean) => void;
  textos: TextosComposer;
  rotulosDeCategoria?: RotulosDeCategoria;
  rotulosDeStatus?: RotulosDeStatus;
  templates: {
    data?: TemplateWhatsApp[];
    isError: boolean;
    isLoading: boolean;
  };
  parametros: Record<string, string[]>;
  onParametros: (chave: string, valores: string[]) => void;
  enviando: boolean;
  onEnviar: (template: TemplateWhatsApp, valores: string[]) => void;
  onTemplateExcluido?: (template: TemplateWhatsApp) => void;
  templateSelecionado?: string | null;
  rotuloAcao?: string;
};

const ORDEM_DAS_CATEGORIAS: CategoriaTemplateWhatsApp[] = [
  "UTILIDADE",
  "MARKETING",
  "AUTENTICACAO",
];

export function chaveDoTemplate(template: TemplateWhatsApp): string {
  return `${template.nome}:${template.idioma}`;
}

export function ModalDeTemplates({
  aberto,
  onAbertoChange,
  textos,
  rotulosDeCategoria,
  rotulosDeStatus,
  templates,
  parametros,
  onParametros,
  enviando,
  onEnviar,
  onTemplateExcluido,
  templateSelecionado = null,
  rotuloAcao,
}: Props) {
  const [busca, setBusca] = useState("");
  const [tocados, setTocados] = useState<Record<string, boolean>>({});
  const [chaveClicada, setChaveClicada] = useState<string | null | undefined>(undefined);
  const [editando, setEditando] = useState<TemplateWhatsApp | null>(null);
  const [excluindo, setExcluindo] = useState<TemplateWhatsApp | null>(null);
  const papel = useAuthStore((estado) => estado.papel);
  const catalogo = useTextos();
  const textosTemplates = catalogo.templatesWhatsApp;
  const podeGerenciar = podeGerenciarTemplates(papel);
  const cache = useQueryClient();

  const editar = useMutation({
    mutationFn: (pedido: { id: string; corpo: string }) =>
      editarTemplateWhatsApp(pedido.id, { corpo: pedido.corpo }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["whatsapp-templates"] });
      setEditando(null);
    },
  });
  const excluir = useMutation({
    mutationFn: (template: TemplateWhatsApp) => excluirTemplateWhatsApp(template.id, template.nome),
    onSuccess: (_resultado, template) => {
      void cache.invalidateQueries({ queryKey: ["whatsapp-templates"] });
      if (chaveSelecionada === chaveDoTemplate(template)) {
        setChaveClicada(null);
        onTemplateExcluido?.(template);
      }
      setExcluindo(null);
    },
  });

  const chaveSelecionada = chaveClicada === undefined ? templateSelecionado : chaveClicada;

  const aprovados = (templates.data ?? []).filter((item) => item.status === "APROVADO");
  const filtrados = filtrarTemplates(aprovados, busca, rotulosDeCategoria);
  const grupos = agruparPorCategoria(filtrados);
  const selecionado = aprovados.find((item) => chaveDoTemplate(item) === chaveSelecionada) ?? null;
  const valores = selecionado
    ? (parametros[chaveDoTemplate(selecionado)] ?? Array(selecionado.quantidadeDeParametros).fill(""))
    : [];
  const preenchido = selecionado !== null && parametrosDoTemplatePreenchidos(valores);
  const previa = selecionado
    ? interpolarPreviaDoTemplate(selecionado.corpo, valores, textos.marcadorVariavelVazia)
    : "";

  function confirmar() {
    if (!selecionado || !preenchido) return;
    onEnviar(selecionado, valores);
  }

  return (
    <Dialog open={aberto} onOpenChange={onAbertoChange}>
      <DialogContent className="flex max-h-[85vh] w-[min(100%-2rem,72rem)] max-w-none flex-col gap-0 overflow-hidden p-0 sm:max-w-[72rem]">
        <DialogHeader className="border-b border-border px-4 py-3">
          <DialogTitle>{textos.escolherTemplate}</DialogTitle>
        </DialogHeader>
        <div className="grid min-h-0 flex-1 grid-cols-1 overflow-y-auto lg:grid-cols-3 lg:overflow-hidden lg:min-h-[min(28rem,calc(85vh-9rem))]">
          <section className="flex min-h-0 flex-col gap-3 border-b border-border p-4 lg:border-r lg:border-b-0 lg:overflow-hidden">
            <h3 className="text-[0.65rem] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunaTemplates}
            </h3>
            {templates.isError ? (
              <p className="text-sm text-destructive">{textos.templatesErro}</p>
            ) : templates.isLoading ? (
              <p className="text-sm text-muted-foreground">{textos.templatesCarregando}</p>
            ) : aprovados.length === 0 ? (
              <p className="text-sm text-muted-foreground">{textos.semTemplates}</p>
            ) : (
              <>
                {aprovados.length > 1 && (
                  <div className="relative">
                    <Search className="pointer-events-none absolute left-2.5 top-1/2 size-(--tamanho-icone-interface) -translate-y-1/2 text-muted-foreground" />
                    <Input
                      value={busca}
                      onChange={(evento) => setBusca(evento.target.value)}
                      placeholder={textos.buscaTemplate}
                      className="h-9 bg-muted/40 pl-9"
                      aria-label={textos.buscaTemplate}
                    />
                  </div>
                )}
                {filtrados.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{textos.semResultadosTemplate}</p>
                ) : (
                  <ul className="min-h-0 flex-1 space-y-3 overflow-y-auto">
                    {grupos.map((grupo) => (
                      <li key={grupo.categoria} className="space-y-2">
                        {grupos.length > 1 && rotulosDeCategoria && (
                          <p className="px-0.5 text-[0.65rem] font-bold tracking-wide text-muted-foreground uppercase">
                            {rotulosDeCategoria[grupo.categoria]}
                          </p>
                        )}
                        <ul className="space-y-2">
                          {grupo.itens.map((template) => {
                            const chave = chaveDoTemplate(template);
                            const ativo = chaveSelecionada === chave;
                            const categoria =
                              rotulosDeCategoria?.[template.categoria] ?? template.categoria;
                            const status = rotulosDeStatus?.[template.status] ?? template.status;
                            return (
                              <li key={chave}>
                                <div
                                  className={cn(
                                    "flex rounded-xl border border-border bg-muted/30 transition-colors",
                                    "hover:bg-muted/50",
                                    ativo && "border-primary ring-1 ring-primary/30",
                                  )}
                                  data-active={ativo || undefined}
                                >
                                  <button
                                    type="button"
                                    aria-pressed={ativo}
                                    className={cn(
                                      "min-w-0 flex-1 rounded-l-xl p-3 text-left",
                                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                                    )}
                                    onClick={() => setChaveClicada(chave)}
                                  >
                                    <div className="flex items-start justify-between gap-2">
                                      <p className="text-sm font-medium text-foreground">{template.nome}</p>
                                      <PillDeStatus tom="sucesso">{status}</PillDeStatus>
                                    </div>
                                    <p className="mt-1 text-xs text-muted-foreground">
                                      {template.idioma} · {categoria}
                                    </p>
                                  </button>
                                  {podeGerenciar && template.id && (
                                    <div className="flex shrink-0 items-start gap-1 p-2">
                                      <Button
                                        type="button"
                                        variant="outline"
                                        size="icon"
                                        aria-label={`${textosTemplates.editar}: ${template.nome}`}
                                        title={textosTemplates.editar}
                                        onClick={() => setEditando(template)}
                                      >
                                        <Pencil className="size-(--tamanho-icone-interface)" aria-hidden />
                                      </Button>
                                      <Button
                                        type="button"
                                        variant="outline"
                                        size="icon"
                                        aria-label={`${textosTemplates.excluir}: ${template.nome}`}
                                        title={textosTemplates.excluir}
                                        onClick={() => setExcluindo(template)}
                                      >
                                        <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
                                      </Button>
                                    </div>
                                  )}
                                </div>
                              </li>
                            );
                          })}
                        </ul>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
            <Link
              href="/templates-whatsapp"
              className="inline-flex text-xs font-medium text-primary underline-offset-4 hover:underline"
            >
              {textos.criarTemplate}
            </Link>
          </section>
          <section className="flex min-h-0 flex-col gap-3 border-b border-border p-4 lg:border-r lg:border-b-0 lg:overflow-y-auto">
            <h3 className="text-[0.65rem] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunaConfiguracao}
            </h3>
            {selecionado === null ? (
              <p className="text-sm text-muted-foreground">{textos.configuracaoSemSelecao}</p>
            ) : selecionado.quantidadeDeParametros === 0 ? (
              <p className="text-sm text-muted-foreground">{textos.configuracaoSemVariaveis}</p>
            ) : (
              <div className="space-y-3">
                {valores.map((valor, indice) => {
                  const numero = indice + 1;
                  const chave = chaveDoTemplate(selecionado);
                  const vazio = valor.trim() === "";
                  const invalido = Boolean(tocados[chave]) && vazio;
                  const id = `${selecionado.nome}-${selecionado.idioma}-${numero}`;
                  return (
                    <div key={id}>
                      <label htmlFor={id} className="block text-xs font-medium text-foreground">
                        {interpolarCatalogo(textos.parametroEnvio, { indice: String(numero) })}
                      </label>
                      <Input
                        id={id}
                        className="mt-1"
                        value={valor}
                        aria-invalid={invalido}
                        aria-describedby={invalido ? `${id}-erro` : undefined}
                        placeholder={interpolarCatalogo(textos.parametroEnvio, {
                          indice: String(numero),
                        })}
                        onChange={(evento) => {
                          setTocados((atual) => ({ ...atual, [chave]: true }));
                          const proximo = [...valores];
                          proximo[indice] = evento.target.value;
                          onParametros(chave, proximo);
                        }}
                      />
                      {invalido && (
                        <p id={`${id}-erro`} className="mt-1 text-xs text-destructive" role="alert">
                          {textos.parametroObrigatorio}
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </section>
          <section className="flex min-h-0 flex-col gap-3 p-4 lg:overflow-y-auto">
            <h3 className="text-[0.65rem] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunaPrevia}
            </h3>
            {selecionado === null ? (
              <p className="text-sm text-muted-foreground">{textos.previaSemSelecao}</p>
            ) : (
              <div className="rounded-2xl bg-muted/40 p-4">
                <div className="ml-auto w-fit max-w-[90%] rounded-2xl rounded-tr-md bg-primary px-3.5 py-3 text-sm text-primary-foreground">
                  <p className="whitespace-pre-wrap break-words">{previa}</p>
                </div>
              </div>
            )}
          </section>
        </div>
        <DialogFooter className="mx-0 mb-0">
          <Button type="button" variant="outline" onClick={() => onAbertoChange(false)}>
            {textos.cancelarTemplate}
          </Button>
          <Button type="button" disabled={enviando || !preenchido} onClick={confirmar}>
            {rotuloAcao ?? textos.enviarTemplate}
          </Button>
        </DialogFooter>
      </DialogContent>
      <FormularioEdicaoTemplate
        key={editando?.id ?? "sem-template"}
        template={editando}
        salvando={editar.isPending}
        erro={editar.isError ? textosTemplates.formulario.erroEdicao : null}
        textos={textosTemplates}
        onFechar={() => setEditando(null)}
        onSalvar={(corpo) => editando && editar.mutate({ id: editando.id, corpo })}
      />
      <DialogoConfirmacaoExclusaoTemplate
        template={excluindo}
        excluindo={excluir.isPending}
        textos={textosTemplates}
        onFechar={() => setExcluindo(null)}
        onConfirmar={() => excluindo && excluir.mutate(excluindo)}
      />
    </Dialog>
  );
}

function filtrarTemplates(
  templates: TemplateWhatsApp[],
  busca: string,
  rotulos?: RotulosDeCategoria,
): TemplateWhatsApp[] {
  const termo = busca.trim().toLowerCase();
  if (!termo) {
    return templates;
  }
  return templates.filter((template) => {
    const categoria = rotulos?.[template.categoria] ?? template.categoria;
    return (
      template.nome.toLowerCase().includes(termo)
      || template.corpo.toLowerCase().includes(termo)
      || template.idioma.toLowerCase().includes(termo)
      || categoria.toLowerCase().includes(termo)
    );
  });
}

function agruparPorCategoria(
  templates: TemplateWhatsApp[],
): { categoria: CategoriaTemplateWhatsApp; itens: TemplateWhatsApp[] }[] {
  const porCategoria = new Map<CategoriaTemplateWhatsApp, TemplateWhatsApp[]>();
  for (const template of templates) {
    const itens = porCategoria.get(template.categoria) ?? [];
    itens.push(template);
    porCategoria.set(template.categoria, itens);
  }
  return ORDEM_DAS_CATEGORIAS.filter((categoria) => porCategoria.has(categoria)).map(
    (categoria) => ({ categoria, itens: porCategoria.get(categoria) ?? [] }),
  );
}
