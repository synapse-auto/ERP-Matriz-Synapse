import { z } from "zod";

/** Fallback local para catálogos de instâncias ainda anteriores à integração. */
export const TEXTOS_DARWIN = {
  versaoContrato: "darwin-medico-cpf-v1",
  consultar: "Consultar na Darwin", titulo: "Consultar Darwin médico",
  descricao: "Consulte um CPF conhecido, confira o cadastro e confirme somente os campos que deseja aplicar.",
  cpfConsulta: "CPF para consulta", buscarCpf: "Buscar por CPF", cadastroConsultado: "Cadastro correspondente ao CPF consultado",
  cpfConsultaAjuda: "Informe um CPF conhecido. Esta consulta não descobre CPF pelo telefone. Confirme que o cadastro corresponde a este lead antes de aplicar.",
  cpfConsultaInvalido: "Informe um CPF válido, com 11 dígitos ou no formato 000.000.000-00.",
  carregando: "Consultando dados na Darwin…", aplicar: "Aplicar seleção", cancelar: "Cancelar",
  novamente: "Consultar novamente", sucesso: "Dados aplicados à ficha.",
  nome: "Nome", cpf: "CPF", atual: "Atual", encontrado: "Encontrado", ausente: "Não informado",
  selecionarNome: "Aplicar nome", selecionarCpf: "Aplicar CPF",
  cpfInvalido: "CPF inválido na origem; não será aplicado.",
  SEM_RESULTADO: "Nenhum paciente com esse CPF foi encontrado no escopo da credencial Darwin.",
  AMBIGUO: "A resposta não identifica um cadastro único. Nenhum dado será aplicado.",
  SEM_IDENTIDADE: "É necessário informar um CPF conhecido para consultar o Darwin médico.",
  limite: "A consulta não pôde ser concluída integralmente.",
  sessao: "Cadastro consultado", erro: "Não foi possível consultar a Darwin. O chat continua disponível.",
  conflito: "A ficha mudou. Consulte novamente antes de aplicar.",
  autorizacao: "A Darwin recusou a credencial configurada. Solicite a verificação ao administrador.",
  limiteProvedor: "A Darwin limitou as consultas. Tente novamente mais tarde.",
  configuracao: "A origem ou a credencial do Darwin médico não está configurada corretamente.",
  identidade: "O CPF retornado não corresponde ao consultado. Nenhum dado será aplicado.",
  comprovante: "A confirmação expirou ou não é válida. Consulte novamente.",
} as const;

const BaseDarwinTextosSchema = z.object(Object.fromEntries(
  Object.entries(TEXTOS_DARWIN).map(([chave, valor]) => [chave, z.string().default(valor)]),
) as { [K in keyof typeof TEXTOS_DARWIN]: z.ZodDefault<z.ZodString> });

// Catálogos antigos não podem continuar prometendo busca por telefone/sessões.
// Preserva rótulos genéricos personalizados; só substitui os textos cujo contrato mudou.
export const DarwinTextosSchema = z.preprocess(valor => {
  if (typeof valor !== "object" || valor === null || Array.isArray(valor)) return valor;
  const textos = valor as Record<string, unknown>;
  if (textos.versaoContrato === TEXTOS_DARWIN.versaoContrato) return textos;
  const atualizados = { ...textos };
  for (const chave of ["versaoContrato", "titulo", "descricao", "SEM_RESULTADO", "AMBIGUO", "SEM_IDENTIDADE", "limite", "sessao", "configuracao", "identidade"] as const) {
    atualizados[chave] = TEXTOS_DARWIN[chave];
  }
  return atualizados;
}, BaseDarwinTextosSchema).default(TEXTOS_DARWIN);
