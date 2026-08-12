"use client";

import { useState } from "react";

import { BadgeCheck, Medal, Pencil, Star, UserRoundX, Users } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
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
import { Seletor } from "@/components/ui/seletor";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useAvaliacoesEquipe,
  useCriarUsuario,
  useDesativarUsuario,
  useDesempenhoEquipe,
  useEditarUsuario,
  useEquipe,
} from "@/lib/equipe/use-equipe";
import type { PapelGerenciavel, StatusPresenca, UsuarioEquipe } from "@/lib/equipe/types";

const PRESENCA_COR: Record<StatusPresenca, string> = {
  ONLINE: "var(--cor-sucesso)",
  AUSENTE: "var(--cor-atencao)",
  OFFLINE: "var(--muted-foreground)",
};

export function PaginaEquipe() {
  const t = useTextos().equipe;
  const equipe = useEquipe();
  const avaliacoes = useAvaliacoesEquipe();
  const desempenho = useDesempenhoEquipe();
  const desativar = useDesativarUsuario();
  const [novo, setNovo] = useState(false);
  const [edicao, setEdicao] = useState<UsuarioEquipe | null>(null);

  const usuarios = (equipe.data ?? []).filter(
    (u) => u.papel === "ATENDENTE" || u.papel === "SUBGESTOR",
  );
  const ativos = usuarios.filter((u) => u.ativo);
  const online = usuarios.filter((u) => u.statusPresenca === "ONLINE").length;

  const rankingAvaliacao = (avaliacoes.data?.porAtendente ?? [])
    .slice()
    .sort((a, b) => b.media - a.media)
    .slice(0, 5);
  const rankingVendas = (desempenho.data?.porAtendente ?? [])
    .filter((item) => item.vendas > 0)
    .slice()
    .sort((a, b) => b.vendas - a.vendas || a.atendenteNome.localeCompare(b.atendenteNome))
    .slice(0, 5);

  const carregando = equipe.isLoading || avaliacoes.isLoading || desempenho.isLoading;
  const comErro = equipe.isError || avaliacoes.isError || desempenho.isError;

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <Button onClick={() => setNovo(true)}>{t.novo}</Button>
      </header>

      {carregando ? (
        <p>{t.carregando}</p>
      ) : comErro ? (
        <p className="text-destructive">{t.erro}</p>
      ) : (
        <>
          <MiniDashboard
            totalUsuarios={usuarios.length}
            online={online}
            ativos={ativos.length}
            mediaGeral={avaliacoes.data?.mediaGeral}
            rankingAvaliacao={rankingAvaliacao}
            rankingVendas={rankingVendas}
            textos={t}
          />

          <div className="space-y-3">
            <p className="px-0.5 text-xs font-bold tracking-wide text-muted-foreground">
              {t.grade.titulo} · {usuarios.length}
            </p>
            <TabelaDeUsuarios
              usuarios={usuarios}
              avaliacoes={avaliacoes.data?.porAtendente ?? []}
              desempenho={desempenho.data?.porAtendente ?? []}
              textos={t}
              onEditar={setEdicao}
              onDesativar={(id) => desativar.mutate(id)}
            />
          </div>
        </>
      )}

      <Formulario aberto={novo} onFechar={() => setNovo(false)} />
      {edicao && <Formulario aberto existente={edicao} onFechar={() => setEdicao(null)} />}
    </div>
  );
}

type TextosEquipe = ReturnType<typeof useTextos>["equipe"];
type LinhaRanking = { atendenteId: string; atendenteNome: string; media: number; total: number };
type LinhaDesempenho = {
  atendenteId: string;
  atendenteNome: string;
  atendimentos: number;
  vendas: number;
};

