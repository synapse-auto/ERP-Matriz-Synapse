"use client";

import { useMemo, useState } from "react";
import { Info, Lock, RotateCcw, Search, Undo2 } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Seletor } from "@/components/ui/seletor";
import { Switch } from "@/components/ui/switch";
import { preverCopiaDeUsuario } from "@/lib/gestao/api";
import {
  aplicarNivel,
  alternarAcao,
  chavesAlteradas,
  normalizar,
  perfilFixo,
  quantidade,
  rascunhoDasExcecoes,
  rascunhoDoPerfil,
  restaurarAcao,
  restaurarNivel,
  simular,
  tocaSensivel,
  RASCUNHO_VAZIO,
} from "@/lib/gestao/rascunho";
import type { Catalogo, MinhasPermissoes, Papel, PermissoesDeUsuario, PreviaDeCopia, Rascunho, ResumoDeUsuario } from "@/lib/gestao/types";
import {
  useCatalogo,
  usePermissoesDaEquipe,
  usePermissoesDeUsuario,
  useRestaurarPadrao,
  useSalvarExcecoes,
} from "@/lib/gestao/use-gestao";
import { cn } from "@/lib/utils";

import {
  AlcanceFixo,
  ICONE_DO_MODULO,
  Legenda,
  SeloSensivel,
  SeletorDeNivel,
  motivoDoBloqueio,
  motivoForaDaAlcada,
  perfilFixoDoAtor,
  podeAlterarAcao,
  preencher,
  rotuloDaCapacidade,
  rotuloDoModulo,
  useConfirmacaoDeDescarte,
  useProtecaoDeSaida,
  type TextosGestao,
} from "./apoio";
import { BarraDeAlteracoes } from "./barra-de-alteracoes";
import { DialogoDeCopia } from "./dialogo-de-copia";

const ORDEM_DOS_GRUPOS: Papel[] = ["GESTOR", "SUBGESTOR", "ATENDENTE"];

interface EstadoDoRascunho {
  usuarioId: string;
  revisao: number;
  rascunho: Rascunho;
  copiadoDe?: string;
}

