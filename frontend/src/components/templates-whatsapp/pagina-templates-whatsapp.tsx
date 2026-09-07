"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { LayoutTemplate, Pencil, Search, Trash2 } from "lucide-react";

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
import {
  criarTemplateWhatsApp,
  editarTemplateWhatsApp,
  excluirTemplateWhatsApp,
  listarTemplatesWhatsApp,
  obterCapacidadeDoCanal,
} from "@/lib/atendimento/api";
import {
  analisarVariaveisDoCorpo,
  interpolarCatalogo,
  rotulosDasVariaveis,
} from "@/lib/atendimento/variaveis-do-template";
import type {
  CategoriaTemplateWhatsApp,
  StatusTemplateWhatsApp,
  TemplateWhatsApp,
} from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { podeCriarTemplates, podeGerenciarTemplates } from "@/lib/navegacao/visibilidade-do-menu";
import { useAuthStore } from "@/lib/auth/auth-store";

const TOM_DO_STATUS: Record<StatusTemplateWhatsApp, TomDePill> = {
  APROVADO: "sucesso",
  PENDENTE: "atencao",
  REJEITADO: "erro",
  PAUSADO: "neutro",
  DESCONHECIDO: "neutro",
};

const ORDEM_DAS_CATEGORIAS: CategoriaTemplateWhatsApp[] = [
  "UTILIDADE",
  "MARKETING",
  "AUTENTICACAO",
];

