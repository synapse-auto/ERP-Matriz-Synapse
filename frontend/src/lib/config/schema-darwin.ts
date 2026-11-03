import { z } from "zod";

/** Fallback local para catálogos de instâncias ainda anteriores à integração. */
export const TEXTOS_DARWIN = {
  consultar: "Consultar na Darwin", titulo: "Dados encontrados na Darwin",
  descricao: "Confira os valores da mesma sessão e selecione os campos que deseja aplicar.",
  carregando: "Consultando dados na Darwin…", aplicar: "Aplicar seleção", cancelar: "Cancelar",
  novamente: "Consultar novamente", sucesso: "Dados aplicados à ficha.",
  nome: "Nome", cpf: "CPF", atual: "Atual", encontrado: "Encontrado", ausente: "Não informado",
  selecionarNome: "Aplicar nome", selecionarCpf: "Aplicar CPF",
  cpfInvalido: "CPF inválido na origem; não será aplicado.",
  SEM_RESULTADO: "Nenhum nome ou CPF foi encontrado nos campos configurados.",
  AMBIGUO: "Foram encontrados dados divergentes entre sessões. Nenhum dado será aplicado.",
  SEM_IDENTIDADE: "Este lead não possui telefone compatível com a consulta Darwin.",
  limite: "A consulta atingiu a janela configurada. Sessões mais antigas não foram consultadas.",
  sessao: "Sessão {id}", erro: "Não foi possível consultar a Darwin. O chat continua disponível.",
  conflito: "A ficha mudou. Consulte novamente antes de aplicar.",
  autorizacao: "A Darwin recusou a credencial configurada. Solicite a verificação ao administrador.",
  limiteProvedor: "A Darwin limitou as consultas. Tente novamente mais tarde.",
  configuracao: "O mapeamento Darwin está incompleto ou incompatível com os campos da conta.",
  identidade: "A identidade retornada não corresponde ao telefone deste lead. Nenhum dado será aplicado.",
  comprovante: "A confirmação expirou ou não é válida. Consulte novamente.",
} as const;

export const DarwinTextosSchema = z.object(Object.fromEntries(
  Object.entries(TEXTOS_DARWIN).map(([chave, valor]) => [chave, z.string().default(valor)]),
) as { [K in keyof typeof TEXTOS_DARWIN]: z.ZodDefault<z.ZodString> }).default(TEXTOS_DARWIN);