function MiniDashboard({
  totalUsuarios,
  online,
  ativos,
  mediaGeral,
  rankingAvaliacao,
  rankingVendas,
  textos,
}: {
  totalUsuarios: number;
  online: number;
  ativos: number;
  mediaGeral: number | undefined;
  rankingAvaliacao: LinhaRanking[];
  rankingVendas: LinhaDesempenho[];
  textos: TextosEquipe;
}) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div className="flex items-center gap-4 rounded-lg border bg-card p-4">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-primary/10">
          <Users className="size-6 text-primary" />
        </div>
        <div>
          <p className="text-xs font-medium text-muted-foreground">{textos.dashboard.equipeLabel}</p>
          <p className="text-2xl font-bold">{totalUsuarios}</p>
          <p className="text-xs text-muted-foreground">
            {textos.dashboard.onlineLabel
              .replace("{online}", String(online))
              .replace("{ativos}", String(ativos))}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-4 rounded-lg border bg-card p-4">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-cor-atencao/10">
          <Star className="size-6 text-cor-atencao" />
        </div>
        <div>
          <p className="text-xs font-medium text-muted-foreground">{textos.dashboard.avaliacaoMedia}</p>
          <p className="text-2xl font-bold">
            {mediaGeral != null ? mediaGeral.toFixed(1) : textos.avaliacoes.semDados}
            {mediaGeral != null && <span className="text-sm font-normal text-muted-foreground"> / 10</span>}
          </p>
        </div>
      </div>

      <div className="rounded-lg border bg-card p-4">
        <div className="mb-2 flex items-center gap-2">
          <Medal className="size-4 text-cor-atencao" />
          <p className="text-sm font-bold">{textos.dashboard.rankingAvaliacao}</p>
        </div>
        {rankingAvaliacao.length === 0 ? (
          <p className="text-xs text-muted-foreground">{textos.avaliacoes.semDados}</p>
        ) : (
          <ul className="space-y-1.5">
            {rankingAvaliacao.map((item, indice) => (
              <li key={item.atendenteId} className="flex items-center gap-2.5">
                <span className="flex size-5 shrink-0 items-center justify-center rounded-md bg-muted text-[11px] font-bold text-muted-foreground">
                  {indice + 1}
                </span>
                <AvatarIniciais
                  id={item.atendenteId}
                  nome={item.atendenteNome}
                  className="flex size-6 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
                />
                <span className="flex-1 truncate text-xs font-medium">{item.atendenteNome}</span>
                <span className="text-xs font-bold">{item.media.toFixed(1)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-lg border bg-card p-4">
        <div className="mb-2 flex items-center gap-2">
          <BadgeCheck className="size-4 text-primary" />
          <p className="text-sm font-bold">{textos.dashboard.rankingVendas}</p>
        </div>
        {rankingVendas.length === 0 ? (
          <p className="text-xs text-muted-foreground">{textos.avaliacoes.semDados}</p>
        ) : (
          <ul className="space-y-1.5">
            {rankingVendas.map((item, indice) => (
              <li key={item.atendenteId} className="flex items-center gap-2.5">
                <span className="flex size-5 shrink-0 items-center justify-center rounded-md bg-muted text-[11px] font-bold text-muted-foreground">
                  {indice + 1}
                </span>
                <AvatarIniciais
                  id={item.atendenteId}
                  nome={item.atendenteNome}
                  className="flex size-6 shrink-0 items-center justify-center rounded-md text-[10px] font-bold text-white"
                />
                <span className="flex-1 truncate text-xs font-medium">{item.atendenteNome}</span>
                <span className="text-xs font-bold">{item.vendas}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/** Papel colorido em azul (SUBGESTOR) ou neutro (ATENDENTE) — TOKENS.md, tons do protótipo. */
const TOM_DO_PAPEL: Record<PapelGerenciavel, "info" | "neutro"> = {
  SUBGESTOR: "info",
  ATENDENTE: "neutro",
};

function TabelaDeUsuarios({
  usuarios,
  avaliacoes,
  desempenho,
  textos,
  onEditar,
  onDesativar,
}: {
  usuarios: UsuarioEquipe[];
  avaliacoes: LinhaRanking[];
  desempenho: LinhaDesempenho[];
  textos: TextosEquipe;
  onEditar: (usuario: UsuarioEquipe) => void;
  onDesativar: (id: string) => void;
}) {
  const avaliacaoPorId = new Map(avaliacoes.map((a) => [a.atendenteId, a]));
  const desempenhoPorId = new Map(desempenho.map((item) => [item.atendenteId, item]));

  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-card">
      <table className="min-w-[980px] w-full text-sm">
        <thead className="bg-muted/60">
          <tr>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.usuario}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.funcao}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.presenca}
            </th>
            <th className="px-4 py-3 text-left text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.avaliacao}
            </th>
            <th className="px-4 py-3 text-right text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.atendimentos}
            </th>
            <th className="px-4 py-3 text-right text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.vendas}
            </th>
            <th className="px-4 py-3 text-right text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {textos.colunas.acoes}
            </th>
          </tr>
        </thead>
        <tbody>
          {usuarios.map((usuario) => {
            const papel = usuario.papel as PapelGerenciavel;
            const avaliacao = avaliacaoPorId.get(usuario.id);
            const metricas = desempenhoPorId.get(usuario.id);
            return (
              <tr
                key={usuario.id}
                className={`border-t border-border ${usuario.ativo ? "" : "opacity-60"}`}
              >
                <td className="px-4 py-3">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <AvatarIniciais
                      id={usuario.id}
                      nome={usuario.nome}
                      className="flex size-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                    />
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="truncate font-bold text-foreground">{usuario.nome}</p>
                        {!usuario.ativo && (
                          <span className="shrink-0 rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-bold text-muted-foreground">
                            {textos.inativo}
                          </span>
                        )}
                      </div>
                      <p className="truncate text-xs text-muted-foreground">{usuario.email}</p>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <PillDeStatus tom={TOM_DO_PAPEL[papel]}>
                    {textos.papeis[papel === "SUBGESTOR" ? "subgestor" : "atendente"]}
                  </PillDeStatus>
                </td>
                <td className="px-4 py-3">
                  <span className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                    <span
                      className="size-2 rounded-full"
                      style={{ backgroundColor: PRESENCA_COR[usuario.statusPresenca] }}
                    />
                    {
                      textos.presenca[
                        usuario.statusPresenca.toLowerCase() as "online" | "ausente" | "offline"
                      ]
                    }
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="inline-flex items-center gap-1 text-xs font-bold">
                    <Star className="size-3.5 text-cor-atencao" />
                    {avaliacao
                      ? `${avaliacao.media.toFixed(1)} (${avaliacao.total})`
                      : textos.avaliacoes.semDados}
                  </span>
                </td>
                <td className="px-4 py-3 text-right font-bold tabular-nums">
                  {metricas?.atendimentos ?? 0}
                </td>
                <td className="px-4 py-3 text-right font-bold tabular-nums">
                  {metricas?.vendas ?? 0}
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-1">
                    <Button
                      size="icon"
                      variant="ghost"
                      className="size-8"
                      aria-label={`${textos.editar} ${usuario.nome}`}
                      onClick={() => onEditar(usuario)}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    {usuario.ativo && (
                      <Button
                        size="icon"
                        variant="ghost"
                        className="size-8 text-destructive hover:text-destructive"
                        aria-label={`${textos.desativar} ${usuario.nome}`}
                        onClick={() => onDesativar(usuario.id)}
                      >
                        <UserRoundX className="size-4" />
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Formulario({
  aberto,
  existente,
  onFechar,
}: {
  aberto: boolean;
  existente?: UsuarioEquipe;
  onFechar: () => void;
}) {
  const t = useTextos().equipe.formulario;
  const criar = useCriarUsuario();
  const editar = useEditarUsuario();
  const [nome, setNome] = useState(existente?.nome ?? "");
  const [email, setEmail] = useState(existente?.email ?? "");
  const [senha, setSenha] = useState("");
  const [papel, setPapel] = useState<PapelGerenciavel>(
    (existente?.papel as PapelGerenciavel) ?? "ATENDENTE",
  );

  const salvando = criar.isPending || editar.isPending;
  const comErro = criar.isError || editar.isError;

  function salvar() {
    if (existente) {
      editar.mutate({ id: existente.id, dados: { nome, email, papel } }, { onSuccess: onFechar });
    } else {
      criar.mutate({ nome, email, senha, papel }, { onSuccess: onFechar });
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
            <Input required value={nome} onChange={(e) => setNome(e.target.value)} />
          </label>
          <label className="block space-y-1">
            <span>{t.email}</span>
            <Input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          {!existente && (
            <label className="block space-y-1">
              <span>{t.senha}</span>
              <Input
                required
                minLength={8}
                type="password"
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
              />
            </label>
          )}
          <label className="block space-y-1">
            <span>{t.papel}</span>
            <Seletor
              valor={papel}
              placeholder={t.papel}
              opcoes={[
                { valor: "ATENDENTE", rotulo: t.atendente },
                { valor: "SUBGESTOR", rotulo: t.subgestor },
              ]}
              onChange={(valor) => setPapel(valor as PapelGerenciavel)}
            />
          </label>
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
