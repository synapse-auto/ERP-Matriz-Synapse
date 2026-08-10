"use client";

import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { EtapaAtendimento, CanalResumo, TagDoLead } from "@/lib/lead/types";
import type { UsuarioEquipe } from "@/lib/equipe/types";
import type { CampoFiltravel, FiltroAtivo, OperadorDeFiltro, StatusBasicoLead } from "@/lib/agenda/types";
import type { Textos } from "@/lib/config/schema";

type TextosFiltros = Textos["agenda"]["filtros"];
type TextosStatus = Textos["agenda"]["status"];

type Aridade = "NENHUM" | "UM" | "DOIS" | "LISTA";

function aridadeDoOperador(operador: OperadorDeFiltro): Aridade {
  if (operador === "PREENCHIDO" || operador === "VAZIO") return "NENHUM";
  if (operador === "ENTRE") return "DOIS";
  if (operador === "EM") return "LISTA";
  return "UM";
}

interface Referencias {
  etapas: EtapaAtendimento[];
  canais: CanalResumo[];
  equipe: UsuarioEquipe[];
  tags: TagDoLead[];
}

/** Opções conhecidas (id + rótulo) para um campo de referência — vazio quando o apelido é desconhecido. */
function opcoesDeReferencia(apelido: string, refs: Referencias): { id: string; rotulo: string }[] {
  switch (apelido) {
    case "etapa":
      return refs.etapas.map((e) => ({ id: e.id, rotulo: e.nome }));
    case "canalOrigem":
      return refs.canais.map((c) => ({ id: c.id, rotulo: c.nome }));
    case "atendenteResponsavel":
      return refs.equipe.map((u) => ({ id: u.id, rotulo: u.nome }));
    case "tag":
      return refs.tags.map((t) => ({ id: t.id, rotulo: t.nome }));
    default:
      return [];
  }
}

interface Props {
  campos: CampoFiltravel[];
  carregandoCampos: boolean;
  erroCampos: boolean;
  filtrosAtivos: FiltroAtivo[];
  referencias: Referencias;
  textos: TextosFiltros;
  textosStatus: TextosStatus;
  /** "Exibindo N de M leads" (E17b §Bloco 3) — mesma linha da barra, como no protótipo. */
  contador: ReactNode;
  onAdicionar: (filtro: FiltroAtivo) => void;
  onRemover: (id: string) => void;
  onLimparTudo: () => void;
}

export function BarraDeFiltros({
  campos,
  carregandoCampos,
  erroCampos,
  filtrosAtivos,
  referencias,
  textos,
  textosStatus,
  contador,
  onAdicionar,
  onRemover,
  onLimparTudo,
}: Props) {
  return (
    <div className="mb-4 space-y-3 rounded-lg border border-border bg-card p-3.5">
      <div className="flex flex-wrap items-end gap-3">
        <FormularioDeNovoFiltro
          campos={campos}
          carregandoCampos={carregandoCampos}
          erroCampos={erroCampos}
          referencias={referencias}
          textos={textos}
          textosStatus={textosStatus}
          onAdicionar={onAdicionar}
        />
        <div className="ml-auto shrink-0 pb-1.5">{contador}</div>
      </div>
      {filtrosAtivos.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
          {filtrosAtivos.map((filtro) => (
            <span
              key={filtro.id}
              className="inline-flex items-center gap-1.5 rounded-md border border-primary/20 bg-primary/10 py-1 pr-1.5 pl-2.5 text-xs font-semibold text-primary"
            >
              {filtro.rotuloValor}
              <button
                type="button"
                aria-label={`${textos.limparTudo}: ${filtro.rotuloValor}`}
                className="rounded-sm text-primary/70 hover:text-primary"
                onClick={() => onRemover(filtro.id)}
              >
                <X className="size-3.5" />
              </button>
            </span>
          ))}
          <Button type="button" variant="ghost" size="sm" onClick={onLimparTudo}>
            {textos.limparTudo}
          </Button>
        </div>
      )}
    </div>
  );
}

const ROTULOS_DE_OPERADOR: Record<OperadorDeFiltro, keyof TextosFiltros["operadores"]> = {
  IGUAL: "igual",
  DIFERENTE: "diferente",
  CONTEM: "contem",
  COMECA_COM: "comecaCom",
  MAIOR_QUE: "maiorQue",
  MENOR_QUE: "menorQue",
  ENTRE: "entre",
  EM: "em",
  PREENCHIDO: "preenchido",
  VAZIO: "vazio",
};

const ROTULOS_DE_STATUS: Record<StatusBasicoLead, keyof TextosStatus> = {
  IA: "ia",
  EM_ATENDIMENTO: "emAtendimento",
  FINALIZADO: "finalizado",
};

