---
name: docai-performance-test
description: "Crée un test de charge k6 pour un module DocAI (3 scénarios : nominal, pointe, stress). Utiliser quand on demande un test de performance, un test de charge, un script k6, ou quand un nouveau endpoint public est créé. Applique les seuils CI définis dans le SpecKit et intègre dans 06-performance.yml."
---

# DocAI — Créer un Test de Charge k6

## Règles absolues

| ID | Règle |
|----|-------|
| BR-PERF-001 | Tests de charge en **staging uniquement** — jamais en production |
| BR-PERF-002 | Chaque nouveau endpoint public a un test k6 associé |
| BR-PERF-003 | Un dépassement de seuil **bloque la release** production (CI fail) |
| BR-PERF-004 | Résultats publiés dans Grafana, conservés 90 jours |
| BR-PERF-005 | Test stress exécuté avant chaque release majeure |

## Seuils de référence par endpoint

| Module | Endpoint | P95 cible | Charge |
|--------|----------|-----------|--------|
| Upload | POST /v1/documents | < 2s | 50 simultanés |
| Extraction | GET /v1/documents/{id} | < 500ms | 200 req/s |
| Dashboard | GET /v1/dashboard/summary | < 100ms | 500 req/s |
| API Publique | POST /v1/documents (API Key) | < 2s | 100 req/s |

## Template k6 — 3 scénarios obligatoires

```javascript
// k6/module-{X}-load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Métriques personnalisées
const errorRate = new Rate('errors');
const uploadDuration = new Trend('upload_duration');

// Configuration des 3 scénarios
export const options = {
  scenarios: {
    // Scénario 1 — Charge nominale (valide le SLA quotidien)
    nominal_load: {
      executor: 'constant-vus',
      vus: 50,                    // 50 utilisateurs simultanés
      duration: '5m',
      tags: { scenario: 'nominal' },
    },

    // Scénario 2 — Pointe soudaine (valide la résilience)
    spike_load: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 500 }, // Montée 10 → 500 en 30s
        { duration: '1m',  target: 500 }, // Maintien 500 pendant 1 min
        { duration: '30s', target: 10  }, // Retour à 10
      ],
      tags: { scenario: 'spike' },
    },

    // Scénario 3 — Stress (trouve la limite)
    stress_load: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '2m', target: 100  },
        { duration: '2m', target: 300  },
        { duration: '2m', target: 500  },
        { duration: '2m', target: 1000 },
        { duration: '2m', target: 0    }, // Retour à 0
      ],
      tags: { scenario: 'stress' },
    },
  },

  // Seuils — dépassement = CI fail (sauf stress = alerte seulement)
  thresholds: {
    // Seuils bloquants (nominal + pointe)
    'http_req_duration{scenario:nominal}': ['p(95)<2000'],  // P95 < 2s
    'http_req_duration{scenario:spike}': ['p(95)<5000'],    // P95 < 5s en pointe
    'errors{scenario:nominal}': ['rate<0.01'],              // Erreurs < 1%
    'errors{scenario:spike}': ['rate<0.05'],                // Erreurs < 5% en pointe

    // Seuils informatifs (stress — pas de blocage CI)
    'http_req_duration{scenario:stress}': [{ threshold: 'p(95)<10000', abortOnFail: false }],
  },
};

// Variables d'environnement
const BASE_URL = __ENV.BASE_URL || 'https://staging.docai.fr';
const API_KEY = __ENV.API_KEY;         // API Key de test staging
const JWT_TOKEN = __ENV.JWT_TOKEN;     // JWT de test staging

// Scénario upload document
export function uploadDocument() {
  const payload = {
    file: http.file(generateTestPdf(), 'test-invoice.pdf', 'application/pdf'),
  };

  const headers = { 'Authorization': `Bearer ${JWT_TOKEN}` };

  const startTime = Date.now();
  const response = http.post(`${BASE_URL}/v1/documents`, payload, { headers });
  uploadDuration.add(Date.now() - startTime);

  const success = check(response, {
    'status is 201': (r) => r.status === 201,
    'documentId present': (r) => JSON.parse(r.body).data?.documentId !== undefined,
    'response time < 2s': (r) => r.timings.duration < 2000,
  });

  errorRate.add(!success);
  sleep(1); // Pause entre les requêtes
}

// Scénario dashboard
export function getDashboard() {
  const headers = { 'Authorization': `Bearer ${JWT_TOKEN}` };
  const response = http.get(
    `${BASE_URL}/v1/dashboard/summary?page=0&size=20`,
    { headers }
  );

  check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 100ms': (r) => r.timings.duration < 100,
  });

  errorRate.add(response.status !== 200);
  sleep(0.5);
}

// Point d'entrée par défaut
export default function () {
  // 70% dashboard (lecture), 30% upload (écriture)
  if (Math.random() < 0.7) {
    getDashboard();
  } else {
    uploadDocument();
  }
}

// Utilitaire — PDF de test minimal
function generateTestPdf() {
  return new Uint8Array([0x25, 0x50, 0x44, 0x46]); // Header PDF
}
```

## Intégration CI — job 06-performance.yml

```yaml
# .github/workflows/06-performance.yml
name: Performance Tests

on:
  workflow_dispatch:    # Déclenchement manuel
  push:
    tags: ['v*']       # Avant chaque release

jobs:
  k6-performance:
    runs-on: ubuntu-latest
    environment: staging   # Nécessite approbation
    if: github.ref_type == 'tag' || github.event_name == 'workflow_dispatch'

    steps:
      - uses: actions/checkout@v4

      - name: Install k6
        run: |
          sudo gpg -k
          sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
            --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
          echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] \
            https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
          sudo apt-get update && sudo apt-get install k6

      - name: Run performance tests
        env:
          BASE_URL: ${{ vars.STAGING_URL }}
          API_KEY: ${{ secrets.STAGING_API_KEY }}
          JWT_TOKEN: ${{ secrets.STAGING_JWT_TOKEN }}
          K6_PROMETHEUS_RW_SERVER_URL: ${{ secrets.GRAFANA_PROMETHEUS_URL }}
        run: |
          k6 run \
            --out experimental-prometheus-rw \
            k6/module-1-load-test.js

      - name: Publish results to Grafana
        if: always()
        run: echo "Results published via Prometheus remote write"
```

## Ajouter un test pour un nouveau module

```bash
# 1. Créer le fichier
touch k6/module-{X}-load-test.js

# 2. Copier le template ci-dessus
# 3. Adapter les endpoints et les seuils

# 4. Tester localement contre staging
k6 run --env BASE_URL=https://staging.docai.fr \
       --env API_KEY=... \
       --scenario nominal_load \
       k6/module-X-load-test.js

# 5. Ajouter au job CI 06-performance.yml
```

## Checklist

- [ ] 3 scénarios présents : `nominal_load`, `spike_load`, `stress_load`
- [ ] Seuils bloquants sur nominal et spike (CI fail si dépassé)
- [ ] Seuils informatifs sur stress (`abortOnFail: false`)
- [ ] Métriques personnalisées : `errorRate`, duration par endpoint
- [ ] Variables d'env : `BASE_URL`, `API_KEY`, `JWT_TOKEN` (jamais en dur)
- [ ] `K6_PROMETHEUS_RW_SERVER_URL` configuré pour Grafana
- [ ] Job CI `06-performance.yml` déclenché uniquement en staging
- [ ] Premier run documenté : résultats de référence enregistrés
- [ ] Jamais exécuté en production (BR-PERF-001)
