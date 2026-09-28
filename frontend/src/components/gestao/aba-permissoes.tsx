"use client";

import { useMemo, useState } from "react";
import { ChevronDown, Copy, Info, Lock, Search } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { GradeEmColunas } from "@/components/ui/grade-em-colunas";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { preverCopiaDePerfil } from "@/lib/gestao/api";
import {
  aplicarNivel,
  alternarAcao,
  chavesAlteradas,
  configuravel,
  contagem,
  normalizar,
  rascunhoDoPerfil,
  simular,
  tocaSensivel,
  type Simulacao,
} from "@/lib/gestao/rascunho";
import type { Catalogo, CapacidadeDoCatalogo, MinhasPermissoes, Papel, Perfil, PreviaDeCopia, Rascunho } from "@/lib/gestao/types";
import { useCatalogo, usePerfis, useSalvarPerfil } from "@/lib/gestao/use-gestao";
import { cn } from "@/lib/utils";

import {
  AlcanceFixo,
  ICONE_DO_MODULO,
  ICONE_DO_PAPEL,
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

/**
 * Peso de um cartão na grade, em "linhas de ação": cabeçalho e seletor de nível ocupam cerca de
 * três. Conta só o que a busca lista — nunca o estado dos interruptores, que muda a cada clique.
 */
const LINHAS_DO_CABECALHO = 3;

interface EstadoDoRascunho {
  papel: Papel;
  revisao: number;
  rascunho: Rascunho;
  copiadoDe?: Papel;
}

export function AbaPermissoes({
  textos,
  minhas,
  papelInicial,
  onSujoChange,
}: {
  textos: TextosGestao;
  minhas: MinhasPermissoes;
  papelInicial?: Papel;
  onSujoChange: (sujo: boolean) => void;
}) {
  const catalogo = useCatalogo();
  const perfis = usePerfis();
  const salvar = useSalvarPerfil();
  const [selecionado, setSelecionado] = useState<Papel | null>(papelInicial ?? null);
  const [busca, setBusca] = useState("");
  const [estado, setEstado] = useState<EstadoDoRascunho | null>(null);
  const [copia, setCopia] = useState<{ origem: Papel; previa?: PreviaDeCopia; erro: boolean } | null>(null);

  // Sem escolha explícita, abre no primeiro perfil que quem está logado pode editar.
  const papel: Papel = selecionado ?? perfis.data?.find((p) => p.editavel)?.papel ?? "SUBGESTOR";
  const perfil = perfis.data?.find((p) => p.papel === papel);
  const base = useMemo(() => (perfil ? rascunhoDoPerfil(perfil.modulos, perfil.capacidades) : null), [perfil]);
  const rascunho = estado && estado.papel === papel ? estado.rascunho : base;
  const alteradas = base && rascunho ? chavesAlteradas(base, rascunho) : [];
  const sujo = alteradas.length > 0;
  const protecao = useProtecaoDeSaida(sujo, textos);
  const confirmacao = useConfirmacaoDeDescarte(sujo, textos);

  function atualizar(novo: Rascunho, copiadoDe?: Papel) {
    if (!perfil) return;
    setEstado({ papel, revisao: estado?.papel === papel ? estado.revisao : perfil.revisao, rascunho: novo, copiadoDe: copiadoDe ?? estado?.copiadoDe });
    salvar.reset();
    onSujoChange(true);
  }

  function descartar() {
    setEstado(null);
    salvar.reset();
    onSujoChange(false);
  }

  if (catalogo.isLoading || perfis.isLoading) return <p className="text-sm text-muted-foreground">{textos.carregando}</p>;
  if (catalogo.isError || perfis.isError || !catalogo.data || !perfis.data) {
    return <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void Promise.all([catalogo.refetch(), perfis.refetch()])} />;
  }

  const cat = catalogo.data;
  const sim = perfil && rascunho ? simular(cat, papel, rascunho) : null;
  const editavel = perfil?.editavel === true;
  const atorFixo = perfilFixoDoAtor(minhas);
  const termo = normalizar(busca);
  const modulosVisiveis = cat.modulos.filter((m) => {
    if (!termo) return true;
    const r = rotuloDoModulo(textos, m.id);
    if (normalizar(`${r.rotulo} ${r.descricao} ${m.id}`).includes(termo)) return true;
    return cat.capacidades.some((c) => c.modulo === m.id && normalizar(`${rotuloDaCapacidade(textos, c.id)} ${c.id}`).includes(termo));
  });
  const origensDeCopia: Papel[] = (["SUBGESTOR", "ATENDENTE"] as Papel[]).filter((p) => p !== papel);

  async function abrirCopia(origem: Papel) {
    setCopia({ origem, erro: false });
    try {
      const previa = await preverCopiaDePerfil(papel, origem);
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
        {/* Cópia de perfil é de superior: a única origem para o perfil ATENDENTE está fora da alçada do subgestor. */}
        {editavel && atorFixo && (
          <div className="ml-auto">
            <DropdownMenu>
              <DropdownMenuTrigger render={<Button variant="outline" className="h-10 gap-2 bg-card" />}>
                <Copy className="size-(--tamanho-icone-interface)" aria-hidden />
                {textos.permissoes.copiar}
                <ChevronDown className="size-4" aria-hidden />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {origensDeCopia.map((origem) => (
                  <DropdownMenuItem key={origem} onClick={() => void abrirCopia(origem)}>
                    {preencher(textos.permissoes.copiarDe, { perfil: textos.papeis[origem] })}
                  </DropdownMenuItem>
                ))}
                <DropdownMenuItem disabled>
                  {preencher(textos.permissoes.copiarIndisponivel, { perfil: textos.papeis.GESTOR })}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(15rem,18rem)_minmax(0,1fr)]">
        <aside className="space-y-3 lg:sticky lg:top-4 lg:self-start">
          <p className="px-1 text-[11px] font-bold tracking-wide text-muted-foreground uppercase">{textos.permissoes.perfil}</p>
          <div role="radiogroup" aria-label={textos.permissoes.perfil} className="space-y-2.5">
            {perfis.data.map((p) => (
              <CartaoDePerfil
                key={p.papel}
                perfil={p}
                catalogo={cat}
                selecionado={p.papel === papel}
                simulacao={p.papel === papel && sim ? sim : null}
                textos={textos}
                onSelecionar={() => confirmacao.executar(() => {
                  descartar();
                  setSelecionado(p.papel);
                })}
              />
            ))}
          </div>
          <p className="flex gap-2 rounded-xl border border-dashed border-border p-3 text-xs text-muted-foreground">
            <Info className="mt-px size-3.5 shrink-0" aria-hidden />
            {textos.permissoes.notaHeranca}
          </p>
          {!editavel && perfil && !perfil.fixo && (
            <p className="flex gap-2 rounded-xl bg-muted p-3 text-xs text-muted-foreground">
              <Lock className="mt-px size-3.5 shrink-0" aria-hidden />
              {perfil.papel === minhas.papel ? textos.excecoes.proprio : textos.permissoes.somenteLeitura}
            </p>
          )}
        </aside>

        <div className="min-w-0">
          {perfil?.fixo && (
            <p className="mb-3 flex items-center gap-2 rounded-xl bg-accent px-3 py-2 text-xs font-medium text-accent-foreground">
              <Lock className="size-3.5" aria-hidden />
              {textos.permissoes.fixoAviso}
            </p>
          )}
          {editavel && !atorFixo && (
            <p className="mb-3 flex items-center gap-2 rounded-xl bg-muted px-3 py-2 text-xs text-muted-foreground">
              <Info className="size-3.5" aria-hidden />
              {textos.excecoes.nivelNaoDelegavel}
            </p>
          )}
          {modulosVisiveis.length === 0 ? (
            <p className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">{textos.permissoes.vazio}</p>
          ) : (
            sim && rascunho && (
              <GradeEmColunas
                itens={modulosVisiveis}
                chave={(m) => m.id}
                peso={(m) => LINHAS_DO_CABECALHO + capacidadesListadas(cat, m.id, termo, textos).length}
              >
                {(m) => (
                  <CartaoDeModulo
                    moduloId={m.id}
                    catalogo={cat}
                    papel={papel}
                    simulacao={sim}
                    editavel={editavel}
                    minhas={minhas}
                    termo={termo}
                    textos={textos}
                    onNivel={(nivel) => atualizar(aplicarNivel(rascunho, cat, papel, m.id, nivel))}
                    onAcao={(id, valor) => atualizar(alternarAcao(rascunho, id, valor))}
                  />
                )}
              </GradeEmColunas>
            )
          )}
          {perfil && editavel && (
            <BarraDeAlteracoes
              textos={textos}
              quantidade={alteradas.length}
              impacto={preencher(textos.barra.impactoPerfil, { n: perfil.usuarios })}
              sensivel={tocaSensivel(cat, alteradas)}
              salvando={salvar.isPending}
              erro={salvar.error}
              onDescartar={descartar}
              onRecarregar={() => {
                descartar();
                void perfis.refetch();
              }}
              onSalvar={() => {
                if (!rascunho || !estado) return;
                salvar.mutate(
                  { papel, revisao: estado.revisao, rascunho, copiadoDe: estado.copiadoDe },
                  { onSuccess: () => descartar() },
                );
              }}
            />
          )}
        </div>
      </div>

      {copia && (
        <DialogoDeCopia
          textos={textos}
          origem={textos.papeis[copia.origem]}
          previa={copia.previa}
          carregando={!copia.previa && !copia.erro}
          erro={copia.erro}
          onFechar={() => setCopia(null)}
          onAplicar={() => {
            if (copia.previa) atualizar({ niveis: copia.previa.niveis, acoes: copia.previa.acoes }, copia.origem);
            setCopia(null);
          }}
        />
      )}
      {protecao}
      {confirmacao.dialogo}
    </div>
  );
}

function CartaoDePerfil({
  perfil,
  catalogo,
  selecionado,
  simulacao,
  textos,
  onSelecionar,
}: {
  perfil: Perfil;
  catalogo: Catalogo;
  selecionado: boolean;
  simulacao: Simulacao | null;
  textos: TextosGestao;
  onSelecionar: () => void;
}) {
  const Icone = ICONE_DO_PAPEL[perfil.papel];
  const { permitidas, total } = simulacao
    ? contagem(catalogo, perfil.papel, simulacao)
    : { permitidas: perfil.permitidas, total: perfil.total };
  const percentual = total === 0 ? 0 : Math.round((permitidas / total) * 100);
  const usuarios = preencher(perfil.usuarios === 1 ? textos.resumoPerfil.usuario : textos.resumoPerfil.usuarios, { n: perfil.usuarios });
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selecionado}
      onClick={onSelecionar}
      className={cn(
        "w-full rounded-xl border bg-card p-3.5 text-left transition-shadow outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
        selecionado ? "border-primary shadow-[0_0_0_3px_var(--accent)]" : "border-border hover:shadow-sm",
      )}
    >
      <span className="flex items-center gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
          <Icone className="size-(--tamanho-icone-interface)" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-bold text-foreground">{textos.papeis[perfil.papel]}</span>
          <span className="block truncate text-xs text-muted-foreground">
            {usuarios}
            {perfil.fixo && ` · ${textos.resumoPerfil.acessoTotal}`}
          </span>
        </span>
        {perfil.fixo && <Lock className="size-4 text-muted-foreground" aria-hidden />}
      </span>
      <span className="mt-3 flex items-center gap-2">
        <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
          <span className="block h-full rounded-full bg-primary" style={{ width: `${percentual}%` }} />
        </span>
        <span className="shrink-0 text-[11px] font-semibold text-muted-foreground">
          {perfil.fixo ? `${percentual}%` : preencher(textos.resumoPerfil.permissoes, { permitidas, total })}
        </span>
      </span>
    </button>
  );
}

