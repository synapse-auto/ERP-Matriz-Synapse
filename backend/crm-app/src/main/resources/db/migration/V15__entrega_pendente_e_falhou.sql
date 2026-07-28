-- =========================================================
-- status_entrega: PENDENTE e FALHOU.
--
-- Ate a E04 a mensagem enviada nascia ENVIADO, antes de qualquer provedor ter
-- aceitado. Era dado otimista: se o envio falhasse, o atendente via o tique de
-- enviado numa mensagem que nunca saiu — e nao havia como distinguir, nem na
-- tela nem no diagnostico, "saiu e nao foi lida" de "nunca saiu".
--
-- Ciclo real a partir daqui:
--
--   PENDENTE --(provedor aceitou)--> ENVIADO --> ENTREGUE --> LIDO
--            --(falhou de vez)-----> FALHOU
--
-- Duas migrations separadas de proposito: o PostgreSQL permite ALTER TYPE ...
-- ADD VALUE dentro de transacao, mas proibe USAR o valor novo na mesma
-- transacao que o criou. Separar garante que a V16 e o codigo ja encontrem os
-- rotulos commitados.
-- =========================================================

ALTER TYPE status_entrega ADD VALUE IF NOT EXISTS 'PENDENTE' BEFORE 'ENVIADO';
ALTER TYPE status_entrega ADD VALUE IF NOT EXISTS 'FALHOU';
