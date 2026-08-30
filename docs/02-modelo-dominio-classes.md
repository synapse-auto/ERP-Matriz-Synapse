# 02. Modelo de Domínio — Diagrama de Classes

Diagrama dividido por módulo (bounded context) para legibilidade. Renderiza em qualquer visualizador Mermaid (GitHub, Notion, VS Code + extensão Mermaid).

Convenção: `+` público, `#` protegido/somente leitura externa. Enums marcados com `<<enumeration>>`.

## 1. Equipe (Usuários, Papéis, Presença)

```mermaid
classDiagram
    class Usuario {
        +UUID id
        +String nome
        +String email
        +String senhaHash
        +PapelUsuario papel
        +StatusPresenca statusPresenca
        +boolean ativo
        +Instant criadoEm
        +Instant senhaAlteradaEm
        +autenticar(senha) boolean
        +definirPresenca(status)
        +precisaTrocarSenha() boolean
    }

    class PapelUsuario {
        <<enumeration>>
        ATENDENTE
        SUBGESTOR
        GESTOR
        ADMINISTRADOR
    }

    class StatusPresenca {
        <<enumeration>>
        ONLINE
        AUSENTE
        OFFLINE
    }

    class Avaliacao {
        +UUID id
        +UUID atendimentoId
        +UUID atendenteId
        +int nota
        +String comentario
        +Instant criadoEm
    }

    class RotinaDisponibilidade {
        +UUID id
        +DiaSemana diaSemana
        +String nome
        +TipoRotina tipo
        +boolean ativo
    }

    class TipoRotina {
        <<enumeration>>
        PLANTAO
        FECHADO
    }

    class DisponibilidadeAtendenteIA {
        +UUID atendenteId
        +boolean disponivelParaIA
        +Instant atualizadoEm
    }

    class HorarioTrabalho {
        +UUID id
        +PapelUsuario aplicavelA
        +LocalTime inicio
        +LocalTime fim
        +DiaSemana diaSemana
    }

    Usuario "1" --> "1" PapelUsuario
    Usuario "1" --> "1" StatusPresenca
    Usuario "1" --> "*" Avaliacao : recebe
    RotinaDisponibilidade "*" --> "*" Usuario : atendentes atribuídos
    Usuario "1" --> "0..1" DisponibilidadeAtendenteIA
```

*Cobre RF-CRM-01/02/46/47/54/74/75/81.*

## 2. CRM Core (Lead, Tags, Agenda, Lembretes, Mensagens Programadas)

`Lead.codigo` é identificador interno numérico da instância (somente dígitos, até 20, zeros à esquerda preservados). Opcional; vazio na edição vira `null`. Não é campo customizado: precisa aparecer no card da lista de Atendimentos, e `dadosCustomizados` não entra em projeção de listagem. A Agenda (`LeadResumo`) não o carrega. Normalização em `CodigoDoLead`; persistência em `lead.codigo` (V47).

```mermaid
classDiagram
    class Lead {
        +UUID id
        +String nome
        +String foto
        +String telefone
        +String email
        +String cpf
        +String empresa
        +String codigo
        +String localizacao
        +UUID canalOrigemId
        +StatusBasicoLead statusBasico
        +UUID etapaAtendimentoId
        +UUID atendenteResponsavelId
        +String notas
        +String resumoIA
        +int numAtendimentos
        +int numMensagens
        +Instant criadoEm
    }

    class StatusBasicoLead {
        <<enumeration>>
        IA
        EM_ATENDIMENTO
        FINALIZADO
    }

    class EtapaAtendimento {
        +UUID id
        +String nome
        +int ordem
        +String corVisual
        +ResultadoEtapa resultado
    }

    class ResultadoEtapa {
        <<enumeration>>
        EM_ANDAMENTO
        GANHO
        PERDIDO
    }

    class Tag {
        +UUID id
        +String nome
        +String cor
        +String icone
    }

    class LeadTag {
        +UUID leadId
        +UUID tagId
    }

    class Lembrete {
        +UUID id
        +UUID leadId
        +UUID atendenteId
        +String texto
        +Instant dataHora
        +boolean origemAutomatica
        +StatusLembrete status
    }

    class StatusLembrete {
        <<enumeration>>
        PENDENTE
        CONCLUIDO
    }

    class MensagemProgramada {
        +UUID id
        +UUID leadId
        +UUID atendenteId
        +String conteudo
        +Instant dataEnvio
        +StatusMensagemProgramada status
    }

    class StatusMensagemProgramada {
        <<enumeration>>
        AGENDADA
        ENVIADA
        CANCELADA
    }

    class MensagemRapida {
        +UUID id
        +UUID atendenteId
        +String palavraChave
        +String conteudo
        +String tipoMidia
    }

    class EventoTimeline {
        +UUID id
        +UUID leadId
        +UUID atendimentoId
        +String tipo
        +String descricao
        +OrigemEvento origem
        +UUID atorId
        +Map~String,Object~ dados
        +Instant criadoEm
    }

    class OrigemEvento {
        <<enumeration>>
        SISTEMA
        AUTOMACAO
        USUARIO
    }

    Lead "1" --> "1" StatusBasicoLead
    Lead "1" --> "1" EtapaAtendimento
    Lead "1" --> "0..1" Usuario : atendente responsável
    Lead "1" --> "*" LeadTag
    Tag "1" --> "*" LeadTag
    Lead "1" --> "*" Lembrete
    Lead "1" --> "*" MensagemProgramada
    Lead "1" --> "*" EventoTimeline
    Usuario "1" --> "*" MensagemRapida
```

