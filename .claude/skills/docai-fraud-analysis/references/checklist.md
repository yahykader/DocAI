## Checklist complète (3 phases)

### Phase 3.1
- [ ] `FraudAnalysis` immuable après création (pas de setter)
- [ ] Score -1 uniquement si AUCUN analyseur n'a répondu
- [ ] Strategy Pattern : analyseurs injectés via Spring DI
- [ ] Fail-safe sur chaque analyseur (try/catch, isPartial=true)
- [ ] `FraudAnalyzedEvent` publié via Outbox Pattern
- [ ] Métriques : `docai_fraud_score_distribution{risk_level}`

### Phase 3.2
- [ ] `ApacheTikaMetadataAdapter` : PDF Photoshop → signal `META_EDITOR_SUSPICIOUS`
- [ ] Tika : date création incohérente → `META_DATE_INCONSISTENCY`
- [ ] `VisualAnalyzerAdapter` : JavaCV (`org.bytedeco:opencv:4.9.0`), pas `org.opencv`
- [ ] BR-VIS-002 : analyse visuelle uniquement FACTURE, CNI, RIB
- [ ] BR-VIS-003 : timeout 15s configuré dans Resilience4j (`timelimiter.opencv`)
- [ ] BR-VIS-004 : fail-safe validé (exception → signal ignoré, pipeline continue)
- [ ] `FraudAnalyzerRegistry` auto-enregistrement (nouveau `@Component` → inclus automatiquement)
- [ ] `CompositeFraudAnalyzer` : `isPartialAnalysis = true` si ≥ 1 analyseur échoue
- [ ] Métrique `docai_fraud_analyzer_failure{analyzer}` incrémentée sur chaque échec

### Phase 3.3
- [ ] State machine : transitions invalides → `InvalidReviewStateException` (HTTP 409)
- [ ] `ReviewDecision` immuable (tentative modification → exception)
- [ ] Comment obligatoire pour REJECTED et ESCALATED
- [ ] AuditEntry créé pour chaque décision (userId masqué)
- [ ] SSE : alerte reçue en < 2s pour score > 50 (BR-FRD-015)
- [ ] Isolation SSE : tenant A ne voit pas alertes tenant B (2 connexions testées)
- [ ] Limit 50 connexions SSE par tenant (51ème → HTTP 503)
- [ ] Seul `FRAUD_REVIEWER` accède aux endpoints de révision (ANALYST → HTTP 403)
