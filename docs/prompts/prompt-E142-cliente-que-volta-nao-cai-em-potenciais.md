# Prompt E142 — Cliente que volta não cai em Potenciais

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/retorno-do-lead-finalizado`) e PR. **Sem merge, sem deploy.**
> **Somente backend.** Sem migration, sem frontend, sem mudança de RLS.
> `cd backend && ./mvnw -pl crm-app -am verify`.

Quando um cliente com atendimento já finalizado volta a escrever, o atendimento novo abre em
`EM_IA`, mas o **lead** continua `FINALIZADO` e amarrado ao `atendente_responsavel_id` de quem o
encerrou. Ele não entra em **Potenciais** e não fica elegível para o rodízio da automação.

Medido em produção em 02/09: dos **7** atendimentos na fila da IA, **6** estavam nesse estado.

---

## ⚠️ Leia isto antes de conferir qualquer coisa em produção

Este prompt foi escrito **antes** da V59 (E145), que já está na `main`. A V59 colocou
`status_basico = 'FINALIZADO'` no mesmo escape da RLS que `IA` já tinha:

```sql
atendente_responsavel_id = app_usuario_id() OR status_basico = 'IA' OR status_basico = 'FINALIZADO'
```

**Consequência: o sintoma mais visível deste bug sumiu, e o bug não foi corrigido.** Antes da V59
esses leads eram invisíveis para todo mundo menos o dono anterior. Agora eles aparecem — não
porque o estado passou a estar certo, mas porque `FINALIZADO` virou visível para todos.

Portanto:

- **Não conclua que o bug foi resolvido** porque você achou os leads na tela ou porque uma query
  sob RLS os devolveu. Confirme no estado, não na visibilidade.
- **Não "simplifique" esta etapa** dizendo que a V59 já resolveu. Ela não resolveu: um lead
  `FINALIZADO` não é `Potencial`, não entra no rodízio da automação e continua exibindo o dono
  anterior.
- O que continua quebrado é o **balde**, não o enxergar.

## A causa, já isolada — não reinvestigue

A entrada de mensagem mexe em `atendimento` e não mexe em `lead`:

| tabela | estado após o cliente voltar | correto? |
| --- | --- | --- |
| `atendimento` | `status = 'EM_IA'` | sim, o retorno abre assim |
| `lead` | `status_basico = 'FINALIZADO'`, dono antigo preservado | **não** |

`RegistrarMensagemRecebidaUseCase` abre `Atendimento.abrirComIa(...)` e o arquivo inteiro **não tem
uma única referência a `StatusBasicoLead`**. Confirmado na `main` de 03/09, depois da V59.

O invariante já está escrito em outro lugar. `TransferirAtendimentoUseCase`, ao devolver para a IA:

```java
// Volta para o grupo "Potenciais": sem dono, visivel a todos os atendentes.
leads.marcarStatus(antes.leadId(), StatusBasicoLead.IA);
```

O caminho de entrada é o único que não aplica.

## A correção

No `RegistrarMensagemRecebidaUseCase`, **quando e somente quando um atendimento novo é aberto**
(a variável `abriu`, linha 78), marque o lead como `IA`, na mesma transação, reutilizando
`leads.marcarStatus`.

`abriu == true` significa que o lead não tinha nenhum atendimento em aberto — logo, não tem dono
humano em curso. Não há lead a roubar de ninguém.

Quando já existe atendimento aberto (`abriu == false`), **não mexa em nada**: o lead está com um
humano ou com a IA, e ambos os estados já estão corretos.

## A consequência, decidida — registre-a no comentário

O cliente que volta passa a cair em **Potenciais**, elegível para o rodízio da automação, em vez de
continuar amarrado ao atendente que o finalizou. **É esse o comportamento pedido.** Escreva isso no
comentário junto da chamada, porque é uma decisão comercial e não uma consequência técnica óbvia —
quem ler depois precisa saber que foi de propósito.

O `atendente_responsavel_id` **não** é limpo: `status_basico = 'IA'` já basta, e o histórico de
quem atendeu por último continua útil na ficha. Não invente limpeza que o `devolverParaIa` também
não faz.

## Testes obrigatórios

Atenção ao desenho: **desde a V59, "o lead aparece para outro atendente" não prova nada** — ele
apareceria mesmo sem a correção. Toda asserção de visibilidade precisa ser sobre o **grupo**, e
precisa haver um caso de controle que separe um estado do outro.

1. **O caso dos seis:** lead com atendimento `FINALIZADO` e dono definido recebe mensagem nova →
   atendimento novo `EM_IA` **e** `lead.status_basico = 'IA'`.
2. **O lead entra em POTENCIAIS, sob RLS**, consultado por um atendente que **não** é o dono
   anterior. Asserção sobre o grupo retornado, não sobre "veio na lista".
3. **Controle — este é o teste que dá sentido ao 2.** Lead `FINALIZADO` que **não** voltou a
   escrever **não** aparece em `POTENCIAIS` para esse mesmo atendente, embora seja visível para ele
   por causa da V59. Se os testes 2 e 3 passarem juntos, a etapa está provada; se o 3 falhar, a
   consulta de Potenciais está apenas listando tudo que a RLS deixa passar, e isso é outro defeito
   a reportar — **não o corrija aqui, reporte**.
4. Lead com atendimento **aberto** recebendo mensagem: `status_basico` **inalterado** (não vira
   `IA` no meio de um atendimento humano).
5. Lead novo, criado pela própria mensagem: continua nascendo `IA`, sem regressão.
6. Contato iniciado pelo atendente (`IniciarNovoContatoUseCase`) seguido de resposta do cliente: o
   lead **continua** `EM_ATENDIMENTO` e não é devolvido para a fila.

## Fora do escopo

- **Não mexa na RLS**, nas policies, na V59, na RN-CRM-01 ou no `ux_lead_telefone`. Se você achar
  que a V59 está errada, **reporte no PR e pare** — é decisão do Marcondes, não desta etapa.
- Não limpe `atendente_responsavel_id`.
- Não mexa no botão "Reativar atendimento" nem em `IniciarNovoContatoUseCase` — reativar um lead
  finalizado que **nunca voltou a escrever** é decisão de produto separada, já entregue pela E145.
- Não faça correção retroativa dos 6 leads: é operação manual, com o código já em produção.

## Definição de pronto

- Cliente finalizado que volta a escrever entra em **Potenciais** e fica elegível ao rodízio.
- Existem os testes 2 **e** 3, e eles separam "está em Potenciais" de "a RLS deixou passar".
- Atendimento humano em curso não é afetado.
- `./mvnw -pl crm-app -am verify` verde; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
