 vc# DocAI
## L'Intelligence Artificielle au service de vos documents

> *Transformez votre traitement documentaire en avantage compétitif*

---

&nbsp;

# ✦ Présentation Générale

&nbsp;

DocAI est une **plateforme SaaS d'intelligence documentaire** qui automatise
la lecture, la compréhension et la vérification de vos documents d'entreprise.

Factures · Contrats · Pièces d'identité · Ordonnances · Bulletins de salaire ·
Justificatifs · Formulaires administratifs

**En quelques secondes**, DocAI identifie un document, en extrait les données clés,
les valide contre les référentiels officiels, et détecte les anomalies ou fraudes potentielles.

Ce qui prenait des heures devient **instantané**.
Ce qui échappait à l'œil humain devient **visible et contrôlé**.

&nbsp;

---

&nbsp;

# ✦ Le Problème que nous résolvons

&nbsp;

## Ce que vivent vos équipes aujourd'hui

Chaque jour, dans des milliers d'entreprises, des collaborateurs talentueux
passent leur temps à des tâches qui ne devraient plus exister en 2026.

&nbsp;

| ❌ Ce qui se passe sans DocAI | ✅ Ce qui se passe avec DocAI |
|-------------------------------|-------------------------------|
| Un comptable saisit manuellement les données d'une facture | Les données sont extraites et structurées en 15 secondes |
| Un gestionnaire vérifie à l'œil nu si un RIB est cohérent | L'IBAN est validé algorithmiquement, le titulaire cross-référencé |
| Un service fraude consulte chaque document suspect un par un | Un score de risque est calculé automatiquement sur chaque document |
| Un dossier client bloque en attente de vérification | Le pipeline traite en continu, 24h/24, 7j/7 |
| Une erreur de saisie génère un litige 3 mois plus tard | Les incohérences sont détectées avant traitement |
| Une fausse ordonnance passe entre les mailles du contrôle | Les falsifications (retouches, RPPS invalide) sont signalées |

&nbsp;

## Le coût réel du traitement manuel

&nbsp;

> **1 document traité manuellement coûte entre 3€ et 15€**
> selon la complexité et le niveau de vérification requis.

&nbsp;

Pour une PME qui traite 500 factures par mois, c'est entre **1 500€ et 7 500€
de coût mensuel** — uniquement pour la saisie et la vérification.

Sans compter les erreurs (1 à 3% en saisie humaine), les délais de traitement
(2 à 5 jours ouvrés en moyenne), et les fraudes non détectées
(**4,2% des factures B2B en France contiendraient une anomalie selon la DGFiP**).

&nbsp;

---

&nbsp;

# ✦ Notre Solution

&nbsp;

DocAI est construit autour d'un principe simple :

> *Chaque document qui entre dans votre système doit en ressortir
> structuré, validé et sécurisé — sans intervention humaine.*

&nbsp;

La plateforme combine trois technologies complémentaires :

&nbsp;

**Vision par ordinateur** — Pour voir le document comme un humain expert le verrait,
identifier son type, détecter les anomalies visuelles et extraire la structure.

**Compréhension par IA (LLM)** — Pour lire, interpréter et structurer
les informations en JSON exploitable, quel que soit le format du document.

**Intelligence décisionnelle** — Pour valider les données contre les référentiels
officiels (INSEE, BAN, RPPS), appliquer les règles métier et scorer le risque.

&nbsp;

---

&nbsp;

# ✦ Les 6 Modules DocAI

&nbsp;

## Module 1 — Reconnaissance Intelligente

### *"Votre document est identifié avant même que vous ayez le temps de le nommer"*

&nbsp;

DocAI reçoit un fichier brut — qu'il soit scanné, photographié ou généré numériquement —
et l'identifie automatiquement parmi 10 types de documents supportés.

**Types reconnus en V1 :**
Facture · Devis · Contrat · CNI · Passeport · RIB · Ordonnance · Bulletin de salaire · Justificatif de domicile · Formulaire administratif

&nbsp;

### Comment ça fonctionne

Un score de confiance (0 à 100%) accompagne chaque classification.
Les documents clairs et bien numérisés sont traités automatiquement.
Les cas ambigus sont signalés pour révision, sans bloquer le flux global.

&nbsp;

