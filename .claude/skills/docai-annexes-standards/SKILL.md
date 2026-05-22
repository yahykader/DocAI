---
name: docai-annexes-standards
description: Références opérationnelles DocAI — Production Readiness Checklist complète, 7 scénarios Chaos Engineering (pannes Keycloak/Kafka/LLM/MongoDB), rotation des secrets (90j, AWS Secrets Manager), SLA documenté (99.9% uptime, ( 30s P95), GitFlow branches stratégie, politique dépendances Dependabot, guide onboarding développeur, glossaire métier. Utiliser quand on demande la checklist de mise en production, les tests de chaos, la rotation des secrets, le SLA, la stratégie de branches, ou le guide de démarrage pour un nouveau développeur.
---

# DocAI — Annexes & Standards Opérationnels
## Production Readiness · Chaos Engineering · Rotation Secrets · SLA · GitFlow

> **Référence :** DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 — Partie 7 (Annexes 7.A + 7.C)

---

## 1. Production Readiness Checklist

### Sécurité

- [ ] Secrets dans AWS Secrets Manager (pas de `.env` en production)
- [ ] WAF devant le load balancer
- [ ] TLS 1.3 sur tous les endpoints publics
- [ ] OWASP ZAP scan en CI (0 vulnérabilité HIGH/CRITICAL)
- [ ] Headers HTTP : CSP, HSTS, X-Frame-Options, X-Content-Type-Options
- [ ] Audit log immuable (append-only, pas de suppression possible)
- [ ] Pentest externe avant le lancement public
- [ ] `.env` dans `.gitignore` — vérification `git-secrets` en CI

### Fiabilité

- [ ] MongoDB : 3 nodes replica set (1 primary + 2 secondary, multi-AZ)
- [ ] Kafka : 3 brokers minimum, replication factor 3
- [ ] Keycloak : 2 instances minimum (ADR-006)
- [ ] Load balancer avec health checks sur `/actuator/health`
- [ ] Circuit Breaker testé : OpenAI down → documents en NEEDS_REVIEW (pas de crash)
- [ ] DLQ monitorée : alerte Grafana si > 10 messages
- [ ] Outbox relay monitoré : alerte si délai > 30 secondes
- [ ] Backup MongoDB : snapshot quotidien, test de restauration mensuel
- [ ] S3 : versioning activé, réplication cross-region eu-west-3 → eu-central-1

### Observabilité

- [ ] Dashboards Grafana : pipeline, fraude, API, JVM heap, Kafka lag
- [ ] Alertes : latence P95, taux erreur > 1%, lag Kafka > 1000, heap JVM > 85%
- [ ] Logs JSON structurés avec `traceId`, `tenantId`, `userId` sur tous les services
- [ ] Grafana OnCall configuré (escalade selon sévérité)
- [ ] SLA publié : 99.9% uptime, < 30s traitement P95
- [ ] Status page publique (Instatus ou Statuspage.io)

### Scalabilité

- [ ] HPA Kubernetes : scale-out basé sur CPU et lag Kafka
- [ ] Stress test passé : 2× la charge maximale attendue (k6)
- [ ] EXPLAIN PLAN MongoDB passé sur toutes les requêtes dashboard (ADR-010)
- [ ] Budget AWS configuré : alerte si coût > 150% du mois précédent

---

## 2. Chaos Engineering — 7 scénarios obligatoires

> À exécuter en staging avant chaque release majeure (BR-CHAOS-001).

| # | Scénario | Ce qui est testé | Résultat attendu |
|---|----------|-----------------|-----------------|
| 1 | Arrêter Keycloak 20 min | Cache JWKS ADR-006 | Utilisateurs non bloqués pendant 1h |
| 2 | Arrêter Kafka 5 min | Outbox Pattern | Zéro perte de documents, reprise automatique |
| 3 | Saturer LLM (renvoyer 429) | Circuit Breaker + fallback | Documents en NEEDS_REVIEW, pas de crash |
| 4 | Remplir disque MongoDB 95% | Health check diskSpace | Alerte Grafana + pod retiré du trafic |
| 5 | Arrêter 1 pod sur 3 en prod | RollingUpdate zero-downtime | 0 erreur HTTP pendant la panne |
| 6 | Dépasser quota LLM | Fallback OCR | Extraction partielle, pipeline non bloqué |
| 7 | Flood documents (×10 normal) | HPA + Kafka consumer lag | Scale-out, lag résorbé en < 5 min |

### Exécution en staging (commandes manuelles)

```bash
# Scénario 1 — Arrêt Keycloak
docker stop docai-keycloak
# Attendre 20 min — tester que les JWT sont encore valides
docker start docai-keycloak

# Scénario 2 — Arrêt Kafka
docker stop docai-kafka
# Vérifier : OutboxPoller continue de créer des OutboxEvents
docker start docai-kafka
# Vérifier : OutboxPoller publie tous les events en retard

# Scénario 3 — LLM down (via WireMock)
# Configurer WireMock pour retourner 503 sur /v1/chat/completions
# Lancer quelques uploads → vérifier NEEDS_REVIEW dans dashboard

# Scénario 7 — Load test
k6 run --vus 100 --duration 30m k6/pipeline-load.js
```

**Règles :**

| ID | Règle |
|----|-------|
| BR-CHAOS-001 | 7 scénarios exécutés en staging avant chaque release majeure |
| BR-CHAOS-002 | Résultats documentés dans le wiki après chaque run |
| BR-CHAOS-003 | Chaos en production = approbation Tech Lead requise |
| BR-CHAOS-004 | Chaque ADR est validé par un test chaos correspondant |

---

## 3. Rotation des Secrets Applicatifs

### Secrets à surveiller

| Secret | Outil | Fréquence rotation | Alerte si oublié |
|--------|-------|-------------------|-----------------|
| OpenAI API Key | AWS Secrets Manager | **90 jours** | Alerte AWS si > 90j |
| Keycloak Client Secret | AWS Secrets Manager | 90 jours | Alerte AWS |
| Stripe Webhook Secret | AWS Secrets Manager | 90 jours | Alerte AWS |
| INSEE OAuth2 credentials | AWS Secrets Manager | 90 jours | Alerte AWS |
| MongoDB credentials | AWS Secrets Manager | 180 jours | Alerte AWS |
| KMS PII encryption key | AWS KMS | Annuelle (automatique) | CloudTrail |

### Procédure rotation OpenAI (exemple)

```
1. Générer nouvelle clé sur portail.openai.com
2. Mettre à jour dans AWS Secrets Manager :
   aws secretsmanager put-secret-value \
     --secret-id docai/openai-api-key \
     --secret-string '{"apiKey":"sk-proj-new-key"}'
3. Spring Cloud AWS recharge automatiquement le secret (sans redéploiement)
4. Vérifier dans logs que les appels LLM réussissent
5. Révoquer l'ancienne clé sur le portail OpenAI
6. Documenter dans le journal de rotation (wiki)
```

**Règles :**

| ID | Règle |
|----|-------|
| BR-ROT-001 | Tous les secrets dans AWS Secrets Manager |
| BR-ROT-002 | Chaque secret a une date d'expiration configurée dans AWS |
| BR-ROT-003 | Rotation documentée dans journal (wiki) |
| BR-ROT-004 | Rotation ne nécessite jamais un redéploiement complet |

---

## 4. SLA Documenté

| Métrique | SLA |
|----------|-----|
| Uptime | **99.9%** (< 8.7h downtime/an) |
| Traitement document P95 | **< 30 secondes** |
| Latence dashboard P95 | **< 500ms** |
| Alerte fraude SSE (score > 50) | **< 2 secondes** |
| Disponibilité API publique | 99.9% |
| RTO (Recovery Time Objective) | < 4 heures |
| RPO (Recovery Point Objective) | < 1 heure |

---

## 5. Stratégie de Branches GitFlow

```
main ──────────────────────── Production uniquement (tags vX.Y.Z)
  │
develop ───────────────────── Intégration continue (staging auto)
  │
feature/XXX ──────────────── Fonctionnalité (PR vers develop)
hotfix/vX.Y.Z-description ── Bugfix urgence (PR vers main + develop)
```

**Convention Conventional Commits :**
```
feat(recognition): add confidence threshold validation
fix(fraud): correct SIRET checksum algorithm
refactor(pipeline): extract retry logic to RetryPolicy
test(extraction): add BDD scenarios for corrupted PDF
docs(api): update OpenAPI spec for /v1/documents
chore(deps): upgrade Spring Boot to 4.0.1
perf(dashboard): add compound index on tenantId + createdAt
```

### Procédure Hotfix

```bash
# 1. Créer depuis main
git checkout main && git pull
git checkout -b hotfix/v1.0.1-fix-siret-validation

# 2. Corriger + écrire le test qui reproduit le bug
# 3. PR vers main (1 reviewer minimum, label: hotfix priority-critical)
# 4. Merger + tag
git tag v1.0.1 && git push --tags
# 5. Surveiller Grafana 30 min après déploiement
# 6. Cherry-pick vers develop
git checkout develop && git cherry-pick <commit>
```

---

## 6. Politique Dépendances (Dependabot)

| Type | Délai max | Qui valide | Test requis |
|------|----------|-----------|------------|
| CVE CRITICAL | **24h** | Tech Lead | CI complet |
| CVE HIGH | **72h** | Tech Lead | CI complet |
| Patch version (x.y.Z) | 1 semaine | Dev | CI complet |
| Minor version (x.Y.0) | 2 semaines | Tech Lead | CI + test manuel |
| Major version (X.0.0) | Sprint dédié | Équipe | CI + non-régression |

**Règles :**

| ID | Règle |
|----|-------|
| BR-DEP-001 | CVE CRITICAL bloque le déploiement production jusqu'à correction |
| BR-DEP-002 | Dependabot configuré pour updates Maven hebdomadaires |
| BR-DEP-003 | Aucune dépendance sans licence compatible (Apache 2.0, MIT, BSD) |
| BR-DEP-004 | Spring Boot + Java LTS mis à jour dans les 3 mois de release |

---

## 7. Guide Onboarding Développeur (J+1)

```
J+1 — Setup local
  1. Lire DOCAI_BACKEND_MASTER_SPECKIT_F.md v15.0 (2h)
  2. Lire les ADR 001 à 011 (référence docai-architecture-adr skill)
  3. Cloner le repo + suivre docai-setup-projet skill
  4. `docker compose up -d` → vérifier tous les services healthy
  5. `./mvnw test -pl docai-domain` → doit passer (ArchUnit)
  6. `GET /actuator/health` → {"status":"UP"}

J+2 — Première PR
  1. Lire docai-architecture-adr (hexagonale + 12 règles ArchUnit)
  2. Créer feature branch : `git checkout -b feature/TICKET-description`
  3. Implémenter + tests unitaires (couverture ≥ 90% sur domaine)
  4. PR avec PR template rempli
  5. CI doit passer (ArchUnit, tests, SonarCloud Quality Gate)

Contacts
  - Tech Lead : alice@docai.fr (architecture, ADR)
  - DPO : rgpd@docai.fr (RGPD, PII)
  - DevOps : ops@docai.fr (CI/CD, infra)
```

---

## 8. Glossaire Métier

| Terme | Définition |
|-------|-----------|
| **Tenant** | Entreprise cliente de DocAI (isolation totale entre tenants) |
| **TENANT_ADMIN** | Responsable du compte entreprise (gestion équipe, billing, API keys) |
| **Pipeline** | Chaîne Upload → Classification → Extraction → Validation → Fraude → Livraison |
| **Outbox Pattern** | Transaction atomique MongoDB + publication Kafka garantie sans perte |
| **Read Model** | Vue dénormalisée pour le dashboard (CQRS — séparation lecture/écriture) |
| **DLQ** | Dead Letter Queue — messages en échec après 3 retry, rétention 90 jours |
| **ADR** | Architecture Decision Record — décision technique documentée et immuable |
| **Fail-safe** | Mécanisme garantissant qu'un analyseur défaillant n'arrête pas le pipeline |
| **Jitter TTL** | Variation aléatoire ±10% du TTL cache pour éviter les expirations simultanées |
| **Anti-Corruption Layer** | Adapter isolant le domaine des APIs externes (INSEE, BAN, RPPS) |
| **Overage** | Documents traités au-delà du quota mensuel, facturés à l'unité |
