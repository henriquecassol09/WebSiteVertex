-- Adiciona coluna CNPJ na tabela EMPRESAS para armazenar o documento obtido via SearXNG + BrasilAPI
ALTER TABLE "EMPRESAS" ADD COLUMN IF NOT EXISTS "CNPJ" TEXT;
