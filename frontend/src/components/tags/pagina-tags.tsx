"use client";

import { useState } from "react";
import type { ComponentType, CSSProperties } from "react";

import {
  Briefcase,
  Building2,
  Crown,
  DoorOpen,
  Droplet,
  Flag,
  Flashlight,
  Flame,
  Hammer,
  Heart,
  Layers,
  Pencil,
  Percent,
  RectangleHorizontal,
  RefreshCw,
  Repeat,
  Rows3,
  Shapes,
  ShieldCheck,
  Star,
  Store,
  Tag as TagIcon,
  Trash2,
  UserPlus,
  UserRoundCheck,
  Wrench,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
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
import { useTextos } from "@/lib/config/textos-provider";
import {
  useAgregacaoDeTags,
  useAtualizarTag,
  useCriarTag,
  useRemoverTag,
  useTags,
} from "@/lib/tags/use-tags";
import type { AgregacaoDeTags, DadosDeTag, Tag } from "@/lib/tags/types";

/**
 * Tokens de tema disponíveis para tag (design/TOKENS.md): nenhuma cor literal aqui, só
 * `var(--...)` — as mesmas variáveis que `AtalhoTags` já usa para pintar o badge da tag.
 */
const CORES = [
  "var(--primary)",
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
  "var(--destructive)",
];

/** lucide-react continua a biblioteca de ícones do projeto — sem introduzir Remix Icon. */
const ICONES: Record<string, ComponentType<{ className?: string; style?: CSSProperties }>> = {
  Tag: TagIcon,
  Droplet,
  RectangleHorizontal,
  Shapes,
  Rows3,
  DoorOpen,
  Hammer,
  RefreshCw,
  UserRoundCheck,
  Flashlight,
  // Compatibilidade de leitura: existia no seletor anterior, mas não faz parte do protótipo.
  Flag,
  Star,
  Heart,
  ShieldCheck,
  Building2,
  Store,
  Briefcase,
  Wrench,
  Repeat,
  UserPlus,
  Crown,
  Flame,
  Layers,
};

/** Mesma ordem das 22 opções aprovadas em design/componentes/Tags.html. */
const ICONES_DO_MODAL = [
  "Droplet",
  "ShieldCheck",
  "RectangleHorizontal",
  "Shapes",
  "Building2",
  "Rows3",
  "DoorOpen",
  "Layers",
  "Store",
  "Briefcase",
  "Hammer",
  "Repeat",
  "UserPlus",
  "RefreshCw",
  "UserRoundCheck",
  "Wrench",
  "Crown",
  "Tag",
  "Star",
  "Flame",
  "Flashlight",
  "Heart",
] as const;

const ICONE_PADRAO = "Tag";

function IconeDaTag({
  nome,
  className,
  style,
}: {
  nome: string | null;
  className?: string;
  style?: CSSProperties;
}) {
  const Icone = (nome && ICONES[nome]) || ICONES[ICONE_PADRAO];
  return <Icone className={className} style={style} />;
}

type TextosTags = ReturnType<typeof useTextos>["tags"];

export function PaginaTags() {
  const t = useTextos().tags;
  const tags = useTags();
  const agregacao = useAgregacaoDeTags();
  const remover = useRemoverTag();
  const [busca, setBusca] = useState("");
  const [novaAberta, setNovaAberta] = useState(false);
  const [edicao, setEdicao] = useState<Tag | null>(null);

  const todas = tags.data ?? [];
  const filtradas = todas.filter((tag) =>
    tag.nome.toLowerCase().includes(busca.trim().toLowerCase()),
  );

  const contagemPorTagId = new Map(
    (agregacao.data?.porTag ?? []).map((item) => [item.id, item.quantidade]),
  );
  const maiorContagem = Math.max(1, ...Array.from(contagemPorTagId.values()));
  const totalVisivel = agregacao.data?.totalLeadsVisiveis ?? 0;

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <div className="flex items-center gap-2">
          <Input
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder={t.busca}
            className="w-56"
          />
          <Button onClick={() => setNovaAberta(true)}>{t.nova}</Button>
        </div>
      </header>

      {tags.isLoading ? (
        <p>{t.carregando}</p>
      ) : tags.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => tags.refetch()} />
      ) : (
        <>
          {agregacao.isError ? (
            <ErroDeCarregamento
              mensagem={t.erro}
              onTentarNovamente={() => agregacao.refetch()}
            />
          ) : (
            <MiniDashboard totalTags={todas.length} agregacao={agregacao.data} textos={t.dashboard} />
          )}

          {filtradas.length === 0 ? (
            <p className="text-muted-foreground">{busca ? t.semResultados : t.vazio}</p>
          ) : (
            <div className="space-y-3">
              <p className="px-0.5 text-xs font-bold tracking-wide text-muted-foreground">
                {t.grade.titulo} · {filtradas.length}
              </p>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {filtradas.map((tag) => (
                  <CartaoDeTag
                    key={tag.id}
                    tag={tag}
                    contagem={contagemPorTagId.get(tag.id) ?? 0}
                    maiorContagem={maiorContagem}
                    totalVisivel={totalVisivel}
                    textos={t}
                    onEditar={() => setEdicao(tag)}
                    onRemover={() => remover.mutate(tag.id)}
                  />
                ))}
              </div>
            </div>
          )}
        </>
      )}

      <Formulario aberto={novaAberta} onFechar={() => setNovaAberta(false)} />
      {edicao && <Formulario aberto existente={edicao} onFechar={() => setEdicao(null)} />}
    </div>
  );
}

