"use client";

import { useState } from "react";
import type { ComponentType } from "react";

import {
  Briefcase,
  Building2,
  Crown,
  Flag,
  Flame,
  Heart,
  Layers,
  Repeat,
  ShieldCheck,
  Star,
  Store,
  Tag as TagIcon,
  UserPlus,
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
import { useTextos } from "@/lib/config/textos-provider";
import { useAtualizarTag, useCriarTag, useRemoverTag, useTags } from "@/lib/tags/use-tags";
import type { DadosDeTag, Tag } from "@/lib/tags/types";

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
const ICONES: Record<string, ComponentType<{ className?: string }>> = {
  Tag: TagIcon,
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

const ICONE_PADRAO = "Tag";

function IconeDaTag({ nome, className }: { nome: string | null; className?: string }) {
  const Icone = (nome && ICONES[nome]) || ICONES[ICONE_PADRAO];
  return <Icone className={className} />;
}

export function PaginaTags() {
  const t = useTextos().tags;
  const tags = useTags();
  const remover = useRemoverTag();
  const [busca, setBusca] = useState("");
  const [novaAberta, setNovaAberta] = useState(false);
  const [edicao, setEdicao] = useState<Tag | null>(null);

  const filtradas = (tags.data ?? []).filter((tag) =>
    tag.nome.toLowerCase().includes(busca.trim().toLowerCase()),
  );

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
        <p className="text-destructive">{t.erro}</p>
      ) : filtradas.length === 0 ? (
        <p className="text-muted-foreground">{busca ? t.semResultados : t.vazio}</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="p-2 text-left">{t.colunas.tag}</th>
                <th className="p-2 text-left">{t.colunas.acoes}</th>
              </tr>
            </thead>
            <tbody>
              {filtradas.map((tag) => (
                <tr className="border-t" key={tag.id}>
                  <td className="p-2">
                    <Badge
                      variant="outline"
                      style={{ borderColor: tag.cor, color: tag.cor }}
                      className="gap-1.5"
                    >
                      <IconeDaTag nome={tag.icone} className="size-3" />
                      {tag.nome}
                    </Badge>
                  </td>
                  <td className="space-x-2 p-2">
                    <Button size="sm" variant="outline" onClick={() => setEdicao(tag)}>
                      {t.editar}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => remover.mutate(tag.id)}>
                      {t.remover}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Formulario aberto={novaAberta} onFechar={() => setNovaAberta(false)} />
      {edicao && <Formulario aberto existente={edicao} onFechar={() => setEdicao(null)} />}
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
            <div className="grid grid-cols-7 gap-1.5">
              {Object.entries(ICONES).map(([nomeIcone, Icone]) => (
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
              ))}
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
