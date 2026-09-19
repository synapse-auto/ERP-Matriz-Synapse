-- O runner controlado cria esta tabela somente durante a execução paginada da V73.
-- Depois que o Flyway já registrou a V73 e executou as migrations seguintes, o checkpoint
-- operacional deixa de ser necessário e não deve fazer parte do schema funcional do CRM.
DROP TABLE IF EXISTS synapse_v73_runner_checkpoint;