/**
 * Mini-dashboard (E17b §Bloco 5): total de tags vem do catálogo (`useTags`), independente de papel
 * — tag é da operação inteira. Tag mais usada e % tagueados vêm de `useAgregacaoDeTags`, que já
 * chega recortado pela mesma visibilidade da listagem de leads (RN-CRM-01); nunca calculados aqui
 * a partir de um total que o atendente não enxergaria.
 */
function MiniDashboard({
  totalTags,
  agregacao,
  textos,
}: {
  totalTags: number;
  agregacao: AgregacaoDeTags | undefined;
  textos: TextosTags["dashboard"];
}) {
  const maisUsada = agregacao?.tagMaisUsada ?? null;
  const percentual = agregacao ? Math.round(agregacao.percentualTagueados) : 0;

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <div className="flex items-center gap-4 rounded-lg border bg-card p-4">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-primary/10">
          <TagIcon className="size-6 text-primary" />
        </div>
        <div>
          <p className="text-xs font-medium text-muted-foreground">{textos.totalTags}</p>
          <p className="text-2xl font-bold">{totalTags}</p>
        </div>
      </div>

      <div className="flex items-center gap-4 rounded-lg border bg-card p-4">
        <div
          className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-muted"
          style={
            maisUsada
              ? { backgroundColor: `color-mix(in srgb, ${maisUsada.cor} 16%, transparent)` }
              : undefined
          }
        >
          <IconeDaTag
            nome={maisUsada?.icone ?? null}
            className="size-6"
            style={maisUsada ? { color: maisUsada.cor } : undefined}
          />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium text-muted-foreground">{textos.tagMaisUsada}</p>
          {maisUsada ? (
            <>
              <p className="truncate text-lg font-bold">{maisUsada.nome}</p>
              <p className="text-xs text-muted-foreground">
                {textos.leadsVinculados.replace("{n}", String(maisUsada.quantidade))}
              </p>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">{textos.semDados}</p>
          )}
        </div>
      </div>

      <div className="flex items-center gap-4 rounded-lg border bg-card p-4">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-cor-sucesso/10">
          <Percent className="size-6 text-cor-sucesso" />
        </div>
        <div>
          <p className="text-xs font-medium text-muted-foreground">{textos.leadsTagueados}</p>
          <p className="text-2xl font-bold">{percentual}%</p>
          <p className="text-xs text-muted-foreground">
            {textos.leadsDeTotal
              .replace("{comTag}", String(agregacao?.leadsComTag ?? 0))
              .replace("{total}", String(agregacao?.totalLeadsVisiveis ?? 0))}
          </p>
        </div>
      </div>
    </div>
  );
}

