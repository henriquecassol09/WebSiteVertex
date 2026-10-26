-- Atualização de locais e estabelecimentos da região BR-277 e Candói
UPDATE "PESQUISAS"
SET "REGIAO" = REPLACE(REPLACE("REGIAO", '"cidade":"Cantagalo"', '"cidade":"Candói"'), '"cidade":"Laranjeiras do Sul"', '"cidade":"Candói"')
WHERE (LOWER("CONSULTA") LIKE '%candói%' OR LOWER("CONSULTA") LIKE '%candoi%')
  AND ("REGIAO" LIKE '%"cidade":"Cantagalo"%' OR "REGIAO" LIKE '%"cidade":"Laranjeiras do Sul"%');

UPDATE "PESQUISAS"
SET "REGIAO" = REPLACE("REGIAO", '"endereco":"Centro"', '"endereco":"Rodovia BR-277"')
WHERE (LOWER("CONSULTA") LIKE '%posto%' OR LOWER("CONSULTA") LIKE '%br-277%' OR LOWER("CONSULTA") LIKE '%br 277%')
  AND "REGIAO" LIKE '%"endereco":"Centro"%';

UPDATE "EMPRESAS"
SET "CIDADE" = 'Candói'
WHERE (LOWER("NOME") LIKE '%candói%' OR LOWER("NOME") LIKE '%candoi%')
  AND ("CIDADE" IS NULL OR "CIDADE" != 'Candói');

UPDATE "EMPRESAS"
SET "ENDERECO" = 'Rodovia BR-277'
WHERE (LOWER("NOME") LIKE '%posto%' OR LOWER("NOME") LIKE '%br-277%')
  AND ("ENDERECO" = 'Centro' OR "ENDERECO" IS NULL OR "ENDERECO" = '');