function FormularioDeNovoFiltro({
  campos,
  carregandoCampos,
  erroCampos,
  referencias,
  textos,
  textosStatus,
  onAdicionar,
}: {
  campos: CampoFiltravel[];
  carregandoCampos: boolean;
  erroCampos: boolean;
  referencias: Referencias;
  textos: TextosFiltros;
  textosStatus: TextosStatus;
  onAdicionar: (filtro: FiltroAtivo) => void;
}) {
  const [apelido, setApelido] = useState("");
  const [operador, setOperador] = useState<OperadorDeFiltro | "">("");
  const [valor, setValor] = useState("");
  const [valorAte, setValorAte] = useState("");
  const [valoresLista, setValoresLista] = useState<string[]>([]);

  const campo = campos.find((c) => c.apelido === apelido) ?? null;
  const aridade = operador ? aridadeDoOperador(operador) : null;
  const opcoesReferencia = campo ? opcoesDeReferencia(campo.apelido, referencias) : [];

  function limparValores() {
    setValor("");
    setValorAte("");
    setValoresLista([]);
  }

  function selecionarCampo(novoApelido: string) {
    setApelido(novoApelido);
    setOperador("");
    limparValores();
  }

  function selecionarOperador(novoOperador: OperadorDeFiltro | "") {
    setOperador(novoOperador);
    limparValores();
  }

  function rotuloDoValorUnico(bruto: string): string {
    const opcao = opcoesReferencia.find((o) => o.id === bruto);
    if (opcao) return opcao.rotulo;
    if (campo?.tipo === "STATUS") return textosStatus[ROTULOS_DE_STATUS[bruto as StatusBasicoLead]];
    return bruto;
  }

  function podeAdicionar(): boolean {
    if (!campo || !operador) return false;
    if (aridade === "NENHUM") return true;
    if (aridade === "DOIS") return valor.trim() !== "" && valorAte.trim() !== "";
    if (aridade === "LISTA") return valoresLista.length > 0;
    return valor.trim() !== "";
  }

  function adicionar() {
    if (!campo || !operador || !podeAdicionar()) return;
    const rotuloOperador = textos.operadores[ROTULOS_DE_OPERADOR[operador]];
    let filtro: FiltroAtivo;
    if (aridade === "NENHUM") {
      filtro = {
        id: crypto.randomUUID(),
        campo,
        operador,
        rotuloValor: `${campo.rotulo} ${rotuloOperador}`,
      };
    } else if (aridade === "DOIS") {
      filtro = {
        id: crypto.randomUUID(),
        campo,
        operador,
        valores: [valor, valorAte],
        rotuloValor: `${campo.rotulo} ${rotuloOperador} ${valor} ${textos.valorFinal.toLowerCase()} ${valorAte}`,
      };
    } else if (aridade === "LISTA") {
      const rotulos = valoresLista.map(rotuloDoValorUnico).join(", ");
      filtro = {
        id: crypto.randomUUID(),
        campo,
        operador,
        valores: valoresLista,
        rotuloValor: `${campo.rotulo} ${rotuloOperador} ${rotulos}`,
      };
    } else {
      filtro = {
        id: crypto.randomUUID(),
        campo,
        operador,
        valor,
        rotuloValor: `${campo.rotulo} ${rotuloOperador} ${rotuloDoValorUnico(valor)}`,
      };
    }
    onAdicionar(filtro);
    selecionarCampo("");
  }

  const campoOrdenados = useMemo(
    () => [...campos].sort((a, b) => a.rotulo.localeCompare(b.rotulo)),
    [campos],
  );

  if (carregandoCampos) {
    return <p className="text-sm text-muted-foreground">{textos.carregandoCampos}</p>;
  }
  if (erroCampos) {
    return (
      <p role="alert" className="text-sm text-destructive">
        {textos.erroCampos}
      </p>
    );
  }

  return (
    <div className="flex flex-wrap items-end gap-2">
      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        {textos.campo}
        <select
          className="h-8 min-w-40 rounded-md border border-border bg-background px-2 text-sm text-foreground"
          value={apelido}
          onChange={(evento) => selecionarCampo(evento.target.value)}
        >
          <option value="">{textos.selecionarCampo}</option>
          {campoOrdenados.map((c) => (
            <option key={c.apelido} value={c.apelido}>
              {c.rotulo}
            </option>
          ))}
        </select>
      </label>

      {campo && (
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          {textos.operador}
          <select
            className="h-8 min-w-36 rounded-md border border-border bg-background px-2 text-sm text-foreground"
            value={operador}
            onChange={(evento) => selecionarOperador(evento.target.value as OperadorDeFiltro | "")}
          >
            <option value="">{textos.selecionarOperador}</option>
            {campo.operadores.map((op) => (
              <option key={op} value={op}>
                {textos.operadores[ROTULOS_DE_OPERADOR[op]]}
              </option>
            ))}
          </select>
        </label>
      )}

      {campo && operador && aridade === "UM" && (
        <ValorUnico campo={campo} valor={valor} onChange={setValor} opcoes={opcoesReferencia} textos={textos} textosStatus={textosStatus} />
      )}

      {campo && operador && aridade === "DOIS" && (
        <>
          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            {textos.valorInicial}
            <Input className="h-8 w-32" type={campo.tipo === "DATA" ? "date" : campo.tipo === "NUMERO" ? "number" : "text"} value={valor} onChange={(e) => setValor(e.target.value)} />
          </label>
          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            {textos.valorFinal}
            <Input className="h-8 w-32" type={campo.tipo === "DATA" ? "date" : campo.tipo === "NUMERO" ? "number" : "text"} value={valorAte} onChange={(e) => setValorAte(e.target.value)} />
          </label>
        </>
      )}

      {campo && operador && aridade === "LISTA" && (
        <ValorLista
          campo={campo}
          valores={valoresLista}
          onChange={setValoresLista}
          opcoes={opcoesReferencia}
          textos={textos}
          textosStatus={textosStatus}
        />
      )}

      <Button type="button" size="sm" disabled={!podeAdicionar()} onClick={adicionar}>
        {textos.adicionar}
      </Button>
    </div>
  );
}