### Avantages business

| Avant | Après |
|-------|-------|
| Tri manuel des documents en arrivée | Routage automatique dès réception |
| Erreurs de classement fréquentes | Précision ≥ 92% sur le jeu de test |
| Temps de tri : 1 à 2 min/document | Temps de reconnaissance : < 5 secondes |
| Impossible à passer à l'échelle | Traitement illimité, 24h/24 |

&nbsp;

### Business Value

> 💡 **Pour une équipe traitant 2 000 documents par mois**, la reconnaissance automatique
> représente entre **30 et 60 heures économisées chaque mois** — soit l'équivalent d'une
> semaine de travail d'un collaborateur à temps plein.

&nbsp;

---

&nbsp;

## Module 2 — Extraction d'Informations

### *"Fini la saisie manuelle. Les données viennent à vous, structurées et prêtes à l'emploi."*

&nbsp;

Après identification, DocAI extrait automatiquement les champs clés selon le type
de document. Le résultat est un **objet JSON structuré, normalisé et enrichi**,
prêt à être injecté dans votre ERP, GED, CRM ou système de gestion.

&nbsp;

### Ce qui est extrait — Exemples

**Sur une Facture :**
Émetteur (raison sociale, SIRET, adresse) · Destinataire · Numéro · Date d'émission
· Date d'échéance · Montant HT · TVA (taux + montant) · Montant TTC · Lignes détail

**Sur une CNI / Passeport :**
Nom · Prénom · Date de naissance · Numéro de document · Date d'expiration
· Autorité émettrice · MRZ (zone lecture machine)

**Sur une Ordonnance :**
Médecin (nom, RPPS, spécialité) · Patient (nom, date de naissance)
· Médicaments (nom, dosage, posologie, durée) · Date de prescription

&nbsp;

### La validation intégrée — Ce que les autres ne font pas

DocAI ne se contente pas d'extraire. Il **valide** chaque donnée extraite :

- Le calcul TVA est vérifié mathématiquement
- Le SIRET est contrôlé algorithmiquement (algorithme de Luhn) puis croisé avec le registre INSEE
- L'IBAN est validé (norme ISO 13616 modulo 97)
- L'adresse est vérifiée dans la Base Adresse Nationale officielle
- Le numéro RPPS est contrôlé dans l'Annuaire Santé de l'ANS

&nbsp;

### Avantages business

| Indicateur | Sans DocAI | Avec DocAI | Gain |
|------------|-----------|-----------|------|
| Temps de saisie par document | 3 à 5 min | 0 (automatique) | 100% |
| Taux d'erreur de saisie | 1 à 3% | < 0,5% | −80% |
| Délai de traitement | 2 à 5 jours | < 30 secondes | ×300 |
| Coût par document traité | 3€ à 8€ | 0,05€ à 0,15€ | −97% |

&nbsp;

### Business Value

> 💡 **Pour une entreprise traitant 500 factures fournisseurs par mois**, DocAI
> représente une économie directe de **25 à 40 heures de saisie mensuelle**
> et une réduction des erreurs qui génèrent des litiges fournisseurs coûteux.

> 💡 **Pour un organisme de santé**, l'extraction automatique des ordonnances
> réduit le délai de prise en charge et garantit la conformité de chaque prescription
> avant remboursement.

&nbsp;

---

&nbsp;

## Module 3 — Détection de Fraude

### *"Ce que l'œil humain ne voit pas, DocAI le détecte."*

&nbsp;

La fraude documentaire est un problème massif et sous-estimé.
Fausses factures, ordonnances falsifiées, RIB modifiés, pièces d'identité retouchées —
ces fraudes coûtent chaque année plusieurs milliards d'euros aux entreprises françaises.

DocAI analyse chaque document selon **trois axes complémentaires** et produit
un **score de fraude de 0 à 100** avec la liste détaillée des signaux détectés.

&nbsp;

### Les trois axes d'analyse

**Axe 1 — Analyse des métadonnées fichier**
DocAI inspecte les métadonnées invisibles à l'œil nu : logiciel ayant créé le fichier,
dates de modification, couches cachées dans les PDF, artefacts de compression artificielle.
Un document créé avec Photoshop ou présentant des couches PDF masquées est immédiatement signalé.

