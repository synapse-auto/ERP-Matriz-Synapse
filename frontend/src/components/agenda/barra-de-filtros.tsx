"use client";

import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { ChevronDown, Search, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { SeletorMultiplo } from "@/components/ui/seletor-multiplo";
import type {
  EtapaAtendimento,
  CanalResumo,
  TagDoLead,
} from "@/lib/lead/types";
import type { UsuarioEquipe } from "@/lib/equipe/types";
import type {
  CampoFiltravel,
  FiltroAtivo,
  FiltrosRapidosAgenda,
  OperadorDeFiltro,
  StatusBasicoLead,
} from "@/lib/agenda/types";
import { SEM_RESPONSAVEL } from "@/lib/agenda/use-agenda";
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
function opcoesDeReferencia(
  apelido: string,
  refs: Referencias,
): { id: string; rotulo: string }[] {
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
  onTentarCamposNovamente: () => void | Promise<unknown>;
  filtrosAtivos: FiltroAtivo[];
  filtrosRapidos: FiltrosRapidosAgenda;
  cidades: string[];
  referencias: Referencias;
  textos: TextosFiltros;
  textosStatus: TextosStatus;
  textoSemResponsavel: string;
  /** "Exibindo N de M leads" (E17b §Bloco 3) — mesma linha da barra, como no protótipo. */
  contador: ReactNode;
  onAdicionar: (filtro: FiltroAtivo) => void;
  onFiltrosRapidosChange: (filtros: FiltrosRapidosAgenda) => void;
  onRemover: (id: string) => void;
  onLimparTudo: () => void;
}

export function BarraDeFiltros({
  campos,
  carregandoCampos,
  erroCampos,
  onTentarCamposNovamente,
  filtrosAtivos,
  filtrosRapidos,
  cidades,
  referencias,
  textos,
  textosStatus,
  textoSemResponsavel,
  contador,
  onAdicionar,
  onFiltrosRapidosChange,
  onRemover,
  onLimparTudo,
}: Props) {
  const [avancadosAbertos, setAvancadosAbertos] = useState(false);
  const cidadesConhecidas = useMemo(
    () =>
      [...new Set([...filtrosRapidos.cidades, ...cidades])].sort((a, b) =>
        a.localeCompare(b, "pt-BR"),
      ),
    [filtrosRapidos.cidades, cidades],
  );

  const opcoesRapidas = {
    etapas: referencias.etapas.map((etapa) => ({
      valor: etapa.id,
      rotulo: etapa.nome,
    })),
    atendentes: [
      ...referencias.equipe.map((usuario) => ({
        valor: usuario.id,
        rotulo: usuario.nome,
      })),
      { valor: SEM_RESPONSAVEL, rotulo: textoSemResponsavel },
    ],
    cidades: cidadesConhecidas.map((cidade) => ({
      valor: cidade,
      rotulo: cidade,
    })),
    tags: referencias.tags.map((tag) => ({ valor: tag.id, rotulo: tag.nome })),
  };

  function atualizarRapido(
    chave: "etapas" | "atendentes" | "cidades" | "tags",
    valores: string[],
  ) {
    onFiltrosRapidosChange({ ...filtrosRapidos, [chave]: valores });
  }

  const chipsRapidos = [
    ...(filtrosRapidos.busca
      ? [
          {
            chave: "busca" as const,
            valor: filtrosRapidos.busca,
            rotulo: `${textos.busca}: ${filtrosRapidos.busca}`,
          },
        ]
      : []),
    ...(["etapas", "atendentes", "cidades", "tags"] as const).flatMap((chave) =>
      filtrosRapidos[chave].map((valor) => {
        const opcao = opcoesRapidas[chave].find((item) => item.valor === valor);
        const titulo = {
          etapas: textos.etapa,
          atendentes: textos.atendente,
          cidades: textos.cidade,
          tags: textos.tag,
        }[chave];
        return { chave, valor, rotulo: `${titulo}: ${opcao?.rotulo ?? valor}` };
      }),
    ),
  ];

  function removerRapido(
    chave: (typeof chipsRapidos)[number]["chave"],
    valor: string,
  ) {
    if (chave === "busca") {
      onFiltrosRapidosChange({ ...filtrosRapidos, busca: "" });
      return;
    }
    atualizarRapido(
      chave,
      filtrosRapidos[chave].filter((atual) => atual !== valor),
    );
  }

  const haFiltros = chipsRapidos.length > 0 || filtrosAtivos.length > 0;

  return (
    <div className="mb-3 space-y-2 border-b border-border pb-3">
      <div className="flex flex-wrap items-center gap-2.5">
        <div className="relative min-w-72 max-w-[380px] flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={filtrosRapidos.busca}
            aria-label={textos.busca}
            placeholder={textos.buscaPlaceholder}
            className="h-10 rounded-lg pl-9"
            onChange={(evento) =>
              onFiltrosRapidosChange({
                ...filtrosRapidos,
                busca: evento.target.value,
              })
            }
          />
        </div>
        <SeletorMultiplo
          className="min-w-28"
          ariaLabel={textos.etapa}
          valores={filtrosRapidos.etapas}
          opcoes={opcoesRapidas.etapas}
          placeholder={textos.etapa}
          onChange={(valores) => atualizarRapido("etapas", valores)}
        />
        <SeletorMultiplo
          className="min-w-32"
          ariaLabel={textos.atendente}
          valores={filtrosRapidos.atendentes}
          opcoes={opcoesRapidas.atendentes}
          placeholder={textos.atendente}
          onChange={(valores) => atualizarRapido("atendentes", valores)}
        />
        <SeletorMultiplo
          className="min-w-28"
          ariaLabel={textos.cidade}
          valores={filtrosRapidos.cidades}
          opcoes={opcoesRapidas.cidades}
          placeholder={textos.cidade}
          onChange={(valores) => atualizarRapido("cidades", valores)}
        />
        <SeletorMultiplo
          className="min-w-24"
          ariaLabel={textos.tag}
          valores={filtrosRapidos.tags}
          opcoes={opcoesRapidas.tags}
          placeholder={textos.tag}
          onChange={(valores) => atualizarRapido("tags", valores)}
        />
        <div className="ml-auto shrink-0">{contador}</div>
      </div>

      <div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-expanded={avancadosAbertos}
          onClick={() => setAvancadosAbertos((abertos) => !abertos)}
        >
          {textos.avancados}
          <ChevronDown
            className={
              avancadosAbertos
                ? "rotate-180 transition-transform"
                : "transition-transform"
            }
          />
        </Button>
        {avancadosAbertos && (
          <div className="mt-2 border-t border-border pt-3">
            <FormularioDeNovoFiltro
              campos={campos}
              carregandoCampos={carregandoCampos}
              erroCampos={erroCampos}
              onTentarNovamente={onTentarCamposNovamente}
              referencias={referencias}
              textos={textos}
              textosStatus={textosStatus}
              onAdicionar={onAdicionar}
            />
          </div>
        )}
      </div>

      {haFiltros && (
        <div className="flex flex-wrap items-center gap-1.5 border-t border-border pt-2">
          {chipsRapidos.map((filtro) => (
            <ChipDeFiltro
              key={`${filtro.chave}-${filtro.valor}`}
              rotulo={filtro.rotulo}
              textoRemover={textos.limparTudo}
              onRemover={() => removerRapido(filtro.chave, filtro.valor)}
            />
          ))}
          {filtrosAtivos.map((filtro) => (
            <ChipDeFiltro
              key={filtro.id}
              rotulo={filtro.rotuloValor}
              textoRemover={textos.limparTudo}
              onRemover={() => onRemover(filtro.id)}
            />
          ))}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onLimparTudo}
          >
            {textos.limparTudo}
          </Button>
        </div>
      )}
    </div>
  );
}