*Cobre RF-CRM-14 a 19, 23 a 29, 48 a 53, 57 a 64, 70/71/77.*

## 3. Atendimento (Conversas e Mensagens)

```mermaid
classDiagram
    class Canal {
        +UUID id
        +String nome
        +TipoCanal tipo
        +boolean ativo
    }

    class TipoCanal {
        <<enumeration>>
        WHATSAPP
        OUTRO
    }

    class Atendimento {
        +UUID id
        +UUID leadId
        +UUID canalId
        +UUID atendenteId
        +StatusAtendimento status
        +Instant iniciadoEm
        +Instant finalizadoEm
        +transferirPara(atendenteId)
        +finalizar()
    }

    class StatusAtendimento {
        <<enumeration>>
        EM_IA
        EM_ATENDIMENTO
        FINALIZADO
    }

    class Mensagem {
        +UUID id
        +UUID atendimentoId
        +RemetenteTipo remetenteTipo
        +UUID remetenteId
        +TipoMensagem tipo
        +String conteudo
        +String midiaUrl
        +StatusEntrega statusEntrega
        +Instant enviadoEm
    }

    class RemetenteTipo {
        <<enumeration>>
        LEAD
        ATENDENTE
        SISTEMA
        IA
    }

    class TipoMensagem {
        <<enumeration>>
        TEXTO
        AUDIO
        IMAGEM
        DOCUMENTO
    }

    class StatusEntrega {
        <<enumeration>>
        ENVIADO
        ENTREGUE
        LIDO
    }

    Canal "1" --> "*" Atendimento
    Lead "1" --> "*" Atendimento
    Usuario "1" --> "*" Atendimento : atende
    Atendimento "1" --> "*" Mensagem
    Mensagem "1" --> "1" RemetenteTipo
    Mensagem "1" --> "1" TipoMensagem
    Mensagem "1" --> "1" StatusEntrega
```

*Cobre RF-CRM-06 a 22, 65 a 69 e a RNF-CRM-01 (ultra-regra de estabilidade).*

## 4. Campanhas

```mermaid
classDiagram
    class Campanha {
        +UUID id
        +String nome
        +UUID filtroPublicoId
        +Instant dataInicio
        +Instant dataFim
        +int intervaloEnvioDias
        +StatusCampanha status
        +UUID criadoPorId
        +ativar()
    }

    class StatusCampanha {
        <<enumeration>>
        RASCUNHO
        ATIVA
        PAUSADA
        ENCERRADA
    }

    class CampanhaMensagem {
        +UUID id
        +UUID campanhaId
        +int ordem
        +String conteudo
        +String tipoMidia
    }

    class CampanhaMensagemMetrica {
        +UUID id
        +UUID campanhaMensagemId
        +UUID leadId
        +boolean enviada
        +boolean visualizou
        +boolean respondeu
        +boolean entrouAtendimento
        +int numMensagensLead
        +boolean fechado
    }

    class FiltroModular {
        +UUID id
        +String nome
        +ContextoFiltro contexto
        +JsonB criterios
        +UUID criadoPorId
        +contar() int
    }

    class ContextoFiltro {
        <<enumeration>>
        ATENDIMENTOS
        AGENDA
        CAMPANHA
    }

    Campanha "1" --> "1" FiltroModular : público
    Campanha "1" --> "*" CampanhaMensagem
    CampanhaMensagem "1" --> "*" CampanhaMensagemMetrica
    CampanhaMensagemMetrica "*" --> "1" Lead
```