**Axe 2 — Cohérence des données extraites**
Les calculs sont vérifiés (TVA, totaux), les identifiants contrôlés (SIRET, IBAN, RPPS),
les dates cohérentes entre elles et avec la réalité. Un SIRET appartenant à une entreprise
fermée depuis 6 mois est un signal fort de fraude.

**Axe 3 — Analyse visuelle du document**
Polices incohérentes dans un même champ, texte superposé sur un fond existant,
logo de résolution dégradée, alignement brisé — les signes caractéristiques
d'un document retouché sont détectés automatiquement.

&nbsp;

### Les 4 niveaux de risque et leurs actions automatiques

&nbsp;

| Score | Niveau | Action automatique |
|-------|--------|--------------------|
| 0 – 25 | 🟢 **Faible** | Validation automatique · Aucune intervention requise |
| 26 – 50 | 🟡 **Modéré** | Signalement dans le dashboard · Révision optionnelle |
| 51 – 75 | 🟠 **Élevé** | Blocage temporaire · Envoi en queue de révision obligatoire |
| 76 – 100 | 🔴 **Critique** | Rejet immédiat · Alerte temps réel · Trace immuable |

&nbsp;

### Avantages business

| Indicateur | Contrôle manuel | DocAI | Amélioration |
|------------|----------------|-------|-------------|
| Taux de détection des fraudes | ~40% | ≥ 85% | +45 points |
| Documents légitimes sans intervention | Variable | 75%+ | Fluidité opérationnelle |
| Délai de détection | Jours | Secondes | Instantané |
| Taux de faux positifs | — | < 5% | Pertinence |
| Traçabilité des décisions | Partielle | 100% immuable | Conformité juridique |

&nbsp;

### Business Value

> 💡 **Pour un assureur traitant des dossiers sinistres**, chaque fausse facture
> non détectée représente un préjudice direct. DocAI rentabilise son coût
> dès la **première fraude détectée** — souvent en moins d'une semaine d'utilisation.

> 💡 **Pour une banque ou un organisme de crédit**, la vérification automatique
> des pièces KYC (CNI, justificatif domicile, RIB) réduit les risques de fraude
> à l'identité et assure la conformité réglementaire à chaque dossier.

> 💡 **Conformité réglementaire :** Chaque décision est horodatée, signée
> et immuable — votre entreprise dispose d'une **piste d'audit complète**
> exploitable en cas de contrôle ou de litige.

&nbsp;

---

&nbsp;

## Module 4 — Orchestration & Pipeline

### *"Aucun document ne se perd. Jamais."*

&nbsp;

L'Orchestration est le chef d'orchestre invisible de DocAI. C'est le module
qui garantit que chaque document suit son chemin de traitement jusqu'au bout,
même en cas de panne partielle, de surcharge ou d'erreur technique.

&nbsp;

### Ce que vous ressentez en tant qu'utilisateur

Vous soumettez un document. Vous recevez une confirmation immédiate.
Quelques secondes plus tard, le résultat est disponible.

Vous ne voyez pas les mécanismes internes — et c'est exactement le but.

&nbsp;

### Ce qui se passe sous le capot

- **Pipeline 100% asynchrone** — Votre application n'attend jamais.
  L'upload est confirmé en < 2 secondes, le traitement continue en arrière-plan.

- **Zéro perte de document** — Grâce au pattern Outbox et à Apache Kafka,
  chaque document soumis sera traité, même si un service tombe en panne.
  À la reprise, le traitement reprend exactement où il s'est arrêté.

- **Reprise automatique** — En cas d'erreur temporaire (service IA indisponible,
  timeout réseau), le système réessaie automatiquement avec une stratégie progressive.
  Vous ne voyez rien — le document arrive quand même.

- **Scalabilité horizontale** — DocAI traite 10 documents ou 100 000 documents
  par jour sans modification d'architecture. Le pipeline s'adapte à votre volume.

&nbsp;

### Avantages business

&nbsp;

> **99,9% des documents sont traités sans intervention humaine,**
> même lors des pics de charge ou des maintenances planifiées.

&nbsp;

| Garantie | Valeur |
|----------|--------|
| Disponibilité du service | ≥ 99,9% |
| Perte de document après soumission | 0% |
| Traitement automatique sans intervention | ≥ 99% des cas |
| Délai end-to-end (upload → résultat) | < 30 secondes |