/** Um card por tag (E17b §Bloco 5), no lugar da linha de tabela que existia antes. */
function CartaoDeTag({
  tag,
  contagem,
  maiorContagem,
  totalVisivel,
  textos,
  onEditar,
  onRemover,
}: {
  tag: Tag;
  contagem: number;
  maiorContagem: number;
  totalVisivel: number;
  textos: TextosTags;
  onEditar: () => void;
  onRemover: () => void;
}) {
  const percentualDaBase = totalVisivel > 0 ? Math.round((contagem / totalVisivel) * 100) : 0;
  const larguraDaBarra = Math.round((contagem / maiorContagem) * 100);

  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-center gap-3">
        <div
          className="flex size-11 shrink-0 items-center justify-center rounded-lg"
          style={{ backgroundColor: `color-mix(in srgb, ${tag.cor} 16%, transparent)` }}
        >
          <IconeDaTag nome={tag.icone} className="size-5" style={{ color: tag.cor }} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold">{tag.nome}</p>
          <p className="text-xs text-muted-foreground">
            {textos.grade.leadsEPercentual
              .replace("{n}", String(contagem))
              .replace("{pct}", String(percentualDaBase))}
          </p>
        </div>
        <div className="flex gap-1">
          <Button
            size="icon"
            variant="ghost"
            className="size-8"
            aria-label={`${textos.editar} ${tag.nome}`}
            onClick={onEditar}
          >
            <Pencil className="size-4" />
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="size-8 text-destructive hover:text-destructive"
            aria-label={`${textos.remover} ${tag.nome}`}
            onClick={onRemover}
          >
            <Trash2 className="size-4" />
          </Button>
        </div>
      </div>
      <div className="mt-4 h-2 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full"
          style={{ width: `${larguraDaBarra}%`, backgroundColor: tag.cor }}
        />
      </div>
      <div className="mt-3 flex items-center justify-between">
        <Badge variant="outline" style={{ borderColor: tag.cor, color: tag.cor }} className="gap-1.5">
          <IconeDaTag nome={tag.icone} className="size-3" />
          {tag.nome}
        </Badge>
        <span className="text-xs font-medium text-muted-foreground">{textos.grade.previa}</span>
      </div>
    </div>
  );
}

function Formulario({
  aberto,
  existente,
  onFechar,
}: {
  aberto: boolean;
  existente?: Tag;
  onFechar: () => void;
}) {
  const t = useTextos().tags.formulario;
  const criar = useCriarTag();
  const atualizar = useAtualizarTag();
  const [nome, setNome] = useState(existente?.nome ?? "");
  const [cor, setCor] = useState(existente?.cor ?? CORES[0]);
  const [icone, setIcone] = useState(existente?.icone ?? ICONE_PADRAO);

  const salvando = criar.isPending || atualizar.isPending;
  const comErro = criar.isError || atualizar.isError;

  function salvar() {
    const dados: DadosDeTag = { nome, cor, icone };
    if (existente) {
      atualizar.mutate({ id: existente.id, dados }, { onSuccess: onFechar });
    } else {
      criar.mutate(dados, { onSuccess: onFechar });
    }
  }

  return (
    <Dialog open={aberto} onOpenChange={(v) => !v && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{existente ? t.editarTitulo : t.criarTitulo}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            salvar();
          }}
        >
          <label className="block space-y-1">
            <span>{t.nome}</span>
            <Input
              required
              value={nome}
              placeholder={t.nomePlaceholder}
              onChange={(e) => setNome(e.target.value)}
            />
          </label>

          <div className="space-y-1">
            <span className="text-sm">{t.cor}</span>
            <div className="flex gap-2">
              {CORES.map((valor) => (
                <button
                  key={valor}
                  type="button"
                  aria-label={valor}
                  aria-pressed={cor === valor}
                  onClick={() => setCor(valor)}
                  className="size-7 rounded-full border-2"
                  style={{
                    backgroundColor: valor,
                    borderColor: cor === valor ? valor : "transparent",
                  }}
                />
              ))}
            </div>
          </div>

          <div className="space-y-1">
            <span className="text-sm">{t.icone}</span>
            <div
              role="group"
              aria-label={t.icone}
              className="grid grid-cols-11 gap-1.5"
            >
              {ICONES_DO_MODAL.map((nomeIcone) => {
                const Icone = ICONES[nomeIcone];
                return (
                <button
                  key={nomeIcone}
                  type="button"
                  aria-label={nomeIcone}
                  aria-pressed={icone === nomeIcone}
                  onClick={() => setIcone(nomeIcone)}
                  className={
                    icone === nomeIcone
                      ? "flex size-8 items-center justify-center rounded-md border-2 border-primary bg-primary/10 text-primary"
                      : "flex size-8 items-center justify-center rounded-md border text-muted-foreground"
                  }
                >
                  <Icone className="size-4" />
                </button>
                );
              })}
            </div>
          </div>

          {comErro && <p className="text-destructive">{t.erro}</p>}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar}>
              {t.cancelar}
            </Button>
            <Button type="submit" disabled={salvando}>
              {t.salvar}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