*Cobre RF-CRM-04/05/39 a 44.*

## 5. Automação — Configuração (fonte da verdade dos parâmetros)

```mermaid
classDiagram
    class ConfiguracaoAutomacao {
        +String chave
        +String valor
        +String unidade
        +String tipo
        +Double min
        +Double max
        +String descricao
        +UUID atualizadoPorId
        +Instant atualizadoEm
        +validar(valor) boolean
    }

    class RegraFollowUp {
        +UUID id
        +String nome
        +int tempoMinutos
        +String texto
        +boolean ativo
    }

    class RegraFidelizacao {
        +UUID id
        +int diasSemContato
        +String mensagem
        +boolean ativo
    }

    class MensagemFestiva {
        +UUID id
        +LocalDate data
        +String texto
        +boolean ativo
    }

    class ConfiguracaoResumoIA {
        +boolean ativo
        +GatilhoResumo gatilho
        +int quantidadeMensagens
    }

    class GatilhoResumo {
        <<enumeration>>
        A_CADA_X_MENSAGENS
        AO_FINALIZAR
        AMBOS
    }

    class StatusAutomacaoTelemetria {
        +UUID id
        +long mensagensEnviadas
        +long clientesTransferidos
        +boolean conexaoAutomacaoAtiva
        +boolean crmOnline
        +Instant atualizadoEm
    }
```

*Cobre RF-CRM-34 a 38, 38a a 38e, 72 a 76 — todo parâmetro numérico/temporal da Automação vive aqui, nunca em código (RN-CRM-07).*

## 6. Chat Interno e Banco de Arquivos

```mermaid
classDiagram
    class ChatInternoConversa {
        +UUID id
        +TipoConversa tipo
        +Instant criadoEm
    }

    class TipoConversa {
        <<enumeration>>
        DIRETA
        GRUPO
    }

    class ChatInternoParticipante {
        +UUID conversaId
        +UUID usuarioId
    }

    class ChatInternoMensagem {
        +UUID id
        +UUID conversaId
        +UUID remetenteId
        +TipoMensagem tipo
        +String conteudo
        +String midiaUrl
        +Instant enviadoEm
    }

    class ArquivoBanco {
        +UUID id
        +String nome
        +String tipo
        +String url
        +long tamanhoBytes
        +String descricaoMetadados
        +UUID enviadoPorId
        +Instant criadoEm
    }

    ChatInternoConversa "1" --> "*" ChatInternoParticipante
    ChatInternoConversa "1" --> "*" ChatInternoMensagem
    Usuario "1" --> "*" ArquivoBanco : envia
```

*Cobre RF-CRM-45, 55, 56, 78.*

## Observações de modelagem

- **`FiltroModular`** é compartilhado entre Agenda, Atendimentos e Campanhas (RF-CRM-04/05/40) — é uma entidade própria, não duplicada por tela.
- **`EtapaAtendimento`** é uma tabela de configuração (não enum fixo em código), porque a etapa é definida pela Automação (RF-AUT-12) e pode mudar sem deploy do CRM. Seu `ResultadoEtapa` dá semântica comercial estável (`EM_ANDAMENTO`, `GANHO`, `PERDIDO`) sem deduzir venda pelo nome configurável; um índice único parcial permite no máximo uma etapa `GANHO`.
- **`ConfiguracaoAutomacao`** usa modelo chave-valor tipado deliberadamente (em vez de uma coluna por parâmetro), para satisfazer RF-CRM-38e (extensibilidade: novo parâmetro = nova linha, nunca nova coluna/deploy).
- **Enums vs. tabelas**: `PapelUsuario`, `StatusPresenca`, `TipoMensagem`, `StatusEntrega` são enums porque são estáveis e fazem parte do contrato do domínio; `EtapaAtendimento` e `Canal` são tabelas porque são geridos/expandidos por configuração (multicanal, novas etapas).
