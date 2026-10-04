"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Ban, KeyRound, Pencil, ShieldCheck, Star, UserCheck, UserRoundCog } from "lucide-react";

import { MiniDashboard, SenhaProvisoriaDialog } from "@/components/equipe/pagina-equipe";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Switch } from "@/components/ui/switch";
import { useTextos } from "@/lib/config/textos-provider";
import { recebeAtendimento, visivelNaEquipe } from "@/lib/equipe/papel";
import type { PapelGerenciavel, StatusPresenca, UsuarioEquipe } from "@/lib/equipe/types";
import {
  useAtualizarDisponibilidadeParaIa,
  useAvaliacoesEquipe,
  useDesativarUsuario,
  useDesempenhoEquipe,
  useEquipe,
  useGerarSenhaProvisoria,
} from "@/lib/equipe/use-equipe";
import type { MinhasPermissoes, Papel, Perfil } from "@/lib/gestao/types";
import { CHAVE_GESTAO, usePerfis, usePermissoesDaEquipe } from "@/lib/gestao/use-gestao";
import { cn } from "@/lib/utils";

import { ICONE_DO_PAPEL, perfilFixoDoAtor, pode, preencher, type TextosGestao } from "./apoio";
import { DialogoUsuario } from "./dialogo-usuario";

const COR_PRESENCA: Record<StatusPresenca, string> = {
  ONLINE: "var(--cor-sucesso)",
  AUSENTE: "var(--cor-atencao)",
  OFFLINE: "var(--texto-fraco)",
};

const ORDEM_DO_PAPEL: Record<Papel, number> = { ADMINISTRADOR: 0, GESTOR: 1, SUBGESTOR: 2, ATENDENTE: 3, OPERADOR: 4 };

const TOM_DO_PAPEL = { GESTOR: "info", SUBGESTOR: "info", ATENDENTE: "neutro", OPERADOR: "neutro", ADMINISTRADOR: "info" } as const;

/**
 * Equipe: resumos por perfil com números reais, integrantes com presença, disponibilidade da IA,
 * avaliação, situação de permissões e as ações que quem está logado realmente pode executar.
 * Presença, disponibilidade para a IA e permissão são conceitos distintos e aparecem separados.
 */
