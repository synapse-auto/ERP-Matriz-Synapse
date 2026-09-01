"use client";

import { useState } from "react";

import { BadgeCheck, Check, Copy, KeyRound, Medal, Pencil, Star, UserRoundX, Users } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Seletor } from "@/components/ui/seletor";
import { Switch } from "@/components/ui/switch";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useAvaliacoesEquipe,
  useCriarUsuario,
  useDesativarUsuario,
  useDesempenhoEquipe,
  useEditarUsuario,
  useEquipe,
  useGerarSenhaProvisoria,
  useAtualizarDisponibilidadeParaIa,
} from "@/lib/equipe/use-equipe";
import { recebeAtendimento } from "@/lib/equipe/papel";
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
  const gerarSenha = useGerarSenhaProvisoria();
  const disponibilidadeIa = useAtualizarDisponibilidadeParaIa();
  const [novo, setNovo] = useState(false);
  const [edicao, setEdicao] = useState<UsuarioEquipe | null>(null);
  const [paraDesativar, setParaDesativar] = useState<UsuarioEquipe | null>(null);
  const [senhaGeradaPara, setSenhaGeradaPara] = useState<UsuarioEquipe | null>(null);
  const [senhaGerada, setSenhaGerada] = useState<string | null>(null);

  const usuarios = (equipe.data ?? []).filter((u) => recebeAtendimento(u.papel));
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

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <Button onClick={() => setNovo(true)}>{t.novo}</Button>
      </header>

      {equipe.isLoading ? (
        <p>{t.carregando}</p>
      ) : equipe.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => equipe.refetch()} />
      ) : (
        <>
          {avaliacoes.isError || desempenho.isError ? (
            <ErroDeCarregamento
              mensagem={t.erro}
              onTentarNovamente={() =>
                Promise.all([avaliacoes.refetch(), desempenho.refetch()])
              }
            />
          ) : !avaliacoes.isLoading && !desempenho.isLoading ? (
            <MiniDashboard
              totalUsuarios={usuarios.length}
              online={online}
              ativos={ativos.length}
              mediaGeral={avaliacoes.data?.mediaGeral}
              rankingAvaliacao={rankingAvaliacao}
              rankingVendas={rankingVendas}
              fotoPorId={new Map(usuarios.map((usuario) => [usuario.id, usuario.fotoUrl]))}
              textos={t}
            />
          ) : null}

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
              onDesativar={setParaDesativar}
              onGerarSenhaProvisoria={(usuario) => {
                setSenhaGeradaPara(usuario);
                setSenhaGerada(null);
                gerarSenha.mutate(usuario.id, {
                  onSuccess: (resposta) => setSenhaGerada(resposta.senha),
                });
              }}
              onAlternarDisponibilidade={(usuario, disponivelParaIa) =>
                disponibilidadeIa.mutate({ id: usuario.id, disponivelParaIa })
              }
            />
          </div>
        </>
      )}

      <Formulario aberto={novo} onFechar={() => setNovo(false)} />
      {edicao && <Formulario aberto existente={edicao} onFechar={() => setEdicao(null)} />}
      {senhaGeradaPara && (
        <SenhaProvisoriaDialog
          usuario={senhaGeradaPara}
          senha={senhaGerada}
          erro={gerarSenha.isError}
          onFechar={() => setSenhaGeradaPara(null)}
        />
      )}
      {paraDesativar && (
        <Dialog open onOpenChange={(v) => !v && setParaDesativar(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t.desativacao.titulo}</DialogTitle>
              <DialogDescription>
                {t.desativacao.descricao.replace("{nome}", paraDesativar.nome)}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setParaDesativar(null)}>
                {t.desativacao.cancelar}
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={() => {
                  desativar.mutate(paraDesativar.id, {
                    onSuccess: () => setParaDesativar(null),
                  });
                }}
                disabled={desativar.isPending}
              >
                {t.desativacao.confirmar}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
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
  fotoPorId,
  textos,
}: {
  totalUsuarios: number;
  online: number;
  ativos: number;
  mediaGeral: number | undefined;
  rankingAvaliacao: LinhaRanking[];
  rankingVendas: LinhaDesempenho[];
  fotoPorId: Map<string, string | null | undefined>;
  textos: TextosEquipe;
}) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div className="flex items-center gap-4 rounded-lg border bg-card p-4">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-primary/10">
          <Users className="size-[calc(var(--tamanho-icone-interface)*1.5)] text-primary" />
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
          <Star className="size-[calc(var(--tamanho-icone-interface)*1.5)] text-cor-atencao" />
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
          <Medal className="size-(--tamanho-icone-interface) text-cor-atencao" />
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
                  fotoUrl={fotoPorId.get(item.atendenteId)}
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
          <BadgeCheck className="size-(--tamanho-icone-interface) text-primary" />
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
                  fotoUrl={fotoPorId.get(item.atendenteId)}
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
  onGerarSenhaProvisoria,
  onAlternarDisponibilidade,
}: {
  usuarios: UsuarioEquipe[];
  avaliacoes: LinhaRanking[];
  desempenho: LinhaDesempenho[];
  textos: TextosEquipe;
  onEditar: (usuario: UsuarioEquipe) => void;
  onDesativar: (usuario: UsuarioEquipe) => void;
  onGerarSenhaProvisoria: (usuario: UsuarioEquipe) => void;
  onAlternarDisponibilidade: (usuario: UsuarioEquipe, disponivelParaIa: boolean) => void;
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
              {textos.disponibilidadeIa.rotulo}
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
                      fotoUrl={usuario.fotoUrl}
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
                  {recebeAtendimento(usuario.papel) ? (
                    <div className="flex items-center gap-2">
                      <Switch
                        checked={usuario.disponivelParaIa ?? false}
                        aria-label={`${textos.disponibilidadeIa.rotulo} ${usuario.nome}`}
                        onCheckedChange={(checked) => onAlternarDisponibilidade(usuario, checked)}
                      />
                      <span className="text-xs text-muted-foreground">
                        {usuario.disponivelParaIa
                          ? textos.disponibilidadeIa.disponivel
                          : textos.disponibilidadeIa.indisponivel}
                      </span>
                    </div>
                  ) : (
                    <span className="text-xs text-muted-foreground">
                      {textos.disponibilidadeIa.naoAplicavel}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className="inline-flex items-center gap-1 text-xs font-bold">
                    <Star className="size-[calc(var(--tamanho-icone-interface)*0.875)] text-cor-atencao" />
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
                      <Pencil className="size-(--tamanho-icone-interface)" />
                    </Button>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="size-8"
                      aria-label={`${textos.senhaProvisoria.acao} ${usuario.nome}`}
                      onClick={() => onGerarSenhaProvisoria(usuario)}
                    >
                      <KeyRound className="size-(--tamanho-icone-interface)" />
                    </Button>
                    {usuario.ativo && (
                      <Button
                        size="icon"
                        variant="ghost"
                        className="size-8 text-destructive hover:text-destructive"
                        aria-label={`${textos.desativar} ${usuario.nome}`}
                        onClick={() => onDesativar(usuario)}
                      >
                        <UserRoundX className="size-(--tamanho-icone-interface)" />
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

/**
 * A senha só existe em memória enquanto este diálogo estiver aberto (E29): não há como reabri-lo
 * com a mesma senha depois de fechado — o backend não a devolve de novo.
 */
export function SenhaProvisoriaDialog({
  usuario,
  senha,
  erro,
  onFechar,
}: {
  usuario: UsuarioEquipe;
  senha: string | null;
  erro: boolean;
  onFechar: () => void;
}) {
  const t = useTextos().equipe.senhaProvisoria;
  const [copiada, setCopiada] = useState(false);

  async function copiar() {
    if (!senha) return;
    await navigator.clipboard.writeText(senha);
    setCopiada(true);
  }

  return (
    <Dialog open onOpenChange={(v) => !v && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t.dialogoTitulo}</DialogTitle>
          <DialogDescription>
            {t.dialogoDescricao.replace("{nome}", usuario.nome)}
          </DialogDescription>
        </DialogHeader>
        {erro ? (
          <p className="text-destructive">{t.erro}</p>
        ) : senha ? (
          <div className="flex items-center gap-2">
            <Input readOnly value={senha} className="font-mono" />
            <Button type="button" variant="outline" size="icon" onClick={() => void copiar()}>
              {copiada ? <Check className="size-(--tamanho-icone-interface)" /> : <Copy className="size-(--tamanho-icone-interface)" />}
            </Button>
          </div>
        ) : (
          <p className="text-muted-foreground">…</p>
        )}
        <DialogFooter>
          <Button type="button" onClick={onFechar}>
            {t.fechar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function Formulario({
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
              <PasswordInput
                required
                minLength={8}
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