&nbsp;

### Business Value

> 💡 **Pour un service comptabilité** recevant des factures toute la journée,
> DocAI garantit qu'aucune facture ne reste "dans les limbes" du système.
> Le suivi en temps réel permet de savoir exactement où en est chaque document.

> 💡 **Pour un département IT**, la résilience du pipeline signifie
> **zéro escalade de nuit** pour un document bloqué. Le système se guérit seul.

&nbsp;

---

&nbsp;

## Module 5 — Dashboard & Reporting

### *"Toute votre activité documentaire en un coup d'œil."*

&nbsp;

Le Dashboard DocAI est la tour de contrôle de votre traitement documentaire.
Il donne à chaque profil — manager, analyste, responsable fraude —
**la visibilité dont il a besoin, au moment où il en a besoin**.

&nbsp;

### Pour le Manager — Les KPIs qui comptent

- Volume de documents traités (aujourd'hui, semaine, mois)
- Temps de traitement moyen par type de document
- Taux de validation automatique vs révision manuelle
- Distribution des niveaux de risque fraude
- Évolution du taux de fraude détecté dans le temps
- Coût évité estimé (fraudes stoppées × coût moyen)

&nbsp;

### Pour l'Analyste — L'efficacité au quotidien

- Liste des documents en cours de traitement avec statut en temps réel
- Queue de révision triée par priorité et niveau de risque
- Vue détaillée de l'extraction avec champs mis en évidence
- Correction manuelle avec historique complet des modifications
- Relance en un clic d'un document en erreur

&nbsp;

### Pour le Responsable Fraude — La vigilance sans fatigue

- Alertes en temps réel (< 2 secondes) sur les documents critiques (score > 75)
- Rapport détaillé de chaque signal détecté avec les preuves visuelles
- Workflow de décision : approuver, rejeter, escalader
- Historique immuable de chaque décision avec identité du validateur
- Export des cas pour analyse juridique ou signalement

&nbsp;

### Avantages business

&nbsp;

> Le dashboard ne nécessite aucune formation technique.
> **Prise en main en moins d'une heure.**

&nbsp;

| Fonctionnalité | Valeur apportée |
|----------------|-----------------|
| Alertes temps réel (< 2 sec) | Réaction immédiate sur les cas critiques |
| Read Model CQRS dédié | Requêtes < 100ms même avec des millions de documents |
| Historique complet | Conformité audit · Preuve juridique |
| Export CSV/JSON | Intégration avec vos outils BI existants |
| Accès multi-profils | Chaque utilisateur voit ce dont il a besoin |

&nbsp;

### Business Value

> 💡 **Pour un COMEX**, le dashboard transforme une activité invisible
> (le traitement documentaire) en **indicateur de performance mesurable**.
> DocAI passe de centre de coût à actif stratégique visible.

> 💡 **Pour un service de conformité**, l'historique immuable des décisions
> et la piste d'audit complète répondent aux exigences des contrôleurs
> **sans préparation supplémentaire** — tout est déjà là.

&nbsp;

---

&nbsp;

## Module 6 — Intégrations & API Publique

### *"DocAI s'intègre dans votre système — pas l'inverse."*

&nbsp;

DocAI n'est pas une île. Il est conçu pour s'insérer **naturellement
dans votre écosystème existant** — votre ERP, votre GED, votre SIRH,
votre CRM ou vos applications métier sur mesure.

&nbsp;

### Trois modes d'intégration

**API REST versionnée**
Une API simple, documentée, avec des exemples dans chaque langage.
Votre développeur peut envoyer son premier document et recevoir un résultat
en **moins d'une heure**. L'API est versionnée (`/v1/`, `/v2/`) —
aucune mise à jour DocAI ne casse votre intégration.

**Webhooks sécurisés**
DocAI vous notifie dès qu'un traitement est terminé, qu'une fraude est détectée
ou qu'une révision est requise. La notification est **signée cryptographiquement**
(HMAC-SHA256) — vous pouvez vérifier son authenticité.
En cas d'échec de livraison, DocAI réessaie automatiquement jusqu'à 5 fois.

**Plans & Quotas adaptés**
DocAI s'adapte à votre volume. Démarrez avec le plan Starter pour tester
et valider la valeur. Passez au plan Pro quand vous en avez besoin.
Pas d'engagement à long terme. Pas de frais cachés.

&nbsp;

### Compatibilité & Formats

| Format supporté en entrée | Format de sortie |
|--------------------------|-----------------|
| PDF (natif et scanné) | JSON structuré normalisé |
| PNG, JPG, JPEG | Webhook JSON signé |
| TIFF, WEBP | Export CSV / Excel |
| Taille max 25MB | API REST standardisée |

&nbsp;

### Sécurité de l'intégration

- Authentification **OAuth2 / OIDC** (Keycloak) pour les utilisateurs humains
- **API Keys sécurisées** (hash SHA-256) pour les intégrations machine-à-machine
- **Isolation totale** des données entre clients — votre tenant est hermétiquement séparé
- **Rate limiting** par plan pour garantir la stabilité du service à tous les clients

&nbsp;

### Avantages business

&nbsp;

| Intégration | Ce que ça change |
|-------------|-----------------|
| ERP (SAP, Sage, Cegid…) | Factures traitées automatiquement sans module OCR tiers coûteux |
| GED (SharePoint, Alfresco…) | Documents indexés et enrichis dès l'entrée dans la GED |
| SIRH (ADP, Silae…) | Bulletins de salaire et contrats vérifiés avant archivage |
| Application métier sur mesure | API REST prête en < 1h pour votre développeur |
| Plateforme d'assurance | Ordonnances et devis validés en temps réel dans votre workflow |

&nbsp;

### Business Value

> 💡 **Pour votre DSI**, DocAI ne crée pas un nouveau silo.
> Il se connecte à vos outils existants via des standards ouverts (REST, OAuth2, webhooks).
> **Délai d'intégration estimé : 1 à 5 jours** selon la complexité de votre système.

> 💡 **Pour votre direction commerciale**, l'API versionnée et la documentation
> complète permettent de **proposer DocAI comme service à vos propres clients**
> (intégration en marque blanche).

&nbsp;

---

&nbsp;

# ✦ Synthèse — Pourquoi DocAI ?

&nbsp;

## L'impact global en chiffres

&nbsp;

| Indicateur | Impact DocAI |
|------------|-------------|
| 🕐 Temps de traitement par document | **15 à 30 secondes** (vs 3 à 5 minutes) |
| 🎯 Précision d'extraction | **≥ 95%** sur les champs clés |
| 🛡️ Taux de détection fraude | **≥ 85%** (vs ~40% en contrôle manuel) |
| 💰 Réduction du coût de traitement | **−90% à −97%** par document |
| 📋 Documents sans intervention humaine | **≥ 75%** (traitement entièrement automatique) |
| ⚡ Disponibilité du service | **≥ 99,9%** · pipeline résilient 24/7 |
| 🔗 Délai d'intégration | **< 1 jour** pour un développeur |
| 📈 ROI estimé premier semestre | **3× à 10×** selon le volume traité |

&nbsp;

## À qui s'adresse DocAI en priorité

&nbsp;

| Secteur | Volume estimé | Cas d'usage principal |
|---------|--------------|----------------------|
| **Cabinets comptables & experts-comptables** | 500 à 50 000 factures/mois | Saisie automatique + détection fraude fournisseur |
| **Compagnies d'assurance** | 1 000 à 100 000 dossiers/mois | Validation pièces justificatives + ordonnances |
| **Banques & Fintechs** | 5 000 à 500 000 KYC/mois | Vérification CNI, RIB, justificatif domicile |
| **Entreprises RH & SIRH** | 200 à 10 000 contrats/mois | Vérification bulletins, contrats, déclarations |
| **Acteurs de la santé** | 500 à 50 000 ordonnances/mois | Validation RPPS, contrôle prescriptions |
| **Plateformes immobilières** | 1 000 à 20 000 dossiers/mois | Analyse dossiers locataires complets |

&nbsp;

## Ce qui nous différencie

&nbsp;

> DocAI n'est pas un simple outil OCR.
> C'est une **chaîne complète de valeur documentaire**
> qui combine reconnaissance, extraction, validation et sécurité
> dans une seule plateforme, accessible par API.

&nbsp;

| Fonctionnalité | DocAI | OCR simple | Saisie manuelle |
|----------------|-------|-----------|----------------|
| Identification automatique du type | ✅ | ❌ | ✅ (lent) |
| Extraction structurée par type | ✅ | Partielle | ✅ (lent) |
| Validation référentiels (INSEE, BAN) | ✅ | ❌ | Rarement |
| Détection fraude multi-signaux | ✅ | ❌ | Partielle |
| Pipeline 100% asynchrone + résilient | ✅ | ❌ | N/A |
| API REST + Webhooks sécurisés | ✅ | Variable | N/A |
| Dashboard temps réel | ✅ | Rare | ❌ |
| Audit trail immuable | ✅ | ❌ | Rare |
| Multi-tenant sécurisé | ✅ | Variable | N/A |

&nbsp;

---

&nbsp;

# ✦ Comment démarrer

&nbsp;

## Les 3 étapes pour lancer DocAI dans votre organisation

&nbsp;

**Étape 1 — Démonstration & Cadrage** *(1 à 2 jours)*

Nous analysons vos flux documentaires actuels, identifions les types de documents
prioritaires et estimons votre ROI personnalisé. Vous repartez avec un chiffrage
précis de l'économie réalisable.

**Étape 2 — Pilote sur votre volume réel** *(2 à 4 semaines)*

Nous connectons DocAI à votre système sur un périmètre limité (un type de document,
un service). Vous mesurez les résultats réels sur vos propres documents.
Le pilote est déployé en < 1 semaine.

**Étape 3 — Déploiement complet & montée en charge** *(1 à 2 mois)*

Après validation du pilote, DocAI est étendu à l'ensemble de vos flux.
Votre équipe est formée. Les intégrations ERP/GED sont finalisées.
Vous disposez d'une plateforme documentaire qui travaille à votre place.

&nbsp;

## Les questions que posent nos prospects

&nbsp;

**Nos documents sont confidentiels. Comment est gérée la sécurité ?**
DocAI est architecturé en multi-tenant strict — vos données sont hermétiquement
séparées des autres clients. Chaque accès est authentifié (OAuth2/Keycloak),
audité et chiffré. Les fichiers sont stockés dans votre propre espace de stockage
sécurisé (S3 ou équivalent souverain selon votre choix).

**Pouvons-nous garder le contrôle sur les décisions ?**
Absolument. Pour les documents à risque modéré ou élevé, DocAI place le document
en queue de révision humaine. Votre équipe valide ou rejette avec une justification.
Aucune décision finale n'est prise sans validation humaine au-delà du niveau de risque
que vous définissez vous-même.

**Que se passe-t-il si DocAI fait une erreur ?**
Toute extraction peut être corrigée manuellement. Chaque correction est auditée.
Le système apprend de ces corrections pour améliorer sa précision dans le temps.
Le score de confiance sur chaque champ indique explicitement le niveau de certitude.

**Est-ce que DocAI peut s'intégrer à notre ERP existant ?**
Oui. DocAI expose une API REST standard documentée avec Swagger.
Si votre ERP accepte des webhooks ou peut appeler une API REST,
l'intégration se fait en quelques jours sans développement lourd.

&nbsp;

---

&nbsp;

# ✦ L'équipe & La Vision

&nbsp;

DocAI est né d'une conviction simple :

> *Les documents d'entreprise contiennent une valeur immense.
> Cette valeur est aujourd'hui enfouie sous des couches de processus manuels,
> d'erreurs humaines et de contrôles insuffisants.*
>
> *L'IA ne doit pas remplacer les collaborateurs.
> Elle doit leur permettre de se concentrer sur ce que les humains font mieux
> que les machines : comprendre le contexte, prendre des décisions complexes,
> créer de la relation.*
>
> *DocAI automatise ce qui peut l'être pour libérer ce qui doit rester humain.*

&nbsp;

Le projet combine des expertise en **intelligence artificielle appliquée**,
**architecture logicielle résiliente**, **sécurité des données** et
**compréhension profonde des processus métier documentaires**.

&nbsp;

---

&nbsp;

*DocAI — Intelligence documentaire pour l'entreprise moderne*
*Document de présentation commerciale — Confidentiel*

&nbsp;