export function AbaEquipe({
  textos,
  minhas,
  novoAberto,
  onFecharNovo,
  onAbrirPerfil,
  onAbrirExcecoes,
}: {
  textos: TextosGestao;
  minhas: MinhasPermissoes;
  novoAberto: boolean;
  onFecharNovo: () => void;
  onAbrirPerfil: (papel: Papel) => void;
  /** Ausente com Exceções "Em breve": o atalho da linha abre o perfil da função. */
  onAbrirExcecoes?: (usuarioId: string) => void;
}) {
  const textosEquipe = useTextos().equipe;
  const cache = useQueryClient();
  const equipe = useEquipe();
  const perfis = usePerfis();
  const permissoes = usePermissoesDaEquipe();
  const atorFixo = perfilFixoDoAtor(minhas);
  const avaliacoes = useAvaliacoesEquipe(atorFixo);
  const desempenho = useDesempenhoEquipe(atorFixo);
  const desativar = useDesativarUsuario();
  const gerarSenha = useGerarSenhaProvisoria();
  const disponibilidade = useAtualizarDisponibilidadeParaIa();
  const [edicao, setEdicao] = useState<UsuarioEquipe | null>(null);
  const [paraDesativar, setParaDesativar] = useState<UsuarioEquipe | null>(null);
  const [senhaPara, setSenhaPara] = useState<UsuarioEquipe | null>(null);
  const [senha, setSenha] = useState<string | null>(null);

  if (equipe.isLoading || perfis.isLoading) return <p className="text-sm text-muted-foreground">{textos.carregando}</p>;
  if (equipe.isError || perfis.isError) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void Promise.all([equipe.refetch(), perfis.refetch()])} />;
  }

  const usuarios = (equipe.data ?? [])
    .filter((u) => visivelNaEquipe(u.papel))
    .sort((a, b) => ORDEM_DO_PAPEL[a.papel] - ORDEM_DO_PAPEL[b.papel]
      || Number(b.ativo) - Number(a.ativo) || a.nome.localeCompare(b.nome));
  const excecoesPorId = new Map((permissoes.data ?? []).map((p) => [p.id, p.excecoes]));
  const visiveisNasExcecoes = new Set((permissoes.data ?? []).map((p) => p.id));
  const avaliacaoPorId = new Map((avaliacoes.data?.porAtendente ?? []).map((a) => [a.atendenteId, a]));
  const funcoesPermitidas: PapelGerenciavel[] = atorFixo ? ["SUBGESTOR", "OPERADOR", "ATENDENTE"] : ["ATENDENTE"];

  function gerenciavel(u: UsuarioEquipe): boolean {
    if (atorFixo) return u.papel === "ATENDENTE" || u.papel === "SUBGESTOR" || u.papel === "OPERADOR";
    return u.papel === "ATENDENTE" && u.id !== minhas.usuarioId;
  }

  return (
    <div className="space-y-5">
      <div className="grid gap-4 md:grid-cols-3">
        {(perfis.data ?? []).map((p) => (
          <ResumoDoPerfil key={p.papel} perfil={p} textos={textos} onAbrir={() => onAbrirPerfil(p.papel)} />
        ))}
      </div>

      <div className="overflow-hidden rounded-2xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead className="text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
            <tr className="border-b border-border">
              <th scope="col" className="px-5 py-3.5 text-left">{textos.equipe.colunas.usuario}</th>
              <th scope="col" className="hidden px-3 py-3.5 text-left md:table-cell">{textos.equipe.colunas.funcao}</th>
              <th scope="col" className="hidden px-3 py-3.5 text-left sm:table-cell">{textos.equipe.colunas.presenca}</th>
              <th scope="col" className="hidden px-3 py-3.5 text-left lg:table-cell">{textos.equipe.colunas.ia}</th>
              {atorFixo && <th scope="col" className="hidden px-3 py-3.5 text-left xl:table-cell">{textos.equipe.colunas.avaliacao}</th>}
              <th scope="col" className="hidden px-3 py-3.5 text-left md:table-cell">{textos.equipe.colunas.permissoes}</th>
              <th scope="col" className="px-5 py-3.5 text-right">{textos.equipe.colunas.acoes}</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.length === 0 && (
              <tr>
                <td colSpan={7} className="px-5 py-10 text-center text-muted-foreground">{textos.equipe.vazio}</td>
              </tr>
            )}
            {usuarios.map((u) => {
              const Icone = ICONE_DO_PAPEL[u.papel];
              const excecoes = excecoesPorId.get(u.id) ?? 0;
              const avaliacao = avaliacaoPorId.get(u.id);
              const podeGerenciar = gerenciavel(u);
              const permissao = u.papel === "GESTOR" ? (
                <PillDeStatus tom="info" icone={<ShieldCheck className="size-3.5" />}>{textos.equipe.permissoes.acessoTotal}</PillDeStatus>
              ) : excecoes > 0 ? (
                <PillDeStatus tom="atencao" icone={<UserRoundCog className="size-3.5" />}>
                  {excecoes === 1 ? textos.equipe.permissoes.excecao : preencher(textos.equipe.permissoes.excecoes, { n: excecoes })}
                </PillDeStatus>
              ) : (
                <PillDeStatus tom="neutro" icone={<UserCheck className="size-3.5" />}>{textos.equipe.permissoes.padrao}</PillDeStatus>
              );
              return (
                <tr key={u.id} className={cn("border-b border-border last:border-b-0", !u.ativo && "opacity-60")}>
                  <td className="px-5 py-3.5">
                    <div className="flex min-w-0 items-center gap-3">
                      <AvatarIniciais id={u.id} nome={u.nome} fotoUrl={u.fotoUrl} className="flex size-10 shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white" />
                      <div className="min-w-0">
                        <p className="flex items-center gap-2">
                          <span className="truncate font-bold text-foreground">{u.nome}</span>
                          {!u.ativo && (
                            <span className="shrink-0 rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-bold tracking-wide text-muted-foreground uppercase">
                              {textos.equipe.desativado}
                            </span>
                          )}
                        </p>
                        <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                        <p className="mt-1 flex flex-wrap gap-1.5 md:hidden">
                          <PillDeStatus tom={TOM_DO_PAPEL[u.papel]}>{textos.papeis[u.papel]}</PillDeStatus>
                          {permissao}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="hidden px-3 py-3.5 md:table-cell">
                    <PillDeStatus tom={TOM_DO_PAPEL[u.papel]} icone={<Icone className="size-3.5" />}>{textos.papeis[u.papel]}</PillDeStatus>
                  </td>
                  <td className="hidden px-3 py-3.5 sm:table-cell">
                    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-foreground">
                      <span className="size-2 rounded-full" style={{ backgroundColor: COR_PRESENCA[u.statusPresenca] }} />
                      {textos.equipe.presenca[u.statusPresenca]}
                    </span>
                  </td>
                  <td className="hidden px-3 py-3.5 lg:table-cell">
                    {recebeAtendimento(u.papel) ? (
                      <span className="inline-flex items-center gap-2">
                        <Switch
                          checked={u.disponivelParaIa ?? false}
                          disabled={!u.ativo || !pode(minhas, "equipe.disponibilidade_ia") || disponibilidade.isPending}
                          aria-label={preencher(textos.equipe.ia.rotulo, { nome: u.nome })}
                          onCheckedChange={(v) => disponibilidade.mutate({ id: u.id, disponivelParaIa: v })}
                        />
                        <span className="text-xs text-muted-foreground">
                          {u.disponivelParaIa ? textos.equipe.ia.disponivel : textos.equipe.ia.indisponivel}
                        </span>
                      </span>
                    ) : (
                      <span className="text-xs text-muted-foreground">{textos.equipe.ia.naoAplicavel}</span>
                    )}
                  </td>
                  {atorFixo && (
                    <td className="hidden px-3 py-3.5 xl:table-cell">
                      <span className="inline-flex items-center gap-1 text-xs font-bold">
                        <Star className="size-3.5 text-cor-atencao" aria-hidden />
                        {avaliacao ? `${avaliacao.media.toFixed(1)} (${avaliacao.total})` : textos.equipe.semAvaliacao}
                      </span>
                    </td>
                  )}
                  <td className="hidden px-3 py-3.5 md:table-cell">{permissao}</td>
                  <td className="px-5 py-3.5">
                    <div className="ml-auto flex max-w-[4.75rem] flex-wrap justify-end gap-1.5 sm:max-w-none sm:flex-nowrap">
                      {visiveisNasExcecoes.has(u.id) && (
                        <AcaoDaLinha rotulo={preencher(textos.equipe.acoes.permissoes, { nome: u.nome })} onClick={() => (onAbrirExcecoes ? onAbrirExcecoes(u.id) : onAbrirPerfil(u.papel))}>
                          <ShieldCheck className="size-4" />
                        </AcaoDaLinha>
                      )}
                      {podeGerenciar && pode(minhas, "equipe.editar") && (
                        <AcaoDaLinha rotulo={preencher(textos.equipe.acoes.editar, { nome: u.nome })} onClick={() => setEdicao(u)}>
                          <Pencil className="size-4" />
                        </AcaoDaLinha>
                      )}
                      {podeGerenciar && pode(minhas, "equipe.senha_provisoria") && (
                        <AcaoDaLinha
                          rotulo={preencher(textos.equipe.acoes.senha, { nome: u.nome })}
                          onClick={() => {
                            setSenhaPara(u);
                            setSenha(null);
                            gerarSenha.mutate(u.id, { onSuccess: (r) => setSenha(r.senha) });
                          }}
                        >
                          <KeyRound className="size-4" />
                        </AcaoDaLinha>
                      )}
                      {podeGerenciar && u.ativo && pode(minhas, "equipe.desativar") && (
                        <AcaoDaLinha perigo rotulo={preencher(textos.equipe.acoes.desativar, { nome: u.nome })} onClick={() => setParaDesativar(u)}>
                          <Ban className="size-4" />
                        </AcaoDaLinha>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {atorFixo && avaliacoes.data && desempenho.data && (
        <section className="space-y-3" aria-label={textos.equipe.indicadores}>
          <p className="px-0.5 text-[11px] font-bold tracking-wide text-muted-foreground uppercase">{textos.equipe.indicadores}</p>
          <MiniDashboard
            totalUsuarios={usuarios.length}
            online={usuarios.filter((u) => u.statusPresenca === "ONLINE").length}
            ativos={usuarios.filter((u) => u.ativo).length}
            mediaGeral={avaliacoes.data.mediaGeral}
            rankingAvaliacao={avaliacoes.data.porAtendente.slice().sort((a, b) => b.media - a.media).slice(0, 5)}
            rankingVendas={desempenho.data.porAtendente.filter((i) => i.vendas > 0).slice().sort((a, b) => b.vendas - a.vendas).slice(0, 5)}
            fotoPorId={new Map(usuarios.map((u) => [u.id, u.fotoUrl]))}
            textos={textosEquipe}
          />
        </section>
      )}

      {novoAberto && (
        <DialogoUsuario textos={textos} funcoesPermitidas={funcoesPermitidas} podeAlterarFuncao onFechar={onFecharNovo} />
      )}
      {edicao && (
        <DialogoUsuario
          textos={textos}
          existente={edicao}
          funcoesPermitidas={funcoesPermitidas}
          podeAlterarFuncao={pode(minhas, "equipe.alterar_papel")}
          onFechar={() => setEdicao(null)}
        />
      )}
      {senhaPara && (
        <SenhaProvisoriaDialog usuario={senhaPara} senha={senha} erro={gerarSenha.isError} onFechar={() => setSenhaPara(null)} />
      )}
      {paraDesativar && (
        <Dialog open onOpenChange={(v) => !v && !desativar.isPending && setParaDesativar(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{textos.desativacao.titulo}</DialogTitle>
              <DialogDescription>{preencher(textos.desativacao.descricao, { nome: paraDesativar.nome })}</DialogDescription>
            </DialogHeader>
            {desativar.isError && <p role="alert" className="text-sm text-destructive">{textos.desativacao.erro}</p>}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setParaDesativar(null)} disabled={desativar.isPending}>
                {textos.desativacao.cancelar}
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={desativar.isPending}
                onClick={() =>
                  desativar.mutate(paraDesativar.id, {
                    onSuccess: () => {
                      void cache.invalidateQueries({ queryKey: CHAVE_GESTAO });
                      setParaDesativar(null);
                    },
                  })
                }
              >
                {textos.desativacao.confirmar}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}

function ResumoDoPerfil({ perfil, textos, onAbrir }: { perfil: Perfil; textos: TextosGestao; onAbrir: () => void }) {
  const Icone = ICONE_DO_PAPEL[perfil.papel];
  return (
    <div className="flex items-center gap-4 rounded-2xl border border-border bg-card px-5 py-4">
      <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-accent text-primary">
        <Icone className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-bold text-foreground">{textos.papeis[perfil.papel]}</p>
        {perfil.fixo ? (
          <p className="text-xs text-muted-foreground">{textos.resumoPerfil.fixo}</p>
        ) : (
          <button type="button" onClick={onAbrir} className="inline-flex items-center gap-1 text-left text-xs text-muted-foreground hover:text-primary focus-visible:text-primary focus-visible:outline-none">
            {preencher(textos.resumoPerfil.permissoes, { permitidas: perfil.permitidas, total: perfil.total })}
            {" · "}
            {perfil.editavel ? textos.resumoPerfil.editar : textos.resumoPerfil.ver}
            <ArrowRight className="size-3" aria-hidden />
          </button>
        )}
      </div>
      <span className="shrink-0 text-2xl font-extrabold text-foreground tabular-nums">{perfil.usuarios}</span>
    </div>
  );
}

function AcaoDaLinha({
  rotulo,
  perigo,
  onClick,
  children,
}: {
  rotulo: string;
  perigo?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <Button
      type="button"
      size="icon"
      variant="outline"
      aria-label={rotulo}
      title={rotulo}
      onClick={onClick}
      className={cn("size-8 rounded-lg text-muted-foreground", perigo && "hover:text-destructive")}
    >
      {children}
    </Button>
  );
}