export function AbaExcecoes({
  textos,
  minhas,
  usuarioInicial,
  onSujoChange,
}: {
  textos: TextosGestao;
  minhas: MinhasPermissoes;
  usuarioInicial?: string;
  onSujoChange: (sujo: boolean) => void;
}) {
  const catalogo = useCatalogo();
  const equipe = usePermissoesDaEquipe();
  const [selecionado, setSelecionado] = useState<string | null>(usuarioInicial ?? null);
  const usuarioId = selecionado ?? equipe.data?.find((u) => u.editavel)?.id ?? equipe.data?.[0]?.id ?? null;
  const detalhe = usePermissoesDeUsuario(usuarioId);
  const salvar = useSalvarExcecoes();
  const restaurar = useRestaurarPadrao();
  const [busca, setBusca] = useState("");
  const [estado, setEstado] = useState<EstadoDoRascunho | null>(null);
  const [copia, setCopia] = useState<{ origem: ResumoDeUsuario; previa?: PreviaDeCopia; erro: boolean } | null>(null);

  const d = detalhe.data && detalhe.data.usuario.id === usuarioId ? detalhe.data : undefined;
  const perfil = useMemo(() => (d ? rascunhoDoPerfil(d.modulos, d.capacidades) : RASCUNHO_VAZIO), [d]);
  const base = useMemo(() => (d ? rascunhoDasExcecoes(d.modulos, d.capacidades) : RASCUNHO_VAZIO), [d]);
  const rascunho = estado && estado.usuarioId === usuarioId ? estado.rascunho : base;
  const alteradas = chavesAlteradas(base, rascunho);
  const sujo = alteradas.length > 0;
  const protecao = useProtecaoDeSaida(sujo, textos);
  const confirmacao = useConfirmacaoDeDescarte(sujo, textos);
  const mutacao = quantidade(rascunho) === 0 && quantidade(base) > 0 ? restaurar : salvar;

  function atualizar(novo: Rascunho, copiadoDe?: string) {
    if (!d) return;
    setEstado({
      usuarioId: d.usuario.id,
      revisao: estado?.usuarioId === d.usuario.id ? estado.revisao : d.revisao,
      rascunho: novo,
      copiadoDe: copiadoDe ?? estado?.copiadoDe,
    });
    salvar.reset();
    restaurar.reset();
    onSujoChange(true);
  }

  function descartar() {
    setEstado(null);
    salvar.reset();
    restaurar.reset();
    onSujoChange(false);
  }

  if (catalogo.isLoading || equipe.isLoading) return <p className="text-sm text-muted-foreground">{textos.carregando}</p>;
  if (catalogo.isError || equipe.isError || !catalogo.data || !equipe.data) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void Promise.all([catalogo.refetch(), equipe.refetch()])} />;
  }
  if (equipe.data.length === 0) {
    return <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">{textos.excecoes.vazio}</p>;
  }

  const cat = catalogo.data;
  const termo = normalizar(busca);
  const atorFixo = perfilFixoDoAtor(minhas);

  async function abrirCopia(origem: ResumoDeUsuario) {
    if (!usuarioId) return;
    setCopia({ origem, erro: false });
    try {
      const previa = await preverCopiaDeUsuario(usuarioId, origem.id);
      setCopia({ origem, previa, erro: false });
    } catch {
      setCopia({ origem, erro: true });
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <label className="relative w-full max-w-sm">
          <span className="sr-only">{textos.permissoes.busca}</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder={textos.permissoes.busca} className="h-10 bg-card pl-9" />
        </label>
        <Legenda textos={textos} />
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(15rem,19rem)_minmax(0,1fr)]">
        <ListaDeIntegrantes
          textos={textos}
          usuarios={equipe.data}
          selecionado={usuarioId}
          contagemDoSelecionado={sujo ? quantidade(rascunho) : null}
          onSelecionar={(id) => confirmacao.executar(() => {
            descartar();
            setSelecionado(id);
          })}
        />
        <div className="min-w-0">
          {detalhe.isError ? (
            <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void detalhe.refetch()} />
          ) : !d ? (
            <p className="text-sm text-muted-foreground">{usuarioId ? textos.carregando : textos.excecoes.selecione}</p>
          ) : (
            <DetalheDoUsuario
              textos={textos}
              catalogo={cat}
              detalhe={d}
              minhas={minhas}
              atorFixo={atorFixo}
              perfil={perfil}
              rascunho={rascunho}
              sujo={sujo}
              termo={termo}
              equipe={equipe.data}
              onNivel={(modulo, nivel) => atualizar(aplicarNivel(rascunho, cat, d.usuario.papel, modulo, nivel, perfil))}
              onAcao={(id, valor) => atualizar(alternarAcao(rascunho, id, valor, perfil))}
              onRestaurarAcao={(id) => atualizar(restaurarAcao(rascunho, id))}
              onRestaurarNivel={(modulo) => atualizar(restaurarNivel(rascunho, modulo))}
              onVoltarAoPadrao={() => atualizar(RASCUNHO_VAZIO)}
              onCopiar={(origem) => void abrirCopia(origem)}
            />
          )}
          {d && d.usuario.editavel && (
            <BarraDeAlteracoes
              textos={textos}
              quantidade={alteradas.length}
              impacto={quantidade(rascunho) === 0 && quantidade(base) > 0
                ? preencher(textos.barra.restauracao, { nome: d.usuario.nome })
                : preencher(textos.barra.impactoUsuario, { nome: d.usuario.nome })}
              sensivel={tocaSensivel(cat, alteradas)}
              salvando={salvar.isPending || restaurar.isPending}
              erro={mutacao.error}
              onDescartar={descartar}
              onRecarregar={() => {
                descartar();
                void detalhe.refetch();
              }}
              onSalvar={() => {
                if (!estado) return;
                if (mutacao === restaurar) {
                  restaurar.mutate({ id: d.usuario.id, revisao: estado.revisao }, { onSuccess: () => descartar() });
                } else {
                  salvar.mutate(
                    { id: d.usuario.id, revisao: estado.revisao, rascunho, copiadoDe: estado.copiadoDe },
                    { onSuccess: () => descartar() },
                  );
                }
              }}
            />
          )}
        </div>
      </div>

      {copia && (
        <DialogoDeCopia
          textos={textos}
          origem={`${copia.origem.nome} · ${textos.papeis[copia.origem.papel]}`}
          previa={copia.previa}
          carregando={!copia.previa && !copia.erro}
          erro={copia.erro}
          onFechar={() => setCopia(null)}
          onAplicar={() => {
            if (copia.previa) atualizar({ niveis: copia.previa.niveis, acoes: copia.previa.acoes }, copia.origem.id);
            setCopia(null);
          }}
        />
      )}
      {protecao}
      {confirmacao.dialogo}
    </div>
  );
}

