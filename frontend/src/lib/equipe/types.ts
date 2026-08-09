export type PapelGerenciavel="ATENDENTE"|"SUBGESTOR";export type StatusPresenca="ONLINE"|"AUSENTE"|"OFFLINE";
export interface UsuarioEquipe{id:string;nome:string;email:string;papel:PapelGerenciavel|"GESTOR"|"ADMINISTRADOR";statusPresenca:StatusPresenca;ativo:boolean}
export interface AvaliacoesEquipe{mediaGeral:number;total:number;porAtendente:{atendenteId:string;atendenteNome:string;media:number;total:number}[]}
/** GET /api/v1/me (E17) — nome, papel e presença de quem está autenticado. */
export interface MeuUsuario{nome:string;papel:PapelGerenciavel|"GESTOR"|"ADMINISTRADOR";presenca:StatusPresenca}
