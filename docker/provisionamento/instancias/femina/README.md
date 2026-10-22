# Textos de Cancelamentos — Fêmina

`cancelamentos.textos.override.json` e uma **sobreposicao parcial**, nao um catalogo completo.
Mesclar apenas suas chaves em `cancelamentos` do `textos.json` da instancia, preservando todas
as demais secoes. A leitura existente e pelo classpath, no boot; nao ha endpoint de edicao
generica de textos nem aplicacao automatica deste arquivo pelo Dokploy.

Nao substituir o catalogo inteiro por este fragmento. Nao aplicar ao catalogo Base PAI:
outras instancias continuam com Contato/Profissional. Nenhuma configuracao real foi alterada.
O pacote de release da instancia precisa incluir o catalogo mesclado pelo mecanismo de recursos
da instancia. Nao inventar variavel de ambiente que o codigo nao le.

Datas de agenda e cancelamento conservam os significados de `agendadoPara` e `canceladoEm`.
`registro` permanece recebimento no CRM e tambem identifica a ordenacao quando essa base esta ativa.
`tipo` continua Tipo de agendamento: Consulta/Retorno/Exame nao sao especialidades.
CPF/telefone sao lidos do cadastro atual autorizado, somente ao abrir os detalhes.

Especialidade depende da definicao entre dado atual do lead e snapshot do agendamento; nao
renomear `tipo` nem preencher registros antigos enquanto essa fronteira nao estiver definida.