export function PaginaTemplatesWhatsApp() {
  const t = useTextos().templatesWhatsApp;
  const papel = useAuthStore((estado) => estado.papel);
  const cache = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const [busca, setBusca] = useState("");
  const [editando, setEditando] = useState<TemplateWhatsApp | null>(null);
  const [excluindo, setExcluindo] = useState<TemplateWhatsApp | null>(null);

  const capacidade = useQuery({
    queryKey: ["capacidade-do-canal"],
    queryFn: obterCapacidadeDoCanal,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

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
  const editarTemplate = useMutation({
    mutationFn: (pedido: { id: string; corpo: string }) => editarTemplateWhatsApp(pedido.id, { corpo: pedido.corpo }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["whatsapp-templates"] });
      setEditando(null);
    },
  });
  const excluirTemplate = useMutation({
    mutationFn: (template: TemplateWhatsApp) => excluirTemplateWhatsApp(template.id, template.nome),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["whatsapp-templates"] });
      setExcluindo(null);
    },
  });

  if (capacidade.isPending) {
    return <p className="p-6">{t.carregando}</p>;
  }
  if (capacidade.data && !capacidade.data.gerenciaTemplates) {
    return null;
  }
  const podeGerenciar = podeGerenciarTemplates(papel);
  const podeCriar = podeCriarTemplates(papel);

  const todos = consulta.data ?? [];
  const filtrados = filtrarTemplates(todos, busca, t.categorias, t.status);
  const grupos = agruparPorCategoria(filtrados);

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {todos.length > 1 && (
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-(--tamanho-icone-interface) -translate-y-1/2 text-muted-foreground" />
              <Input
                value={busca}
                onChange={(evento) => setBusca(evento.target.value)}
                placeholder={t.busca}
                className="w-56 bg-muted/40 pl-9"
                aria-label={t.busca}
              />
            </div>
          )}
          {podeCriar && <Button onClick={() => setAberto(true)}>{t.novo}</Button>}
        </div>
      </header>

      <div className="flex items-center gap-3 rounded-xl border border-border bg-muted/40 p-3">
        <LayoutTemplate className="size-[calc(var(--tamanho-icone-interface)*1.25)] shrink-0 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">{t.dica}</p>
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
      ) : !todos.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : filtrados.length === 0 ? (
        <p className="text-muted-foreground">{t.semResultados}</p>
      ) : (
        <div className="space-y-6">
          {grupos.map((grupo) => (
            <section key={grupo.categoria} className="space-y-2">
              {grupos.length > 1 && (
                <p className="px-0.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">
                  {t.categorias[grupo.categoria]} · {grupo.itens.length}
                </p>
              )}
              <ul className="space-y-2">
                {grupo.itens.map((template) => (
                  <li
                    key={`${template.nome}:${template.idioma}`}
                    className="rounded-xl border border-border bg-card p-4 shadow-sm"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="min-w-0">
                        <p className="font-semibold">{template.nome}</p>
                        <p className="mt-0.5 text-xs text-muted-foreground">{template.idioma}</p>
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {grupos.length === 1 && (
                          <PillDeStatus tom="neutro">{t.categorias[template.categoria]}</PillDeStatus>
                        )}
                        <PillDeStatus tom={TOM_DO_STATUS[template.status]}>
                          {t.status[template.status]}
                        </PillDeStatus>
                        {podeGerenciar && template.id && (
                          <>
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              aria-label={`${t.editar}: ${template.nome}`}
                              title={t.editar}
                              onClick={() => setEditando(template)}
                            >
                              <Pencil className="size-(--tamanho-icone-interface)" aria-hidden />
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              aria-label={`${t.excluir}: ${template.nome}`}
                              title={t.excluir}
                              onClick={() => setExcluindo(template)}
                            >
                              <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
                            </Button>
                          </>
                        )}
                      </div>
                    </div>
                    <p className="mt-3 whitespace-pre-wrap text-sm text-foreground">{template.corpo}</p>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
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
      <FormularioEdicao
        key={editando?.id ?? "sem-template"}
        template={editando}
        salvando={editarTemplate.isPending}
        erro={editarTemplate.isError ? t.formulario.erroEdicao : null}
        textos={t}
        onFechar={() => setEditando(null)}
        onSalvar={(corpo) => editando && editarTemplate.mutate({ id: editando.id, corpo })}
      />
      <Dialog open={Boolean(excluindo)} onOpenChange={(abertoAgora) => !abertoAgora && setExcluindo(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t.confirmacaoExclusao.titulo}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {excluindo
              ? interpolarCatalogo(t.confirmacaoExclusao.descricao, { nome: excluindo.nome })
              : null}
          </p>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setExcluindo(null)}>
              {t.confirmacaoExclusao.cancelar}
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={excluirTemplate.isPending || !excluindo}
              onClick={() => excluindo && excluirTemplate.mutate(excluindo)}
            >
              {t.confirmacaoExclusao.confirmar}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function FormularioEdicao({
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
  textos: ReturnType<typeof useTextos>["templatesWhatsApp"];
  onFechar: () => void;
  onSalvar: (corpo: string) => void;
}) {
  const [corpo, setCorpo] = useState(() => template?.corpo ?? "");
  const analise = analisarVariaveisDoCorpo(corpo);

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
          {(analise.erro || erro) && <p role="alert" className="text-sm text-destructive">{erro ?? textos.formulario.variavelInvalida}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar}>{textos.formulario.cancelar}</Button>
            <Button type="submit" disabled={salvando || Boolean(analise.erro) || !corpo.trim()}>{textos.formulario.salvarEdicao}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function filtrarTemplates(
  templates: TemplateWhatsApp[],
  busca: string,
  categorias: ReturnType<typeof useTextos>["templatesWhatsApp"]["categorias"],
  status: ReturnType<typeof useTextos>["templatesWhatsApp"]["status"],
): TemplateWhatsApp[] {
  const termo = busca.trim().toLowerCase();
  if (!termo) {
    return templates;
  }
  return templates.filter((template) => {
    return (
      template.nome.toLowerCase().includes(termo)
      || template.corpo.toLowerCase().includes(termo)
      || template.idioma.toLowerCase().includes(termo)
      || categorias[template.categoria].toLowerCase().includes(termo)
      || status[template.status].toLowerCase().includes(termo)
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
          <div>
            <label className="block text-sm" htmlFor="template-whatsapp-nome">
              {textos.formulario.nome}
            </label>
            <Input
              id="template-whatsapp-nome"
              className="mt-1"
              value={nome}
              onChange={(evento) => setNome(evento.target.value)}
              aria-label={textos.formulario.nome}
              aria-describedby="template-whatsapp-nome-ajuda"
              required
            />
            <span id="template-whatsapp-nome-ajuda" className="mt-1 block text-xs text-muted-foreground">
              {textos.formulario.nomeAjuda}
            </span>
          </div>
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
          <div>
            <label className="block text-sm" htmlFor="template-whatsapp-corpo">
              {textos.formulario.corpo}
            </label>
            <Textarea
              id="template-whatsapp-corpo"
              className="mt-1"
              value={corpo}
              onChange={(evento) => setCorpo(evento.target.value)}
              aria-label={textos.formulario.corpo}
              aria-describedby="template-whatsapp-corpo-ajuda"
              required
            />
            <span id="template-whatsapp-corpo-ajuda" className="mt-1 block text-xs text-muted-foreground">
              {ajudaDasVariaveis ? `${ajudaDasVariaveis}. ` : null}
              {textos.formulario.corpoAjuda}
            </span>
          </div>
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