function ValorUnico({
  campo,
  valor,
  onChange,
  opcoes,
  textos,
  textosStatus,
}: {
  campo: CampoFiltravel;
  valor: string;
  onChange: (valor: string) => void;
  opcoes: { id: string; rotulo: string }[];
  textos: TextosFiltros;
  textosStatus: TextosStatus;
}) {
  if (campo.tipo === "REFERENCIA" && opcoes.length > 0) {
    return (
      <SeletorUnico valor={valor} onChange={onChange} opcoes={opcoes} textos={textos} />
    );
  }
  if (campo.tipo === "STATUS") {
    return (
      <SeletorUnico
        valor={valor}
        onChange={onChange}
        opcoes={(Object.keys(ROTULOS_DE_STATUS) as StatusBasicoLead[]).map((s) => ({
          id: s,
          rotulo: textosStatus[ROTULOS_DE_STATUS[s]],
        }))}
        textos={textos}
      />
    );
  }
  if (campo.tipo === "LISTA") {
    return (
      <SeletorUnico
        valor={valor}
        onChange={onChange}
        opcoes={campo.opcoes.map((o) => ({ id: o, rotulo: o }))}
        textos={textos}
      />
    );
  }
  if (campo.tipo === "BOOLEANO") {
    return (
      <SeletorUnico
        valor={valor}
        onChange={onChange}
        opcoes={[
          { id: "true", rotulo: "true" },
          { id: "false", rotulo: "false" },
        ]}
        textos={textos}
      />
    );
  }
  return (
    <label className="flex flex-col gap-1 text-xs text-muted-foreground">
      {textos.valor}
      <Input
        className="h-8 w-40"
        type={campo.tipo === "DATA" ? "date" : campo.tipo === "NUMERO" ? "number" : "text"}
        value={valor}
        onChange={(evento) => onChange(evento.target.value)}
      />
    </label>
  );
}

function SeletorUnico({
  valor,
  onChange,
  opcoes,
  textos,
}: {
  valor: string;
  onChange: (valor: string) => void;
  opcoes: { id: string; rotulo: string }[];
  textos: TextosFiltros;
}) {
  return (
    <label className="flex flex-col gap-1 text-xs text-muted-foreground">
      {textos.valor}
      <select
        className="h-8 min-w-40 rounded-md border border-border bg-background px-2 text-sm text-foreground"
        value={valor}
        onChange={(evento) => onChange(evento.target.value)}
      >
        <option value="">{textos.selecionarValor}</option>
        {opcoes.map((o) => (
          <option key={o.id} value={o.id}>
            {o.rotulo}
          </option>
        ))}
      </select>
    </label>
  );
}

function ValorLista({
  campo,
  valores,
  onChange,
  opcoes,
  textos,
  textosStatus,
}: {
  campo: CampoFiltravel;
  valores: string[];
  onChange: (valores: string[]) => void;
  opcoes: { id: string; rotulo: string }[];
  textos: TextosFiltros;
  textosStatus: TextosStatus;
}) {
  const opcoesDisponiveis =
    campo.tipo === "STATUS"
      ? (Object.keys(ROTULOS_DE_STATUS) as StatusBasicoLead[]).map((s) => ({
          id: s,
          rotulo: textosStatus[ROTULOS_DE_STATUS[s]],
        }))
      : campo.tipo === "LISTA"
        ? campo.opcoes.map((o) => ({ id: o, rotulo: o }))
        : opcoes;

  if (opcoesDisponiveis.length === 0) {
    // Campo de referencia sem catalogo conhecido: ainda funciona, so sem nomes bonitos.
    return (
      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        {textos.valor}
        <Input
          className="h-8 w-56"
          placeholder="valor1, valor2"
          value={valores.join(", ")}
          onChange={(evento) =>
            onChange(
              evento.target.value
                .split(",")
                .map((v) => v.trim())
                .filter(Boolean),
            )
          }
        />
      </label>
    );
  }

  return (
    <label className="flex flex-col gap-1 text-xs text-muted-foreground">
      {textos.valor}
      <select
        multiple
        className="h-20 min-w-40 rounded-md border border-border bg-background px-2 py-1 text-sm text-foreground"
        value={valores}
        onChange={(evento) => onChange(Array.from(evento.target.selectedOptions).map((o) => o.value))}
      >
        {opcoesDisponiveis.map((o) => (
          <option key={o.id} value={o.id}>
            {o.rotulo}
          </option>
        ))}
      </select>
    </label>
  );
}
