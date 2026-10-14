import pg from 'pg';
const client = new pg.Client({
  connectionString: 'postgresql://postgres:postgres@localhost:5432/vertex'
});
async function check() {
  try {
    await client.connect();
    const res = await client.query('SELECT * FROM "PESQUISAS" ORDER BY "CRIADO_EM" DESC LIMIT 5;');
    console.log('Total de pesquisas:', res.rows.length);
    for (const r of res.rows) {
      console.log('CONSULTA:', r.CONSULTA);
      console.log('REGIAO:', r.REGIAO);
    }
  } catch (e) {
    console.log('Erro DB:', e.message);
  } finally {
    await client.end();
  }
}
check();