/** Linhas do cartão: as ações que casam com a busca; nenhuma casou, o módulo casou pelo nome e vai inteiro. */
function capacidadesListadas(catalogo: Catalogo, moduloId: string, termo: string, textos: TextosGestao): CapacidadeDoCatalogo[] {
  const capacidades = catalogo.capacidades.filter((c) => c.modulo === moduloId);
  if (!termo) return capacidades;
  const visiveis = capacidades.filter((c) => normalizar(`${rotuloDaCapacidade(textos, c.id)} ${c.id}`).includes(termo));
  return visiveis.length > 0 ? visiveis : capacidades;
}

function CartaoDeModulo({
  moduloId,
  catalogo,
  papel,
  simulacao,
  editavel,
  minhas,
  termo,
  textos,
  onNivel,
  onAcao,
}: {
  moduloId: string;
  catalogo: Catalogo;
  papel: Papel;
  simulacao: Simulacao;
  editavel: boolean;
  minhas: MinhasPermissoes;
  termo: string;
  textos: TextosGestao;
  onNivel: (nivel: Catalogo["modulos"][number]["nivelMinimoPermitido"]) => void;
  onAcao: (id: string, valor: boolean) => void;
}) {
  const modulo = catalogo.modulos.find((m) => m.id === moduloId)!;
  const { rotulo, descricao } = rotuloDoModulo(textos, moduloId);
  const Icone = ICONE_DO_MODULO[moduloId] ?? Info;
  const capacidades = catalogo.capacidades.filter((c) => c.modulo === moduloId);
  const lista = capacidadesListadas(catalogo, moduloId, termo, textos);
  const configuraveis = capacidades.filter((c) => configuravel(c, papel));
  const permitidas = configuraveis.filter((c) => simulacao.estados[c.id]?.permitido).length;
  const maximo = modulo.nivelMaximoPorPapel[papel];
  // Nível de módulo não é delegável: o subgestor delegado mexe só nos interruptores.
  const nivelEditavel = editavel && perfilFixoDoAtor(minhas);

  return (
    <section aria-labelledby={`modulo-${moduloId}`} className="rounded-2xl border border-border bg-card p-4 shadow-xs">
      <header className="mb-3 flex items-start gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
          <Icone className="size-(--tamanho-icone-interface)" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id={`modulo-${moduloId}`} className="text-sm font-bold text-foreground">{rotulo}</h2>
          <p className="truncate text-xs text-muted-foreground">{descricao}</p>
        </div>
        <span className="shrink-0 rounded-md bg-accent px-2 py-0.5 text-[11px] font-bold text-accent-foreground">
          {permitidas}/{configuraveis.length}
        </span>
      </header>
      <SeletorDeNivel
        valor={simulacao.niveis[moduloId]}
        minimo={modulo.nivelMinimoPermitido}
        maximo={maximo}
        desabilitado={!nivelEditavel}
        rotulo={preencher(textos.permissoes.nivelRotulo, { modulo: rotulo })}
        textos={textos}
        onChange={onNivel}
      />
      <ul className="mt-2 divide-y divide-border">
        {lista.map((c) => {
          const estado = simulacao.estados[c.id];
          const nome = rotuloDaCapacidade(textos, c.id);
          if (c.tipo === "ALCANCE_ESTRUTURAL") {
            const alcance = c.alcancePorPapel[papel] ?? "MEUS";
            return (
              <li key={c.id} className="flex items-center justify-between gap-3 py-2.5">
                <div className="min-w-0">
                  <p className="text-[13px] font-medium text-foreground">{nome}</p>
                  <p className="text-[11px] text-muted-foreground">
                    {alcance === "MEUS" ? textos.permissoes.motivos.ESTRUTURAL_MEUS : textos.permissoes.motivos.ESTRUTURAL_TODOS}
                  </p>
                </div>
                <AlcanceFixo alcance={alcance} textos={textos} />
              </li>
            );
          }
          const motivo = motivoDoBloqueio(textos, { motivo: estado.motivo, alcance: null }, c.nivelMinimo, c.dependencias);
          const bloqueado = estado.motivo === "TETO_DO_PAPEL" || estado.motivo === "FLAG_DESLIGADA"
            || estado.motivo === "NIVEL_DO_MODULO" || estado.motivo === "DEPENDENCIA";
          const alteravel = editavel && !bloqueado && estado.origem !== "FIXO" && podeAlterarAcao(minhas, c, !estado.permitido);
          const dica = !editavel || bloqueado ? motivo : !alteravel ? motivoForaDaAlcada(textos, c) : null;
          return (
            <li key={c.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-1.5 text-[13px] font-medium text-foreground">
                  {nome}
                  {c.sensivel && <SeloSensivel textos={textos} />}
                </p>
                {dica && (
                  <p id={`motivo-${papel}-${c.id}`} className="flex items-center gap-1 text-[11px] text-muted-foreground">
                    <Lock className="size-3" aria-hidden />
                    {dica}
                  </p>
                )}
              </div>
              <Switch
                checked={estado.permitido}
                disabled={!alteravel}
                aria-label={nome}
                aria-describedby={dica ? `motivo-${papel}-${c.id}` : undefined}
                onCheckedChange={(valor) => onAcao(c.id, valor)}
              />
            </li>
          );
        })}
      </ul>
    </section>
  );
}
