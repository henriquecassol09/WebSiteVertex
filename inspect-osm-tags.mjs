const bbox = '-25.68,-52.60,-25.25,-51.95';
const query = `
[out:json][timeout:20];
(
  node["shop"]["name"](${bbox});
  node["amenity"]["name"](${bbox});
  node["craft"]["name"](${bbox});
  node["office"]["name"](${bbox});
);
out body 30;
`;

const res = await fetch('https://overpass.openstreetmap.fr/api/interpreter', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'User-Agent': 'VertexProspector/1.0'
  },
  body: 'data=' + encodeURIComponent(query)
});

const data = await res.json();
console.log('Total de elementos recebidos:', data.elements?.length);
for (const el of data.elements.slice(0, 15)) {
  console.log('--- NOME:', el.tags.name);
  console.log('Tags:', JSON.stringify(el.tags));
}