function ListaDeIntegrantes({
  textos,
  usuarios,
  selecionado,
  contagemDoSelecionado,
  onSelecionar,
}: {
  textos: TextosGestao;
  usuarios: ResumoDeUsuario[];
  selecionado: string | null;
  contagemDoSelecionado: number | null;
  onSelecionar: (id: string) => void;
}) {
  return (
    <nav aria-label={textos.abas.excecoes} className="rounded-2xl border border-border bg-card p-3 lg:sticky lg:top-4 lg:max-h-[calc(100vh-7rem)] lg:self-start lg:overflow-y-auto">
      {ORDEM_DOS_GRUPOS.map((papel) => {
        const doGrupo = usuarios.filter((u) => u.papel === papel);
        if (doGrupo.length === 0) return null;
        return (
          <div key={papel} className="mb-2 last:mb-0">
            <p className="px-2 pt-2 pb-1.5 text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
              {perfilFixo(papel) ? preencher(textos.excecoes.grupoFixo, { papel: textos.papeis[papel] }) : textos.papeis[papel]}
            </p>
            <ul className="space-y-1">
              {doGrupo.map((u) => {
                const ativo = u.id === selecionado;
                const n = ativo && contagemDoSelecionado != null ? contagemDoSelecionado : u.excecoes;
                const sub = !u.ativo ? textos.excecoes.desativado : n > 0 ? textos.excecoes.perfilComExcecoes : textos.excecoes.perfilPadrao;
                return (
                  <li key={u.id}>
                    <button
                      type="button"
                      aria-current={ativo ? "true" : undefined}
                      onClick={() => onSelecionar(u.id)}
                      className={cn(
                        "flex w-full items-center gap-2.5 rounded-xl border px-2.5 py-2 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
                        ativo ? "border-primary bg-accent/60" : "border-transparent hover:bg-muted/60",
                        !u.ativo && "opacity-60",
                      )}
                    >
                      <AvatarIniciais id={u.id} nome={u.nome} fotoUrl={u.fotoUrl} className="flex size-8 shrink-0 items-center justify-center rounded-lg text-[11px] font-bold text-white" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[13px] font-semibold text-foreground">{u.nome}</span>
                        <span className="block truncate text-[11px] text-muted-foreground">{sub}</span>
                      </span>
                      {n > 0 && (
                        <span className="shrink-0 rounded-md bg-cor-atencao/15 px-1.5 py-0.5 text-[10px] font-bold text-cor-atencao">
                          {n === 1 ? textos.excecoes.excecao : preencher(textos.excecoes.excecoes, { n })}
                          {ativo && contagemDoSelecionado != null && ` · ${textos.excecoes.rascunho}`}
                        </span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
    </nav>
  );
}

function DetalheDoUsuario({
  textos,
  catalogo,
  detalhe,
  minhas,
  atorFixo,
  perfil,
  rascunho,
  sujo,
  termo,
  equipe,
  onNivel,
  onAcao,
  onRestaurarAcao,
  onRestaurarNivel,
  onVoltarAoPadrao,
  onCopiar,
}: {
  textos: TextosGestao;
  catalogo: Catalogo;
  detalhe: PermissoesDeUsuario;
  minhas: MinhasPermissoes;
  atorFixo: boolean;
  perfil: Rascunho;
  rascunho: Rascunho;
  sujo: boolean;
  termo: string;
  equipe: ResumoDeUsuario[];
  onNivel: (modulo: string, nivel: Catalogo["modulos"][number]["nivelMinimoPermitido"]) => void;
  onAcao: (id: string, valor: boolean) => void;
  onRestaurarAcao: (id: string) => void;
  onRestaurarNivel: (modulo: string) => void;
  onVoltarAoPadrao: () => void;
  onCopiar: (origem: ResumoDeUsuario) => void;
}) {
  const u = detalhe.usuario;
  const papel = u.papel;
  const editavel = u.editavel && !detalhe.fixo;
  const simUsuario = simular(catalogo, papel, perfil, rascunho);
  const simPerfil = simular(catalogo, papel, perfil);
  const n = quantidade(rascunho);
  const proprio = u.id === minhas.usuarioId;

  const opcoesDeCopia = equipe
    .filter((o) => o.id !== u.id)
    .map((o) => {
      const fixo = perfilFixo(o.papel);
      const foraDaAlcada = !atorFixo && o.papel !== "ATENDENTE";
      const sufixo = fixo
        ? preencher(textos.excecoes.copiaIndisponivel, { nome: o.nome })
        : foraDaAlcada ? textos.excecoes.copiaForaDaAlcada : `${o.nome} · ${textos.papeis[o.papel]}`;
      return { valor: o.id, rotulo: sufixo, desabilitada: fixo || foraDaAlcada };
    });

  const modulos = catalogo.modulos.filter((m) => {
    if (!termo) return true;
    const r = rotuloDoModulo(textos, m.id);
    if (normalizar(`${r.rotulo} ${r.descricao} ${m.id}`).includes(termo)) return true;
    return catalogo.capacidades.some((c) => c.modulo === m.id && normalizar(`${rotuloDaCapacidade(textos, c.id)} ${c.id}`).includes(termo));
  });

  return (
    <section aria-label={u.nome} className="overflow-hidden rounded-2xl border border-border bg-card">
      <header className="flex flex-wrap items-center gap-3 border-b border-border p-4">
        <AvatarIniciais id={u.id} nome={u.nome} fotoUrl={u.fotoUrl} className="flex size-11 shrink-0 items-center justify-center rounded-xl text-sm font-bold text-white" />
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-2">
            <span className="text-base font-bold text-foreground">{u.nome}</span>
            <PillDeStatus tom="neutro">{textos.papeis[papel]}</PillDeStatus>
            {n > 0 && (
              <span className="rounded-md bg-cor-atencao/15 px-1.5 py-0.5 text-[11px] font-bold text-cor-atencao">
                {n === 1 ? textos.excecoes.excecao : preencher(textos.excecoes.excecoes, { n })}
                {sujo && ` · ${textos.excecoes.rascunho}`}
              </span>
            )}
          </p>
          <p className="text-xs text-muted-foreground">
            {detalhe.fixo
              ? preencher(textos.excecoes.fixoAviso, { papel: textos.papeis[papel] })
              : preencher(textos.excecoes.herdaDe, { papel: textos.papeis[papel] })}
          </p>
        </div>
        {editavel && (
          <div className="flex flex-wrap items-center gap-2">
            <Seletor
              valor=""
              placeholder={textos.excecoes.copiarDe}
              ariaLabel={textos.excecoes.copiarDe}
              className="h-9 w-56"
              opcoes={opcoesDeCopia}
              onChange={(id) => {
                const origem = equipe.find((o) => o.id === id);
                if (origem) onCopiar(origem);
              }}
            />
            <Button type="button" variant="outline" className="h-9" disabled={n === 0} onClick={onVoltarAoPadrao}>
              <Undo2 className="size-(--tamanho-icone-interface)" aria-hidden />
              {textos.excecoes.voltarPadrao}
            </Button>
          </div>
        )}
      </header>
      {!editavel && !detalhe.fixo && (
        <p className="flex items-center gap-2 border-b border-border bg-muted/50 px-4 py-2 text-xs text-muted-foreground">
          <Lock className="size-3.5" aria-hidden />
          {proprio ? textos.excecoes.proprio : textos.excecoes.somenteLeitura}
        </p>
      )}
      {editavel && !atorFixo && (
        <p className="flex items-center gap-2 border-b border-border bg-muted/50 px-4 py-2 text-xs text-muted-foreground">
          <Info className="size-3.5" aria-hidden />
          {textos.excecoes.nivelNaoDelegavel}
        </p>
      )}

      {/* relative: o sr-only (absolute) do cabeçalho não pode escapar do recorte e alargar a página. */}
      <div className="relative overflow-x-auto">
        <table className="w-full min-w-[44rem] text-sm">
          <thead className="bg-muted/50 text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
            <tr>
              <th scope="col" className="px-4 py-2.5 text-left">{textos.excecoes.colunas.permissao}</th>
              <th scope="col" className="w-36 px-3 py-2.5 text-left">{textos.excecoes.colunas.padrao}</th>
              <th scope="col" className="w-[22rem] px-3 py-2.5 text-left">{textos.excecoes.colunas.usuario}</th>
              <th scope="col" className="w-12 px-2 py-2.5"><span className="sr-only">{textos.excecoes.personalizado}</span></th>
            </tr>
          </thead>
          <tbody>
            {modulos.map((m) => {
              const Icone = ICONE_DO_MODULO[m.id] ?? Info;
              const r = rotuloDoModulo(textos, m.id);
              const nivelExcecao = m.id in rascunho.niveis;
              const caps = catalogo.capacidades.filter((c) => c.modulo === m.id)
                .filter((c) => !termo || normalizar(`${rotuloDaCapacidade(textos, c.id)} ${c.id}`).includes(termo)
                  || normalizar(`${r.rotulo} ${m.id}`).includes(termo));
              return [
                <tr key={m.id} className={cn("border-t border-border", nivelExcecao ? "bg-cor-atencao/[0.07]" : "bg-muted/20")}>
                  <th scope="rowgroup" className="px-4 py-2.5 text-left">
                    <span className="flex items-center gap-2.5">
                      <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
                        <Icone className="size-4" />
                      </span>
                      <span className="font-bold text-foreground">{r.rotulo}</span>
                      {nivelExcecao && <SeloPersonalizado textos={textos} />}
                    </span>
                  </th>
                  <td className="px-3 py-2.5 text-xs text-muted-foreground">{textos.permissoes.niveis[simPerfil.niveis[m.id]]}</td>
                  <td className="px-3 py-2.5">
                    <SeletorDeNivel
                      valor={simUsuario.niveis[m.id]}
                      minimo={m.nivelMinimoPermitido}
                      maximo={m.nivelMaximoPorPapel[papel]}
                      desabilitado={!editavel || !atorFixo}
                      rotulo={preencher(textos.permissoes.nivelRotulo, { modulo: r.rotulo })}
                      textos={textos}
                      onChange={(nivel) => onNivel(m.id, nivel)}
                    />
                  </td>
                  <td className="px-2 py-2.5 text-center">
                    {nivelExcecao && editavel && atorFixo && (
                      <BotaoRestaurar rotulo={preencher(textos.excecoes.restaurar, { acao: r.rotulo })} onClick={() => onRestaurarNivel(m.id)} />
                    )}
                  </td>
                </tr>,
                ...caps.map((c) => {
                  const nome = rotuloDaCapacidade(textos, c.id);
                  const estado = simUsuario.estados[c.id];
                  const personalizado = c.id in rascunho.acoes;
                  if (c.tipo === "ALCANCE_ESTRUTURAL") {
                    const alcance = c.alcancePorPapel[papel] ?? "MEUS";
                    return (
                      <tr key={c.id} className="border-t border-border">
                        <td className="py-2.5 pr-3 pl-[3.75rem] text-[13px]">{nome}</td>
                        <td className="px-3 py-2.5 text-xs text-muted-foreground">{textos.permissoes.alcance[alcance]}</td>
                        <td className="px-3 py-2.5"><AlcanceFixo alcance={alcance} textos={textos} /></td>
                        <td />
                      </tr>
                    );
                  }
                  const motivo = motivoDoBloqueio(textos, { motivo: estado.motivo, alcance: null }, c.nivelMinimo, c.dependencias);
                  const bloqueado = estado.motivo === "TETO_DO_PAPEL" || estado.motivo === "FLAG_DESLIGADA"
                    || estado.motivo === "NIVEL_DO_MODULO" || estado.motivo === "DEPENDENCIA";
                  const alteravel = editavel && !bloqueado && podeAlterarAcao(minhas, c, !estado.permitido);
                  const dica = !editavel || bloqueado ? motivo : !alteravel ? motivoForaDaAlcada(textos, c) : null;
                  return (
                    <tr key={c.id} className={cn("border-t border-border", personalizado && "bg-cor-atencao/[0.07]")}>
                      <td className="py-2.5 pr-3 pl-[3.75rem]">
                        <span className="flex flex-wrap items-center gap-1.5 text-[13px] text-foreground">
                          {nome}
                          {personalizado && <SeloPersonalizado textos={textos} />}
                          {c.sensivel && <SeloSensivel textos={textos} />}
                        </span>
                        {dica && (
                          <span id={`dica-${u.id}-${c.id}`} className="mt-0.5 flex items-center gap-1 text-[11px] text-muted-foreground">
                            <Lock className="size-3" aria-hidden />
                            {dica}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2.5 text-xs text-muted-foreground">
                        {simPerfil.estados[c.id]?.permitido ? textos.excecoes.sim : textos.excecoes.nao}
                      </td>
                      <td className="px-3 py-2.5">
                        <Switch
                          checked={estado.permitido}
                          disabled={!alteravel}
                          aria-label={nome}
                          aria-describedby={dica ? `dica-${u.id}-${c.id}` : undefined}
                          onCheckedChange={(valor) => onAcao(c.id, valor)}
                        />
                      </td>
                      <td className="px-2 py-2.5 text-center">
                        {personalizado && editavel && (atorFixo || c.delegavel) && (
                          <BotaoRestaurar rotulo={preencher(textos.excecoes.restaurar, { acao: nome })} onClick={() => onRestaurarAcao(c.id)} />
                        )}
                      </td>
                    </tr>
                  );
                }),
              ];
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SeloPersonalizado({ textos }: { textos: TextosGestao }) {
  return (
    <span className="shrink-0 rounded-sm bg-cor-atencao/15 px-1.5 py-px text-[9px] font-extrabold tracking-wide text-cor-atencao uppercase">
      {textos.excecoes.personalizado}
    </span>
  );
}

function BotaoRestaurar({ rotulo, onClick }: { rotulo: string; onClick: () => void }) {
  return (
    <Button type="button" size="icon" variant="ghost" className="size-8 text-cor-atencao hover:text-cor-atencao" aria-label={rotulo} title={rotulo} onClick={onClick}>
      <RotateCcw className="size-4" aria-hidden />
    </Button>
  );
}