function ChipDeFiltro({
  rotulo,
  textoRemover,
  onRemover,
}: {
  rotulo: string;
  textoRemover: string;
  onRemover: () => void;
}) {
  return (
    <span className="inline-flex max-w-full items-center gap-1 rounded-md border border-primary/20 bg-primary/10 py-0.5 pr-1 pl-2 text-xs font-semibold text-primary">
      {rotulo}
      <button
        type="button"
        aria-label={`${textoRemover}: ${rotulo}`}
        className="rounded-sm p-0.5 text-primary/70 hover:bg-primary/10 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onClick={onRemover}
      >
        <X className="size-3.5" />
      </button>
    </span>
  );
}

const ROTULOS_DE_OPERADOR: Record<
  OperadorDeFiltro,
  keyof TextosFiltros["operadores"]
> = {
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
  onTentarNovamente,
  referencias,
  textos,
  textosStatus,
  onAdicionar,
}: {
  campos: CampoFiltravel[];
  carregandoCampos: boolean;
  erroCampos: boolean;
  onTentarNovamente: () => void | Promise<unknown>;
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
  const opcoesReferencia = campo
    ? opcoesDeReferencia(campo.apelido, referencias)
    : [];

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
    if (campo?.tipo === "STATUS")
      return textosStatus[ROTULOS_DE_STATUS[bruto as StatusBasicoLead]];
    return bruto;
  }

  function podeAdicionar(): boolean {
    if (!campo || !operador) return false;
    if (aridade === "NENHUM") return true;
    if (aridade === "DOIS")
      return valor.trim() !== "" && valorAte.trim() !== "";
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
    return (
      <p className="text-sm text-muted-foreground">{textos.carregandoCampos}</p>
    );
  }
  if (erroCampos) {
    return (
      <ErroDeCarregamento
        mensagem={textos.erroCampos}
        onTentarNovamente={onTentarNovamente}
      />
    );
  }

  return (
    <div className="flex flex-wrap items-end gap-2">
      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        {textos.campo}
        <Seletor
          className="min-w-40"
          valor={apelido}
          ariaLabel={textos.campo}
          placeholder={textos.selecionarCampo}
          opcoes={campoOrdenados.map((c) => ({
            valor: c.apelido,
            rotulo: c.rotulo,
          }))}
          onChange={selecionarCampo}
        />
      </label>

      {campo && (
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          {textos.operador}
          <Seletor
            className="min-w-36"
            valor={operador}
            ariaLabel={textos.operador}
            placeholder={textos.selecionarOperador}
            opcoes={campo.operadores.map((op) => ({
              valor: op,
              rotulo: textos.operadores[ROTULOS_DE_OPERADOR[op]],
            }))}
            onChange={(valor) =>
              selecionarOperador(valor as OperadorDeFiltro | "")
            }
          />
        </label>
      )}

      {campo && operador && aridade === "UM" && (
        <ValorUnico
          campo={campo}
          valor={valor}
          onChange={setValor}
          opcoes={opcoesReferencia}
          textos={textos}
          textosStatus={textosStatus}
        />
      )}

      {campo && operador && aridade === "DOIS" && (
        <>
          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            {textos.valorInicial}
            {campo.tipo === "DATA" ? (
              <SeletorData
                className="w-40"
                valor={valor}
                placeholder={textos.valorInicial}
                onChange={setValor}
              />
            ) : (
              <Input
                className="h-8 w-32"
                type={campo.tipo === "NUMERO" ? "number" : "text"}
                value={valor}
                onChange={(e) => setValor(e.target.value)}
              />
            )}
          </label>
          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            {textos.valorFinal}
            {campo.tipo === "DATA" ? (
              <SeletorData
                className="w-40"
                valor={valorAte}
                placeholder={textos.valorFinal}
                onChange={setValorAte}
              />
            ) : (
              <Input
                className="h-8 w-32"
                type={campo.tipo === "NUMERO" ? "number" : "text"}
                value={valorAte}
                onChange={(e) => setValorAte(e.target.value)}
              />
            )}
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

      <Button
        type="button"
        size="sm"
        disabled={!podeAdicionar()}
        onClick={adicionar}
      >
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
      <SeletorUnico
        valor={valor}
        onChange={onChange}
        opcoes={opcoes}
        textos={textos}
      />
    );
  }
  if (campo.tipo === "STATUS") {
    return (
      <SeletorUnico
        valor={valor}
        onChange={onChange}
        opcoes={(Object.keys(ROTULOS_DE_STATUS) as StatusBasicoLead[]).map(
          (s) => ({
            id: s,
            rotulo: textosStatus[ROTULOS_DE_STATUS[s]],
          }),
        )}
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
      {campo.tipo === "DATA" ? (
        <SeletorData
          className="w-40"
          valor={valor}
          placeholder={textos.valor}
          onChange={onChange}
        />
      ) : (
        <Input
          className="h-8 w-40"
          type={campo.tipo === "NUMERO" ? "number" : "text"}
          value={valor}
          onChange={(evento) => onChange(evento.target.value)}
        />
      )}
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
      <Seletor
        className="min-w-40"
        valor={valor}
        ariaLabel={textos.valor}
        placeholder={textos.selecionarValor}
        opcoes={opcoes.map((opcao) => ({
          valor: opcao.id,
          rotulo: opcao.rotulo,
        }))}
        onChange={onChange}
      />
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
      <SeletorMultiplo
        className="min-w-40"
        valores={valores}
        placeholder={textos.selecionarValor}
        opcoes={opcoesDisponiveis.map((opcao) => ({
          valor: opcao.id,
          rotulo: opcao.rotulo,
        }))}
        onChange={onChange}
      />
    </label>
  );
}
