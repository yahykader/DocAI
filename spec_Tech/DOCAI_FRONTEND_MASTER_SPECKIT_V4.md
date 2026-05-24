# DocAI — Master Frontend SpecKit
## Spécification Technique Complète · Production Ready · Frontend

> **Stack :** Angular 21 · NgRx 21 (Store · Effects · Entity · RouterStore · ComponentStore) · RxJS 7 · TypeScript 5.x strict · TailwindCSS 4 · Angular Material 21 · Keycloak-Angular 21  
> **Méthodologie :** Feature-First · Smart/Dumb Components · NgRx Entity Adapter · Signals + Store · OnPush partout · BDD  
> **Version :** 1.0 — Mai 2026 — Organisé dans l'ordre de développement · Production Ready · Document de référence unique frontend  
> **Basé sur :** `DOCAI_BACKEND_MASTER_SPECKIT_F.md` — **Version 15.0** — Mai 2026 · 20/20 · 100% Développable (SaaS complet + 11 ADR + 8 éléments manquants intégrés)  
> **Scope :** Spécification frontend uniquement — consomme les APIs définies dans le document backend de référence  
> **Analogie :** Chaque module frontend avance **en parallèle** du module backend correspondant

---

## Ordre de Développement

> **Ce document est organisé dans l'ordre exact de développement.**
> Lire et implémenter dans l'ordre de haut en bas.
> **Miroir exact de la structure backend v15.0.**

| Partie | Contenu Frontend | Durée | Prérequis |
|--------|-----------------|-------|-----------|
| **0 — Description & Vision** | Comprendre le produit, les écrans, les rôles | Lecture 1h | Aucun |
| **1 — Mise en place** | Projet Angular, CI/CD, standards | 1 semaine | Partie 0 lue |
| **2 — Commons Angular** | 7 librairies partagées réutilisables | 2 semaines | Partie 1 validée |
| **3 — Fondations SaaS** | Auth, Inscription, RGPD, Billing | 4 semaines | Partie 2 terminée |
| **4 — Pipeline** | Modules 1 à 4 (traitement documentaire) | 10 semaines | Partie 3 validée |
| **5 — Produit** | Dashboard, API Management, Billing UI | 5 semaines | Partie 4 fonctionnelle |
| **Annexes** | ADR frontend, Standards, Checklist | Référence permanente | — |

---

## Sommaire

- [PARTIE 0 — Description & Vision](#partie-0--description--vision)
- [PARTIE 1 — Mise en Place](#partie-1--mise-en-place)
  - [I.4bis — Pages d'erreur & Routing défensif](#i4bis--pages-derreur--routing-défensif)
  - [I.7 — Design System & Tokens CSS](#i7--design-system--tokens-css)
  - [I.8 — Mock API avec MSW](#i8--mock-api-avec-msw)
  - [I.9 — Storybook](#i9--storybook)
- [PARTIE 2 — Commons Angular](#partie-2--commons-angular)
  - [II.1bis — AuditService + AuditDirective](#ii1bis--auditservice--auditdirective)
  - [II.6bis — PerformanceService & Web Vitals](#ii6bis--performanceservice--web-vitals)
  - [II.8 — Shell Component](#ii8--shell-component-layout-principal-de-lapplication)
- [PARTIE 3 — Fondations SaaS](#partie-3--fondations-saas)
  - [Module 0.1 — Inscription & Équipe](#module-01--inscription--équipe)
  - [Module 0.2 — Login / Logout / 2FA](#module-02--login--logout--2fa)
  - [Module 0.3 — RGPD & Privacy](#module-03--rgpd--privacy)
  - [Module 0.4 — Billing & Abonnements](#module-04--billing--abonnements)
- [PARTIE 4 — Pipeline de Traitement](#partie-4--pipeline-de-traitement)
  - [Module 1 — Upload & Reconnaissance](#module-1--upload--reconnaissance)
  - [Module 2 — Extraction & Visualisation](#module-2--extraction--visualisation)
  - [Module 3 — Fraude & Révision Humaine](#module-3--fraude--révision-humaine)
  - [Module 4 — Pipeline & Monitoring](#module-4--pipeline--monitoring)
- [PARTIE 5 — Produit](#partie-5--produit)
  - [Module 5 — Dashboard & SSE](#module-5--dashboard--sse)
  - [Module 6 — API Management](#module-6--api-management)
  - [Module 7 — Billing UI](#module-7--billing-ui)
- [Annexes](#annexes)
  - [Annexe I — i18n (ngx-translate)](#annexe-i--i18n-préparation-backlog-v2)

---


# PARTIE 0 — DESCRIPTION & VISION

> **Lire en premier.** Comprendre les écrans, les parcours utilisateurs et les rôles avant d'écrire la première ligne de code.

## Le produit côté utilisateur

DocAI est un SaaS B2B avec **deux types d'expérience** :

**1 — L'application web** (ce document) : interface Angular pour les équipes internes des clients (comptables, analystes, réviseurs fraude, admins).

**2 — L'API publique** (consommée en Module 6) : intégration programmatique pour les systèmes clients.

## Parcours utilisateurs principaux

| Parcours | Rôle | Modules impliqués |
|----------|------|------------------|
| S'inscrire et créer son équipe | Futur TENANT_ADMIN | Module 0.1 |
| Se connecter, activer le 2FA | Tous rôles | Module 0.2 |
| Uploader un document et suivre son traitement | ANALYST | Module 1 |
| Visualiser et corriger les données extraites | ANALYST | Module 2 |
| Réviser les documents suspects | FRAUD_REVIEWER | Module 3 |
| Gérer la DLQ et rejouer des messages | TENANT_ADMIN | Module 4 |
| Consulter le dashboard temps réel | Tous rôles | Module 5 |
| Créer des clés API et webhooks | TENANT_ADMIN | Module 6 |
| Gérer l'abonnement et les factures | TENANT_ADMIN | Module 7 |
| Exporter ses données / demander l'effacement | TENANT_ADMIN | Module 0.3 |

## Rôles et permissions (miroir backend)

| Rôle | Accès |
|------|-------|
| `TENANT_ADMIN` | Toutes les pages — gestion équipe, billing, API keys, RGPD, DLQ |
| `ANALYST` | Upload, liste documents, détail extraction, correction |
| `VIEWER` | Lecture seule — liste documents, détail, dashboard |
| `FRAUD_REVIEWER` | Queue de révision fraude, décisions, alertes SSE |

## Tableau d'analogie Backend ↔ Frontend (référence permanente)

| Module Backend v15.0 | Module Frontend | Déclencheur |
|---------------------|-----------------|-------------|
| Module 0.1 — Inscription & Équipe | Module 0.1 — Pages Signup & Invitations | SES configuré + endpoint `/v1/public/signup` |
| Module 0.2 — Login / Logout / 2FA | Module 0.2 — Pages Login & 2FA | Keycloak realm `docai` configuré |
| Module 0.3 — RGPD & Privacy | Module 0.3 — Pages RGPD | Endpoints `/v1/rgpd/*` disponibles |
| Module 0.4 — Billing & Abonnements | Module 0.4 — Pages Plans & Billing | Stripe + endpoints `/v1/billing/*` |
| Module 1 — Reconnaissance | Module 1 — Upload & Reconnaissance | `POST /v1/documents` disponible |
| Module 2 — Extraction | Module 2 — Extraction & Visualisation | `GET /v1/documents/{id}/extraction` disponible |
| Module 3 — Fraude | Module 3 — Fraude & Révision Humaine | `GET /v1/fraud/review-queue` disponible |
| Module 4 — Orchestration | Module 4 — Pipeline & Monitoring | `GET /v1/admin/dlq` disponible |
| Module 5 — Dashboard | Module 5 — Dashboard & SSE | `GET /v1/dashboard/summary` + SSE disponibles |
| Module 6 — API Publique | Module 6 — API Management | `GET /v1/api-keys` disponible |
| Module 7 — Billing | Module 7 — Billing UI complet | `GET /v1/billing/subscription` disponible |

---

# PARTIE 1 — MISE EN PLACE

> **Durée : 1 semaine**
> **Prérequis : Partie 0 lue**
> **Critère de passage : `ng serve` fonctionne, CI verte, linting zéro erreur**

## I.1 — Architecture Feature-First (Miroir Hexagonal)

| Concept Backend (Hexagonal) | Analogie Frontend | Rôle |
|----------------------------|------------------|------|
| `docai-domain` (Java pur) | `models/` + NgRx State | État métier pur, sans couplage Angular |
| `docai-application` (Use Cases) | NgRx Effects | Orchestre les appels HTTP, side effects |
| `Adapter IN REST` (Controller) | Smart Component (Container) | Dispatch actions, consomme le Store |
| `Adapter OUT MongoDB` (Repository) | API Service (`*-api.service.ts`) | Unique point HTTP vers le backend |
| Ports IN/OUT | Interfaces TypeScript | Contrat strict entre couches |
| Outbox Pattern | Effects + Optimistic Updates | Cohérence état ↔ serveur |


### 📋 Tâches — I.1 Architecture

- [ ] **Lire et comprendre** le schéma Feature-First → 0.5J
      📖 Cette section complète
- [ ] **Valider** la correspondance avec l'architecture hexagonale backend → 0.5J
      📖 Annexe A — ADR Backend → Impact Frontend

## I.2 — Structure du Projet

```
src/
├── app/
│   ├── core/                          ← Singletons, interceptors, guards globaux
│   │   ├── auth/                      Keycloak, JWT, guards, tokens
│   │   ├── api/                       Base URL, HttpClient wrapper
│   │   ├── sse/                       SSE service transversal
│   │   └── store/                     Root store, meta-reducers
│   │
│   ├── shared/                        ← Composants DUMB réutilisables
│   │   ├── components/
│   │   │   ├── badge/                 StatusBadge, RiskBadge, PlanBadge
│   │   │   ├── card/                  MetricCard, DocumentCard
│   │   │   ├── empty-state/           EmptyState
│   │   │   ├── error-boundary/        ErrorBoundary (retry button)
│   │   │   ├── loading-skeleton/      SkeletonLoader
│   │   │   ├── paginator/             Paginator
│   │   │   ├── file-drop-zone/        FileDropZone
│   │   │   ├── confirm-dialog/        ConfirmDialog
│   │   │   ├── quota-bar/             QuotaBar (used/total + couleur)
│   │   │   └── plan-chip/             FREE / STARTER / PRO chip
│   │   ├── pipes/
│   │   │   ├── relative-time.pipe.ts
│   │   │   ├── risk-label.pipe.ts
│   │   │   ├── doc-type-icon.pipe.ts
│   │   │   ├── file-size.pipe.ts
│   │   │   └── plan-label.pipe.ts
│   │   └── directives/
│   │       ├── has-role.directive.ts  *docaiHasRole="'ANALYST'"
│   │       ├── plan-gate.directive.ts *docaiPlanGate="'PRO'"
│   │       └── auto-focus.directive.ts
│   │
│   ├── features/
│   │   ├── auth/                      ← Module 0.2 (login, 2FA, reset MDP)
│   │   ├── signup/                    ← Module 0.1 (inscription, invitation)
│   │   ├── rgpd/                      ← Module 0.3 (export, effacement, rétention)
│   │   ├── billing/                   ← Module 0.4 + 7 (plans, Stripe, factures)
│   │   ├── documents/                 ← Module 1 + 2 (upload + extraction)
│   │   ├── fraud/                     ← Module 3 (révision fraude)
│   │   ├── pipeline/                  ← Module 4 (DLQ, monitoring)
│   │   ├── dashboard/                 ← Module 5 (dashboard + SSE)
│   │   └── settings/                  ← Module 6 (API keys, webhooks, équipe)
│   │
│   └── app.routes.ts
│
├── environments/
│   ├── environment.ts
│   └── environment.prod.ts
└── styles/
    ├── tokens.css
    └── global.css
```


### 📋 Tâches — I.2 Structure du Projet

- [ ] Créer les dossiers `core/`, `shared/`, `features/` → 0.5J
      📖 Cette section : arborescence complète
- [ ] Configurer les **path aliases** TypeScript (`@core`, `@shared`, `@features`) → 0.5J
      📖 Cette section : tsconfig.json
- [ ] Vérifier `ng build` sans erreur de path → 0.5J
      📖 I.6 — CI/CD Pipeline

## I.3 — Stack Technique

| Composant | Technologie | Version | Décision |
|-----------|-------------|---------|----------|
| **Framework** | Angular | 21 | LTS mai 2026, Signals natif, standalone par défaut |
| **State Management** | NgRx | 21 | Entity Adapter, Action Groups, `toSignal` |
| **Réactivité** | RxJS | 7.x | HTTP, SSE, debounce — combiné avec Signals |
| **Langage** | TypeScript | 5.x | `strict: true`, `noUncheckedIndexedAccess` |
| **Styles** | TailwindCSS | 4.x | Utility-first, JIT, purge automatique |
| **Composants UI** | Angular Material | 21 | CDK, table, dialog, snackbar, stepper |
| **Auth** | Keycloak-Angular | 21 | PKCE, refresh automatique, JWT transparent |
| **Paiement** | Stripe.js | — | Redirect vers Stripe Checkout — jamais de CB dans l'appli |
| **Tests unitaires** | Jest 29 + @testing-library/angular | — | Couverture, DX supérieure |
| **Tests E2E** | Playwright | — | Multi-browser, parcours critiques |
| **Linting** | ESLint + angular-eslint | — | Règles strict Angular, 0 warning |


### 📋 Tâches — I.3 Stack Technique

- [ ] **Lire** la table des dépendances et versions → 0.5J
      📖 Cette section : tableau des versions
- [ ] Vérifier compatibilité Angular 21 + NgRx 21 → 0.5J
      📖 Annexe K — Guide Onboarding

## I.4 — Setup du Projet

```bash
# 1. Créer le projet
ng new docai-frontend \
  --style=css --routing=true --ssr=false \
  --strict=true --standalone


### 📋 Tâches — I.4 Setup du Projet

**Phase Setup-A — Installation (Jour 1)**
- [ ] `ng new docai-frontend` avec les flags stricts → 0.5J
      📖 Cette section : bloc bash complet
- [ ] Installer NgRx (store, effects, entity, router-store, devtools) → 0.5J
      📖 Cette section : # 2. NgRx complet

**Phase Setup-B — Configuration (Jour 2)**
- [ ] Configurer Keycloak + TailwindCSS → 0.5J
      📖 Cette section : # 3. UI & Auth
- [ ] Configurer ESLint + Prettier → 0.5J
      📖 Cette section : # 7. Qualité
- [ ] Configurer path aliases TypeScript → 0.5J
      📖 I.2 — Structure du Projet

**Phase Setup-C — Workflow OpenAPI (Jour 3)**
- [ ] Configurer `npm run generate:api` → 0.5J
      📖 I.4.1 — Workflow OpenAPI Generator
- [ ] Tester `ng serve` + `npm run lint` zéro erreur → 0.5J
      📖 I.6 — CI/CD Pipeline

**Critère de passage :** `ng serve` fonctionne, CI verte, linting zéro erreur.

cd docai-frontend

# 2. NgRx complet
ng add @ngrx/store@latest
ng add @ngrx/effects@latest
ng add @ngrx/entity@latest
ng add @ngrx/router-store@latest
ng add @ngrx/store-devtools@latest

# 3. UI & Auth
ng add @angular/material
npm install keycloak-angular keycloak-js
npm install tailwindcss @tailwindcss/vite

# 4. Stripe
npm install @stripe/stripe-js

# 5. Tests
npm install -D jest jest-preset-angular \
  @testing-library/angular @testing-library/user-event
npm install -D @playwright/test

# 6. Types depuis spec OpenAPI backend
npm install -D @openapitools/openapi-generator-cli

# 7. Qualité
npm install -D eslint @angular-eslint/eslint-plugin prettier
```

### I.4.1 — Workflow OpenAPI Generator

> **Objectif :** Les types TypeScript sont générés depuis la spec OpenAPI du backend — jamais écrits à la main.

```bash
# Ajouter dans package.json → scripts :
# "generate:api": "openapi-generator-cli generate ..."

npm run generate:api
```

```json
// package.json — scripts complets
{
  "scripts": {
    "generate:api": "openapi-generator-cli generate -i http://localhost:8080/v3/api-docs -g typescript-angular -o src/app/core/api/generated --additional-properties=ngVersion=21,supportsES6=true,withInterfaces=true,fileNaming=kebab-case",
    "generate:api:ci": "openapi-generator-cli generate -i https://api.docai.fr/v3/api-docs -g typescript-angular -o src/app/core/api/generated --additional-properties=ngVersion=21,supportsES6=true,withInterfaces=true,fileNaming=kebab-case"
  }
}
```

**Règles d'utilisation des types générés :**

| Règle | Détail |
|-------|--------|
| Output path | `src/app/core/api/generated/` — ne jamais modifier manuellement |
| Re-générer | Après chaque changement de contrat API (nouveau endpoint, nouveau champ) |
| Gitignore | Ne pas committer les fichiers générés — les générer au build CI |
| Modèles propres | Les interfaces métier (`document.model.ts`) peuvent étendre les types générés |
| Conflict | En cas de désaccord type généré vs backend réel → ouvrir un ticket backend |

```typescript
// Exemple : étendre un type généré sans le modifier
// core/api/generated/model/documentResponse.ts  ← GÉNÉRÉ, ne pas toucher

// features/documents/document.model.ts  ← modèle métier propre
import type { DocumentResponse } from '@core/api/generated';

export interface Document extends DocumentResponse {
  // Extensions frontend uniquement (champs calculés, UI state)
  readonly displayName: string;   // computed depuis fileName
}
```

```bash
# Vérifier que le backend est bien démarré avant de générer
curl http://localhost:8080/v3/api-docs | head -5
npm run generate:api

# En CI (staging/prod) :
npm run generate:api:ci
```

**⚠️ Ne jamais committer `src/app/core/api/generated/`** — ajouter au `.gitignore` :
```
# OpenAPI generated types
src/app/core/api/generated/
```



### tsconfig.json — TypeScript strict

```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "noImplicitOverride": true,
    "forceConsistentCasingInFileNames": true,
    "target": "ES2022",
    "module": "ES2022",
    "lib": ["ES2022", "dom"],
    "moduleResolution": "bundler",
    "paths": {
      "@core/*":     ["src/app/core/*"],
      "@shared/*":   ["src/app/shared/*"],
      "@features/*": ["src/app/features/*"],
      "@env/*":      ["src/environments/*"]
    }
  }
}
```

### Root Store Configuration

```typescript
// app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(APP_ROUTES, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor, errorInterceptor, tenantInterceptor])
    ),
    provideStore({}, {
      runtimeChecks: {
        strictStateImmutability: true,
        strictActionImmutability: true,
        strictStateSerializability: true,
        strictActionSerializability: true,
        strictActionWithinNgZone: true,
        strictActionTypeUniqueness: true,
      },
    }),
    provideEffects(),
    provideRouterStore(),
    provideStoreDevtools({ maxAge: 25, logOnly: !isDevMode() }),
  ],
};
```

### Routing Racine — Lazy Loading strict

```typescript
// app.routes.ts
export const APP_ROUTES: Routes = [
  // ─── Routes publiques (sans auth) ────────────────────────
  {
    path: 'signup',
    loadChildren: () =>
      import('@features/signup/signup.routes').then((m) => m.SIGNUP_ROUTES),
  },
  {
    path: 'auth',
    loadChildren: () =>
      import('@features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },

  // ─── Routes protégées (auth requise) ─────────────────────
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('@shared/components/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        loadChildren: () =>
          import('@features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
      },
      {
        path: 'documents',
        loadChildren: () =>
          import('@features/documents/documents.routes').then((m) => m.DOCUMENT_ROUTES),
      },
      {
        path: 'fraud',
        loadChildren: () =>
          import('@features/fraud/fraud.routes').then((m) => m.FRAUD_ROUTES),
        canActivate: [roleGuard(['FRAUD_REVIEWER', 'TENANT_ADMIN'])],
      },
      {
        path: 'pipeline',
        loadChildren: () =>
          import('@features/pipeline/pipeline.routes').then((m) => m.PIPELINE_ROUTES),
        canActivate: [roleGuard(['TENANT_ADMIN'])],
      },
      {
        path: 'settings',
        loadChildren: () =>
          import('@features/settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
        canActivate: [roleGuard(['TENANT_ADMIN'])],
      },
      {
        path: 'billing',
        loadChildren: () =>
          import('@features/billing/billing.routes').then((m) => m.BILLING_ROUTES),
        canActivate: [roleGuard(['TENANT_ADMIN'])],
      },
      {
        path: 'rgpd',
        loadChildren: () =>
          import('@features/rgpd/rgpd.routes').then((m) => m.RGPD_ROUTES),
        canActivate: [roleGuard(['TENANT_ADMIN'])],
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
```



## I.4bis — Pages d'erreur & Routing défensif

> **Durée : incluse dans la semaine de mise en place**
> **Prérequis : Structure du projet (I.2)**
> **Critère de passage : Naviguer vers `/error/404` affiche la bonne page. Le feature flag `maintenance.mode` redirige tout vers la page de maintenance.**


### 📋 Tâches — I.4bis Pages d'erreur

- [ ] Créer `NotFoundPageComponent` (404) → 0.5J
      📖 Cette section : ### NotFoundPageComponent
- [ ] Créer `ForbiddenPageComponent` (403) → 0.5J
      📖 Cette section : ### ForbiddenPageComponent
- [ ] Créer `ServerErrorPageComponent` (500) → 0.5J
      📖 Cette section : ### ServerErrorPageComponent
- [ ] Créer `MaintenancePageComponent` → 0.5J
      📖 Cette section : ### MaintenancePageComponent
- [ ] Mettre à jour `app.routes.ts` (wildcard → 404) → 0.5J
      📖 Cette section : ### Routes à ajouter
- [ ] Créer `maintenanceGuard` branché sur feature flag → 0.5J
      📖 Cette section : ### maintenanceGuard
- [ ] Mettre à jour `errorInterceptor` (403 → page, 5xx → page) → 0.5J
      📖 Cette section : ### Mise à jour errorInterceptor

**Critère de passage :** `/error/404` s'affiche, wildcard ne redirige plus vers dashboard.

### Pourquoi maintenant

Le routing actuel finit par `{ path: '**', redirectTo: 'dashboard' }`. Un utilisateur non authentifié qui arrive sur une URL invalide se retrouve dans une boucle de redirections. Les pages d'erreur doivent être posées **avant** de développer les features pour que les guards et l'intercepteur puissent les utiliser dès le Module 0.2.

### Routes à ajouter dans app.routes.ts

```typescript
// app.routes.ts — REMPLACER { path: '**', redirectTo: 'dashboard' } par :

// ─── Routes d'erreur (publiques — pas de authGuard) ──────────────────
{
  path: 'error',
  children: [
    {
      path: '403',
      loadComponent: () =>
        import('@shared/components/error-pages/forbidden-page.component')
          .then((m) => m.ForbiddenPageComponent),
      title: 'Accès refusé — DocAI',
    },
    {
      path: '404',
      loadComponent: () =>
        import('@shared/components/error-pages/not-found-page.component')
          .then((m) => m.NotFoundPageComponent),
      title: 'Page introuvable — DocAI',
    },
    {
      path: '500',
      loadComponent: () =>
        import('@shared/components/error-pages/server-error-page.component')
          .then((m) => m.ServerErrorPageComponent),
      title: 'Erreur serveur — DocAI',
    },
    {
      path: 'maintenance',
      loadComponent: () =>
        import('@shared/components/error-pages/maintenance-page.component')
          .then((m) => m.MaintenancePageComponent),
      title: 'Maintenance — DocAI',
    },
  ],
},

// ─── Wildcard : 404 (jamais redirectTo: dashboard) ───────────────────
{
  path: '**',
  loadComponent: () =>
    import('@shared/components/error-pages/not-found-page.component')
      .then((m) => m.NotFoundPageComponent),
  title: 'Page introuvable — DocAI',
},
```

### Structure des fichiers

```
shared/components/error-pages/
├── forbidden-page.component.ts       ← 403 — rôle insuffisant
├── not-found-page.component.ts       ← 404 — URL inconnue
├── server-error-page.component.ts    ← 500 — erreur backend
└── maintenance-page.component.ts     ← Maintenance (feature flag)
```

### NotFoundPageComponent

```typescript
// shared/components/error-pages/not-found-page.component.ts
@Component({
  selector: 'docai-not-found-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-6 p-8 text-center">
      <span class="text-8xl font-bold text-gray-200" aria-hidden="true">404</span>
      <h1 class="text-2xl font-semibold text-gray-800">Page introuvable</h1>
      <p class="text-gray-500 max-w-md">
        La page que vous cherchez n'existe pas ou a été déplacée.
      </p>
      <a routerLink="/dashboard"
         class="inline-flex items-center gap-2 px-4 py-2 rounded-md
                bg-primary text-white hover:bg-primary-700 transition-colors">
        Retour au dashboard
      </a>
    </div>
  `,
})
export class NotFoundPageComponent {}
```

### ForbiddenPageComponent

```typescript
// shared/components/error-pages/forbidden-page.component.ts
@Component({
  selector: 'docai-forbidden-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-6 p-8 text-center">
      <span class="text-8xl font-bold text-gray-200" aria-hidden="true">403</span>
      <h1 class="text-2xl font-semibold text-gray-800">Accès refusé</h1>
      <p class="text-gray-500 max-w-md">
        Vous n'avez pas les permissions pour accéder à cette page.
        Contactez votre administrateur si vous pensez que c'est une erreur.
      </p>
      <a routerLink="/dashboard"
         class="inline-flex items-center gap-2 px-4 py-2 rounded-md
                bg-primary text-white hover:bg-primary-700 transition-colors">
        Retour au dashboard
      </a>
    </div>
  `,
})
export class ForbiddenPageComponent {}
```

### ServerErrorPageComponent

```typescript
// shared/components/error-pages/server-error-page.component.ts
@Component({
  selector: 'docai-server-error-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-6 p-8 text-center">
      <span class="text-8xl font-bold text-gray-200" aria-hidden="true">500</span>
      <h1 class="text-2xl font-semibold text-gray-800">Erreur serveur</h1>
      <p class="text-gray-500 max-w-md">
        Une erreur inattendue s'est produite. Notre équipe a été notifiée automatiquement.
      </p>
      <div class="flex gap-3">
        <button (click)="retry()"
                class="px-4 py-2 rounded-md bg-primary text-white hover:bg-primary-700 transition-colors">
          Réessayer
        </button>
        <a routerLink="/dashboard"
           class="px-4 py-2 rounded-md border border-gray-300 hover:bg-gray-50 transition-colors">
          Dashboard
        </a>
      </div>
    </div>
  `,
})
export class ServerErrorPageComponent {
  protected retry(): void { window.location.reload(); }
}
```

### MaintenancePageComponent

```typescript
// shared/components/error-pages/maintenance-page.component.ts
@Component({
  selector: 'docai-maintenance-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-6 p-8 text-center bg-gray-50">
      <div class="w-16 h-16 rounded-2xl bg-primary-100 flex items-center justify-center">
        <svg class="w-8 h-8 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M11.42 15.17L17.25 21A2.652 2.652 0 0021 17.25l-5.877-5.877M11.42 15.17l2.496-3.03
                   c.317-.384.74-.626 1.208-.766M11.42 15.17l-4.655 5.653a2.548 2.548 0 11-3.586-3.586
                   l6.837-5.63m5.108-.233c.55-.164 1.163-.188 1.743-.14a4.5 4.5 0 004.486-6.336
                   l-3.276 3.277a3.004 3.004 0 01-2.25-2.25l3.276-3.276a4.5 4.5 0 00-6.336 4.486
                   c.091 1.076-.071 2.264-.904 2.95l-.102.085m-1.745 1.437L5.909 7.5H4.5L2.25 3.75
                   l1.5-1.5L7.5 4.5v1.409l4.26 4.26m-1.745 1.437l1.745-1.437m6.615 8.206L15.75 15.75
                   M4.867 19.125h.008v.008h-.008v-.008z" />
        </svg>
      </div>
      <h1 class="text-2xl font-semibold text-gray-800">Maintenance en cours</h1>
      <p class="text-gray-500 max-w-md">
        DocAI est temporairement indisponible. Nous serons de retour très bientôt.
      </p>
      <p class="text-sm text-gray-400">
        Suivez nos mises à jour sur
        <a href="https://status.docai.fr" target="_blank" rel="noopener"
           class="text-primary-600 hover:underline">status.docai.fr</a>
      </p>
    </div>
  `,
})
export class MaintenancePageComponent {}
```

### maintenanceGuard — Branché sur le feature flag

```typescript
// core/auth/maintenance.guard.ts
export const maintenanceGuard: CanActivateFn = () => {
  const flags  = inject(FeatureFlagsService);
  const router = inject(Router);
  if (flags.isEnabled('maintenance.mode')) {
    return router.createUrlTree(['/error/maintenance']);
  }
  return true;
};

// Dans app.routes.ts — ajouter sur la route racine protégée :
// canActivate: [authGuard, maintenanceGuard]   ← ajouter maintenanceGuard
```

### Mise à jour errorInterceptor — Redirection 403 & 5xx

```typescript
// core/api/error.interceptor.ts — COMPLÉTER le switch existant :
case 403:
  router.navigate(['/error/403']);   // Redirection page dédiée (remplace le snackbar seul)
  break;

case 429:
  snack.open(
    `Quota dépassé${problem?.retryAfter ? ' — réessayez dans ' + problem.retryAfter + 's' : ''}`,
    'Upgrade', { duration: 8000 },
  ).onAction().subscribe(() => router.navigate(['/billing/plans']));
  break;

default:
  if (err.status >= 500) {
    if (flags.isEnabled('maintenance.mode')) {
      router.navigate(['/error/maintenance']);
    } else {
      router.navigate(['/error/500']);
    }
  }
```

### Definition of Done — I.4bis

- [ ] Routes `/error/403`, `/error/404`, `/error/500`, `/error/maintenance` opérationnelles
- [ ] Wildcard `**` → page 404 (plus `redirectTo: 'dashboard'`)
- [ ] `errorInterceptor` redirige vers `/error/403` sur HTTP 403
- [ ] `errorInterceptor` redirige vers `/error/500` sur HTTP 5xx
- [ ] `maintenanceGuard` redirige toutes les routes protégées si `maintenance.mode = true`
- [ ] Lighthouse Accessibility = 100 sur les 4 pages (alt vide sur images décoratives)
- [ ] Tests snapshot Jest pour les 4 pages

---


## I.5 — Design Patterns Angular (tous modules)

| Pattern | Implémentation | Module(s) |
|---------|---------------|-----------|
| **Smart / Dumb Components** | Containers dispatch/select · Dumb = @Input/@Output only | Tous |
| **OnPush partout** | `ChangeDetectionStrategy.OnPush` obligatoire | Tous |
| **Signals + Store** | `toSignal(store.select(...))` dans les Containers | Tous |
| **inject() function** | Pas de constructor injection | Tous |
| **Entity Adapter** | `createEntityAdapter<T>` pour toute collection | Tous |
| **Action Groups** | `createActionGroup` — jamais `createAction` isolé | Tous |
| **Standalone Components** | `standalone: true` — zéro NgModule | Tous |
| **New Control Flow** | `@if`, `@for`, `@switch`, `@defer` — jamais `*ngIf`, `*ngFor` | Tous |
| **Functional Guards** | `canActivate: [authGuard]` | Module 0.2 |
| **Functional Interceptors** | `withInterceptors([...])` | Core |
| **Injection Tokens** | `InjectionToken<T>` pour config, base URL | Core |
| **Optimistic Update** | Mise à jour store avant réponse HTTP | Module 2, 3 |
| **Plan Gate** | `*docaiPlanGate="'PRO'"` — features verrouillées selon plan | Module 0.4, 7 |


### 📋 Tâches — I.5 Design Patterns

- [ ] **Lire** la table des patterns Smart/Dumb → 0.5J
      📖 Cette section : tableau complet
- [ ] Créer un composant Smart + Dumb exemple pour valider la compréhension → 0.5J
      📖 II.8 — Shell Component (exemple Smart complet)

## I.6 — CI/CD Pipeline Frontend

```
git push
    ↓
Phase 1 — LINT & FORMAT     ESLint + Prettier + tsc --noEmit
    ↓
Phase 2 — TESTS UNITAIRES   Jest (≥95% store, ≥80% composants)
    ↓
Phase 3 — BUILD             ng build --prod (budgets vérifiés)
    ↓
Phase 4 — E2E Playwright    Parcours critiques (signup, upload, fraude, billing)
    ↓
Phase 5 — DOCKER BUILD      nginx:1.27-alpine + Trivy scan
    ↓
Phase 6 — DEPLOY            Staging auto / Production avec approbation
```


### 📋 Tâches — I.6 CI/CD Pipeline

- [ ] Créer `.github/workflows/ci.yml` → 1J
      📖 Cette section : bloc YAML complet
- [ ] Configurer quality gates (lint, tests, build, bundle size) → 0.5J
      📖 Cette section : étapes du pipeline
- [ ] Créer `nginx.conf` pour le container Docker → 0.5J
      📖 Cette section : ### nginx.conf
- [ ] Vérifier CI verte sur `main` → 0.5J
      📖 Annexe K — Guide Onboarding

**Critère de passage :** CI verte sur `main`, build Docker fonctionnel.

### Quality Gates Frontend

| Métrique | Seuil | Outil |
|----------|-------|-------|
| Couverture store (reducers + selectors) | ≥ 95% | Jest |
| Couverture services | ≥ 90% | Jest |
| Couverture composants | ≥ 80% | Jest |
| Bundle initial | < 500 kB | Angular budgets |
| Chunk par feature | < 200 kB | Angular budgets |
| Lighthouse Performance | ≥ 90 | CI |
| Lighthouse Accessibility | 100 | CI |
| ESLint warnings | 0 | ESLint |

### Dockerfile Frontend

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --prefer-offline
COPY . .
RUN npm run build -- --configuration=production

FROM nginx:1.27-alpine AS runtime
COPY --from=builder /app/dist/docai-frontend/browser /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s \
  CMD wget -qO- http://localhost/health || exit 1
```

### nginx.conf (SPA + sécurité)

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;

  location / { try_files $uri $uri/ /index.html; }

  location ~* \.(js|css|png|jpg|svg|woff2)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
  }

  add_header X-Frame-Options "DENY";
  add_header X-Content-Type-Options "nosniff";
  add_header Content-Security-Policy
    "default-src 'self'; connect-src 'self' https://api.docai.fr https://auth.docai.fr https://js.stripe.com;
     script-src 'self' https://js.stripe.com; frame-src https://js.stripe.com;";

  location /health { return 200 'ok'; add_header Content-Type text/plain; }
}
```

---



## I.7 — Design System & Tokens CSS

> **Durée : incluse dans la semaine de mise en place (Partie 1)**
> **Prérequis : Structure du projet créée (I.2)**
> **Critère de passage : `npm run build` compile sans erreur de variable CSS. Aucun composant ne contient de couleur hardcodée.**


### 📋 Tâches — I.7 Design System & Tokens CSS

- [ ] Créer `styles/tokens.css` (couleurs primaires, risk, statuts, plans) → 1J
      📖 Cette section : ### styles/tokens.css — Contenu complet
- [ ] Créer `styles/global.css` avec import tokens + reset → 0.5J
      📖 Cette section : ### styles/global.css
- [ ] Configurer `tailwind.config.ts` câblé sur les tokens → 0.5J
      📖 Cette section : ### tailwind.config.ts
- [ ] Charger la police Inter (assets/fonts/ ou CDN) → 0.5J
      📖 Cette section : ### Definition of Done I.7
- [ ] Vérifier `ng build` sans warning bundle → 0.5J
      📖 I.6 — CI/CD Pipeline

**Critère de passage :** Aucun composant ne contient de couleur hardcodée.

### Pourquoi définir les tokens maintenant

Les tokens CSS sont la fondation de **tous** les composants. Les définir en Partie 1 évite la réécriture des composants Dumb produits en Partie 2. Un dev qui commence un badge sans tokens va hardcoder des couleurs — et corriger ça en cours de projet est coûteux.

### styles/tokens.css — Contenu complet

```css
/* styles/tokens.css
   Importé dans styles/global.css : @import './tokens.css';
*/

:root {

  /* ─── Couleurs primaires DocAI (Indigo) ─────────────────────────── */
  --docai-primary-50:  #EEF2FF;
  --docai-primary-100: #E0E7FF;
  --docai-primary-200: #C7D2FE;
  --docai-primary-400: #818CF8;
  --docai-primary-600: #4F46E5;   /* Couleur principale — boutons, liens actifs */
  --docai-primary-700: #4338CA;   /* Hover */
  --docai-primary-900: #312E81;   /* Texte sur fond clair */

  /* ─── Sémantique UI ─────────────────────────────────────────────── */
  --docai-success:        #16A34A;
  --docai-success-bg:     #F0FDF4;
  --docai-success-border: #BBF7D0;

  --docai-warning:        #D97706;
  --docai-warning-bg:     #FFFBEB;
  --docai-warning-border: #FDE68A;

  --docai-danger:         #DC2626;
  --docai-danger-bg:      #FEF2F2;
  --docai-danger-border:  #FECACA;

  --docai-info:           #2563EB;
  --docai-info-bg:        #EFF6FF;
  --docai-info-border:    #BFDBFE;

  /* ─── Neutrals ──────────────────────────────────────────────────── */
  --docai-gray-50:  #F9FAFB;
  --docai-gray-100: #F3F4F6;
  --docai-gray-200: #E5E7EB;
  --docai-gray-300: #D1D5DB;
  --docai-gray-400: #9CA3AF;
  --docai-gray-500: #6B7280;
  --docai-gray-600: #4B5563;
  --docai-gray-700: #374151;
  --docai-gray-800: #1F2937;
  --docai-gray-900: #111827;

  /* ─── Risk Levels (Module 3 — fraude) ──────────────────────────── */
  /* RÈGLE : utiliser UNIQUEMENT ces variables pour les couleurs de risque */
  --docai-risk-faible-text:    #166534;
  --docai-risk-faible-bg:      #F0FDF4;
  --docai-risk-faible-border:  #BBF7D0;

  --docai-risk-modere-text:    #92400E;
  --docai-risk-modere-bg:      #FFFBEB;
  --docai-risk-modere-border:  #FDE68A;

  --docai-risk-eleve-text:     #991B1B;
  --docai-risk-eleve-bg:       #FEF2F2;
  --docai-risk-eleve-border:   #FECACA;

  --docai-risk-critique-text:  #4C1D95;
  --docai-risk-critique-bg:    #F5F3FF;
  --docai-risk-critique-border:#DDD6FE;

  /* ─── Document Status (Modules 1, 2) ───────────────────────────── */
  --docai-status-pending-text:   #6B7280;
  --docai-status-pending-bg:     #F3F4F6;

  --docai-status-running-text:   #1D4ED8;
  --docai-status-running-bg:     #EFF6FF;

  --docai-status-completed-text: #166534;
  --docai-status-completed-bg:   #F0FDF4;

  --docai-status-failed-text:    #991B1B;
  --docai-status-failed-bg:      #FEF2F2;

  --docai-status-review-text:    #92400E;
  --docai-status-review-bg:      #FFFBEB;

  /* ─── Plan Colors (Modules 0.4, 7) ─────────────────────────────── */
  --docai-plan-free-text:        #6B7280;
  --docai-plan-free-bg:          #F3F4F6;
  --docai-plan-starter-text:     #1D4ED8;
  --docai-plan-starter-bg:       #EFF6FF;
  --docai-plan-pro-text:         #4338CA;
  --docai-plan-pro-bg:           #EEF2FF;
  --docai-plan-enterprise-text:  #92400E;
  --docai-plan-enterprise-bg:    #FFFBEB;

  /* ─── Shell Layout ──────────────────────────────────────────────── */
  /* Utilisées dans ShellComponent — ne jamais hardcoder dans les templates */
  --docai-sidebar-width:      240px;
  --docai-sidebar-collapsed:  64px;
  --docai-topbar-height:      64px;
  --docai-banner-height:      48px;
  --docai-content-padding:    24px;
  --docai-content-max-width:  1280px;

  /* ─── Spacing ────────────────────────────────────────────────────── */
  --space-1:  4px;
  --space-2:  8px;
  --space-3:  12px;
  --space-4:  16px;
  --space-5:  20px;
  --space-6:  24px;
  --space-8:  32px;
  --space-10: 40px;
  --space-12: 48px;
  --space-16: 64px;

  /* ─── Border radius ─────────────────────────────────────────────── */
  --radius-xs:   2px;
  --radius-sm:   4px;
  --radius-md:   8px;
  --radius-lg:   12px;
  --radius-xl:   16px;
  --radius-full: 9999px;

  /* ─── Typographie ───────────────────────────────────────────────── */
  --font-base: 'Inter', system-ui, -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;

  --text-xs:   12px;
  --text-sm:   14px;
  --text-base: 16px;
  --text-lg:   18px;
  --text-xl:   20px;
  --text-2xl:  24px;
  --text-3xl:  30px;
  --text-4xl:  36px;

  --font-normal:   400;
  --font-medium:   500;
  --font-semibold: 600;
  --font-bold:     700;

  --leading-tight:   1.25;
  --leading-normal:  1.5;
  --leading-relaxed: 1.75;

  /* ─── Shadows ───────────────────────────────────────────────────── */
  --shadow-xs: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-sm: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);

  /* ─── Transitions ───────────────────────────────────────────────── */
  --transition-fast:   100ms ease;
  --transition-normal: 200ms ease;
  --transition-slow:   350ms ease;

  /* ─── Z-index ───────────────────────────────────────────────────── */
  --z-raised:   10;
  --z-sidebar:  100;
  --z-topbar:   200;
  --z-banner:   300;
  --z-dropdown: 350;
  --z-dialog:   400;
  --z-snackbar: 500;
  --z-tooltip:  600;
}
```

### styles/global.css

```css
/* styles/global.css */
@import './tokens.css';
@import 'tailwindcss';

*, *::before, *::after { box-sizing: border-box; }

html {
  font-family: var(--font-base);
  font-size: var(--text-base);
  line-height: var(--leading-normal);
  color: var(--docai-gray-900);
  background-color: var(--docai-gray-50);
  -webkit-font-smoothing: antialiased;
}

/* Scrollbar */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--docai-gray-300); border-radius: var(--radius-full); }
::-webkit-scrollbar-thumb:hover { background: var(--docai-gray-400); }

/* Focus visible — Lighthouse Accessibility = 100 */
:focus-visible {
  outline: 2px solid var(--docai-primary-600);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}

/* Angular Material overrides */
.mat-mdc-snack-bar-container { z-index: var(--z-snackbar) !important; }
.mdc-dialog__container       { z-index: var(--z-dialog)   !important; }

/* Utilitaires DocAI (complètent Tailwind) */
.docai-card {
  background: white;
  border: 1px solid var(--docai-gray-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.docai-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  line-height: var(--leading-tight);
}
```

### tailwind.config.ts — Câblé sur les tokens

```typescript
// tailwind.config.ts
import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  'var(--docai-primary-50)',
          100: 'var(--docai-primary-100)',
          200: 'var(--docai-primary-200)',
          400: 'var(--docai-primary-400)',
          DEFAULT: 'var(--docai-primary-600)',
          700: 'var(--docai-primary-700)',
          900: 'var(--docai-primary-900)',
        },
        risk: {
          faible:   'var(--docai-risk-faible-text)',
          modere:   'var(--docai-risk-modere-text)',
          eleve:    'var(--docai-risk-eleve-text)',
          critique: 'var(--docai-risk-critique-text)',
        },
      },
      spacing: {
        'sidebar':    'var(--docai-sidebar-width)',
        'sidebar-sm': 'var(--docai-sidebar-collapsed)',
        'topbar':     'var(--docai-topbar-height)',
        'banner':     'var(--docai-banner-height)',
      },
      borderRadius: {
        'sm': 'var(--radius-sm)',
        'md': 'var(--radius-md)',
        'lg': 'var(--radius-lg)',
        'xl': 'var(--radius-xl)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      zIndex: {
        'sidebar':  '100',
        'topbar':   '200',
        'banner':   '300',
        'dialog':   '400',
        'snackbar': '500',
        'tooltip':  '600',
      },
      boxShadow: {
        'xs': 'var(--shadow-xs)',
        'sm': 'var(--shadow-sm)',
        'md': 'var(--shadow-md)',
      },
    },
  },
  plugins: [],
} satisfies Config;
```

### Règles d'utilisation (obligatoires dans tous les composants)

| Mauvais ❌ | Bien ✅ |
|-----------|--------|
| `color: #166534` | `color: var(--docai-risk-faible-text)` |
| `background: #F0FDF4` | `background: var(--docai-status-completed-bg)` |
| `style="z-index: 300"` | classe Tailwind `z-banner` |
| `margin: 24px` | `m-6` Tailwind ou `var(--space-6)` |
| `color: #4338CA` | `color: var(--docai-plan-pro-text)` |

### Definition of Done — I.7

- [ ] `tokens.css` importé en tête de `global.css` avant TailwindCSS
- [ ] `tailwind.config.ts` référence toutes les custom properties
- [ ] Police Inter chargée (`assets/fonts/` ou CDN dans `index.html`)
- [ ] `ng build` compile sans warning bundle (les tokens = 0 kB JS)
- [ ] ESLint rule custom : interdire les couleurs hex dans les `.ts` et `.html` (optionnel — backlog)

---



## I.8 — Mock API avec MSW (Mock Service Worker)

> **Durée : incluse dans la semaine de mise en place**
> **Prérequis : Structure du projet (I.2)**
> **Critère de passage : `npm run dev:mock` démarre l'app sans backend. Toutes les features sont navigables avec des données réalistes.**


### 📋 Tâches — I.8 Mock API avec MSW

- [ ] Installer MSW + générer `mockServiceWorker.js` → 0.5J
      📖 Cette section : ### Installation
- [ ] Créer `src/mocks/browser.ts` + `handlers/index.ts` → 0.5J
      📖 Cette section : ### browser.ts / ### handlers/index.ts
- [ ] Créer handlers pour chaque module (documents, fraud, billing, dashboard, settings, admin) → 1J
      📖 Cette section : ### Exemple — documents.handlers.ts
- [ ] Créer fixtures typées couvrant tous les statuts → 1J
      📖 Cette section : ### fixtures/documents.fixtures.ts
- [ ] Activer MSW dans `main.ts` (dev uniquement) → 0.5J
      📖 Cette section : ### Activation en développement
- [ ] Ajouter scripts `dev:mock` et `dev:real` dans `package.json` → 0.5J
      📖 Cette section : ### Scripts npm

**Critère de passage :** `npm run dev:mock` démarre sans backend, tous les parcours navigables.

### Pourquoi MSW est indispensable

Le frontend et le backend se développent **en parallèle**. Sans MSW, chaque développeur frontend est bloqué dès que le backend n'est pas disponible ou qu'un endpoint n'est pas encore implémenté. MSW intercepte les requêtes HTTP directement dans le browser — sans proxy, sans serveur séparé.

### Installation

```bash
npm install -D msw@2
npx msw init public/ --save
```

```bash
# Vérifier que le fichier service worker a bien été créé
ls public/mockServiceWorker.js   # doit exister
```

### Structure des fichiers

```
src/
└── mocks/
    ├── browser.ts                 ← Setup MSW worker (dev uniquement)
    ├── handlers/
    │   ├── index.ts               ← Exporte tous les handlers
    │   ├── auth.handlers.ts       ← /v1/public/signup, /v1/team/*
    │   ├── documents.handlers.ts  ← /v1/documents/*
    │   ├── fraud.handlers.ts      ← /v1/fraud/*
    │   ├── billing.handlers.ts    ← /v1/billing/*
    │   ├── dashboard.handlers.ts  ← /v1/dashboard/*
    │   ├── settings.handlers.ts   ← /v1/api-keys, /v1/webhooks
    │   └── admin.handlers.ts      ← /v1/admin/dlq, /v1/admin/pipeline
    └── fixtures/
        ├── documents.fixtures.ts  ← 10 documents en différents états
        ├── fraud.fixtures.ts      ← 5 analyses fraude (scores variés)
        └── billing.fixtures.ts    ← 4 plans + état subscription
```

### browser.ts

```typescript
// src/mocks/browser.ts
import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);
```

### handlers/index.ts

```typescript
// src/mocks/handlers/index.ts
import { authHandlers }      from './auth.handlers';
import { documentHandlers }  from './documents.handlers';
import { fraudHandlers }     from './fraud.handlers';
import { billingHandlers }   from './billing.handlers';
import { dashboardHandlers } from './dashboard.handlers';
import { settingsHandlers }  from './settings.handlers';
import { adminHandlers }     from './admin.handlers';

export const handlers = [
  ...authHandlers,
  ...documentHandlers,
  ...fraudHandlers,
  ...billingHandlers,
  ...dashboardHandlers,
  ...settingsHandlers,
  ...adminHandlers,
];
```

### Exemple — documents.handlers.ts

```typescript
// src/mocks/handlers/documents.handlers.ts
import { http, HttpResponse, delay } from 'msw';
import { documentFixtures }          from '../fixtures/documents.fixtures';

export const documentHandlers = [

  // GET /v1/documents — liste paginée
  http.get('/v1/documents', async ({ request }) => {
    await delay(200);   // Simuler la latence réseau
    const url    = new URL(request.url);
    const page   = Number(url.searchParams.get('page') ?? 0);
    const size   = Number(url.searchParams.get('size') ?? 20);
    const status = url.searchParams.get('status');

    let docs = documentFixtures;
    if (status) docs = docs.filter((d) => d.status === status);

    return HttpResponse.json({
      data: docs.slice(page * size, (page + 1) * size),
      page: {
        number: page,
        size,
        totalElements: docs.length,
        totalPages: Math.ceil(docs.length / size),
        first: page === 0,
        last: (page + 1) * size >= docs.length,
      },
    });
  }),

  // POST /v1/documents — upload
  http.post('/v1/documents', async () => {
    await delay(800);   // Simuler le traitement upload
    const newDoc = {
      ...documentFixtures[0],
      id:        crypto.randomUUID(),
      status:    'PENDING',
      createdAt: new Date().toISOString(),
    };
    return HttpResponse.json(newDoc, { status: 201 });
  }),

  // GET /v1/documents/:id
  http.get('/v1/documents/:id', async ({ params }) => {
    await delay(100);
    const doc = documentFixtures.find((d) => d.id === params['id'])
      ?? documentFixtures[0];
    return HttpResponse.json(doc);
  }),
];
```

### fixtures/documents.fixtures.ts

```typescript
// src/mocks/fixtures/documents.fixtures.ts
import type { Document } from '@features/documents/document.model';

export const documentFixtures: Document[] = [
  {
    id:                      'doc-001',
    tenantId:                'tenant-acme',
    fileName:                'facture-2024-001.pdf',
    fileSize:                245_760,
    mimeType:                'application/pdf',
    status:                  'COMPLETED',
    type:                    'FACTURE',
    classificationConfidence: 0.97,
    riskScore:               12,
    riskLevel:               'FAIBLE',
    pipelineSteps: [
      { name: 'CLASSIFICATION', status: 'DONE', durationMs: 1230 },
      { name: 'EXTRACTION',     status: 'DONE', durationMs: 2100 },
      { name: 'FRAUD_ANALYSIS', status: 'DONE', durationMs: 980  },
    ],
    contentHash: 'sha256:abc123',
    createdAt:   '2024-01-15T10:30:00Z',
    updatedAt:   '2024-01-15T10:30:45Z',
  },
  {
    id:        'doc-002',
    tenantId:  'tenant-acme',
    fileName:  'cni-martin-jean.jpg',
    fileSize:  512_000,
    mimeType:  'image/jpeg',
    status:    'NEEDS_REVIEW',
    type:      'CNI',
    classificationConfidence: 0.89,
    riskScore: 78,
    riskLevel: 'ELEVE',
    pipelineSteps: [
      { name: 'CLASSIFICATION', status: 'DONE',    durationMs: 980  },
      { name: 'EXTRACTION',     status: 'DONE',    durationMs: 1540 },
      { name: 'FRAUD_ANALYSIS', status: 'DONE',    durationMs: 2300 },
      { name: 'HUMAN_REVIEW',   status: 'RUNNING', durationMs: 0    },
    ],
    contentHash: 'sha256:def456',
    createdAt:   '2024-01-15T11:00:00Z',
    updatedAt:   '2024-01-15T11:05:12Z',
  },
  // Ajouter 8 autres fixtures couvrant tous les statuts et types
];
```

### Activation en développement — main.ts

```typescript
// src/main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig }            from './app/app.config';
import { AppComponent }         from './app/app.component';

async function bootstrap(): Promise<void> {
  // MSW activé uniquement en développement
  if (!environment.production) {
    const { worker } = await import('./mocks/browser');
    await worker.start({
      onUnhandledRequest: 'bypass',   // Laisser passer les requêtes non mockées (Keycloak, Stripe...)
    });
    console.log('[MSW] Mock Service Worker activé');
  }
  bootstrapApplication(AppComponent, appConfig);
}

bootstrap();
```

### Scripts npm

```json
// package.json
{
  "scripts": {
    "start":      "ng serve --proxy-config proxy.conf.json",
    "dev:mock":   "ng serve",
    "dev:real":   "ng serve --proxy-config proxy.conf.json"
  }
}
```

> **Convention :** `npm run dev:mock` = sans backend (MSW actif). `npm run dev:real` = avec backend réel (proxy vers localhost:8080).

### Definition of Done — I.8

- [ ] `public/mockServiceWorker.js` présent (généré par `npx msw init`)
- [ ] `dev:mock` démarre sans erreur de console
- [ ] Tous les parcours critiques navigables sans backend : signup → login → upload → fraude → billing
- [ ] Fixtures couvrent tous les statuts de `DocumentStatus` et `RiskLevel`
- [ ] Handlers simulent la latence réseau (`delay(200-800ms)`)
- [ ] `onUnhandledRequest: 'bypass'` — Keycloak et Stripe ne sont pas mockés
- [ ] `src/mocks/` exclu du build production (`tsconfig.app.json` → `exclude`)

---



## I.9 — Storybook — Catalogue des Composants Dumb

> **Durée : 2 jours (setup) + 30 min par composant Dumb**
> **Prérequis : Composants Dumb produits en Partie 2**
> **Critère de passage : Chaque composant Dumb a au minimum 3 stories (default, loading, error/empty).**


### 📋 Tâches — I.9 Storybook

- [ ] Installer Storybook + `addon-a11y` → 0.5J
      📖 Cette section : ### Installation
- [ ] Configurer `.storybook/main.ts` et `preview.ts` (tokens CSS) → 0.5J
      📖 Cette section : ### .storybook/main.ts / preview.ts
- [ ] Ajouter scripts `storybook` et `build-storybook` → 0.5J
      📖 Cette section : ### Scripts npm
- [ ] Créer stories pour les 10 composants Dumb prioritaires → 2J
      📖 Cette section : ### Composants Dumb prioritaires
- [ ] Vérifier `build-storybook` passe en CI → 0.5J
      📖 I.6 — CI/CD Pipeline

**Critère de passage :** localhost:6006 opérationnel, addon-a11y 0 violation critique.

### Pourquoi Storybook

La spec définit 30+ composants Dumb. Sans Storybook :
- Impossible de tester visuellement les états (loading skeleton, erreur, empty, variantes de couleur risk)
- Impossible de valider l'accessibilité en isolation
- Les designers ne peuvent pas reviewer sans lancer toute l'app

### Installation

```bash
npx storybook@latest init --type angular
npm install -D @storybook/angular @storybook/addon-essentials @storybook/addon-a11y
```

### .storybook/main.ts

```typescript
// .storybook/main.ts
import type { StorybookConfig } from '@storybook/angular';

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.ts'],
  addons: [
    '@storybook/addon-essentials',
    '@storybook/addon-a11y',      // ← Vérification accessibilité dans Storybook
  ],
  framework: { name: '@storybook/angular', options: {} },
};

export default config;
```

### .storybook/preview.ts

```typescript
// .storybook/preview.ts — importer les tokens CSS globaux
import type { Preview } from '@storybook/angular';
import '../src/styles/global.css';

const preview: Preview = {
  parameters: {
    backgrounds: {
      default: 'light',
      values: [
        { name: 'light', value: '#F9FAFB' },
        { name: 'white', value: '#FFFFFF' },
      ],
    },
    a11y: { config: { rules: [{ id: 'color-contrast', enabled: true }] } },
  },
};

export default preview;
```

### Exemple — RiskBadge.stories.ts

```typescript
// src/stories/shared/RiskBadge.stories.ts
import type { Meta, StoryObj } from '@storybook/angular';
import { RiskBadgeComponent }  from '@shared/components/badge/risk-badge.component';

const meta: Meta<RiskBadgeComponent> = {
  title:     'Shared/Badges/RiskBadge',
  component: RiskBadgeComponent,
  tags:      ['autodocs'],
  argTypes: {
    level: {
      control: 'select',
      options: ['FAIBLE', 'MODERE', 'ELEVE', 'CRITIQUE'],
    },
  },
};
export default meta;

type Story = StoryObj<RiskBadgeComponent>;

export const Faible:   Story = { args: { level: 'FAIBLE'   } };
export const Modere:   Story = { args: { level: 'MODERE'   } };
export const Eleve:    Story = { args: { level: 'ELEVE'    } };
export const Critique: Story = { args: { level: 'CRITIQUE' } };
```

### Exemple — QuotaBar.stories.ts

```typescript
// src/stories/shared/QuotaBar.stories.ts
import type { Meta, StoryObj } from '@storybook/angular';
import { QuotaBarComponent }   from '@shared/components/quota-bar/quota-bar.component';

const meta: Meta<QuotaBarComponent> = {
  title:     'Shared/QuotaBar',
  component: QuotaBarComponent,
  argTypes: {
    used:  { control: { type: 'range', min: 0, max: 1000 } },
    total: { control: { type: 'range', min: 100, max: 1000 } },
  },
};
export default meta;
type Story = StoryObj<QuotaBarComponent>;

export const Safe:     Story = { args: { used: 200,  total: 1000 } };   // < 70%
export const Warning:  Story = { args: { used: 750,  total: 1000 } };   // 70–90%
export const Critical: Story = { args: { used: 970,  total: 1000 } };   // > 90%
export const Full:     Story = { args: { used: 1000, total: 1000 } };   // 100%
```

### Scripts npm

```json
{
  "scripts": {
    "storybook":       "storybook dev -p 6006",
    "build-storybook": "storybook build"
  }
}
```

### Composants Dumb prioritaires à storybook-er (dans l'ordre)

| Composant | Stories minimales |
|-----------|-----------------|
| `StatusBadge` | Tous les `DocumentStatus` (8 variantes) |
| `RiskBadge` | 4 niveaux de risque |
| `PlanChip` | FREE / STARTER / PRO / ENTERPRISE |
| `QuotaBar` | Safe / Warning / Critical / Full |
| `SkeletonLoader` | 3 tailles |
| `EmptyState` | Avec/sans CTA |
| `ErrorBoundary` | Avec message + bouton retry |
| `FraudScoreGauge` | Scores 0 / 25 / 50 / 75 / 100 / -1 (partiel) |
| `ExtractionFieldRow` | VALID / CORRECTED / INVALID + confidence levels |
| `ShellBanner` | 4 types de bannière |

### Definition of Done — I.9

- [ ] Storybook démarre sur `localhost:6006`
- [ ] `@storybook/addon-a11y` activé — aucune violation critique sur les composants
- [ ] Chaque composant Dumb prioritaire a ≥ 3 stories (états principaux)
- [ ] `global.css` (tokens) chargé dans `preview.ts` — les couleurs sont correctes
- [ ] `build-storybook` passe en CI sans erreur

---


# PARTIE 2 — COMMONS ANGULAR

> **Durée : 2 semaines**
> **Prérequis : Partie 1 validée (CI verte)**
> **Critère de passage : Les 10 commons sont testés à 100% et utilisables par toutes les features**
> **Miroir exact des 7 commons Maven du backend + 3 commons purement frontend**

| Common Backend | Équivalent Angular | Rôle |
|---------------|-------------------|------|
| `commons-multitenancy` | `TenantInterceptor` + `TenantStore` | Injecte `tenant-id` dans chaque requête |
| `commons-audit` | `AuditDirective` + `AuditService` | Trace les actions utilisateur sensibles |
| `commons-api` | `ApiErrorHandler` + `ProblemDetailParser` | Parse les erreurs RFC 7807 du backend |
| `commons-kafka` | `SseService` (équivalent consommateur) | Connexion SSE temps réel |
| `commons-outbox` | `OptimisticUpdateService` | Optimistic updates + rollback |
| `commons-security` | `AuthStore` + `HasRoleDirective` + `PlanGateDirective` | RBAC + plan gating |
| `commons-observability` | `PerformanceService` + Web Vitals | Métriques frontend → Grafana |
| *(frontend only)* | `InjectionToken<T>` (config, base URL) | Injection de configuration typée |
| *(frontend only)* | `NgRx Entity Adapter` — patron universel | Collection d'entités sans tableau brut |
| *(frontend only)* | `ShellComponent` — layout principal | Wrapper de toutes les routes protégées |


## II.1 — Common : TenantInterceptor (miroir commons-multitenancy)

```typescript
// core/api/tenant.interceptor.ts
export const tenantInterceptor: HttpInterceptorFn = (req, next) => {
  const store    = inject(Store);
  const tenantId = store.selectSignal(AuthSelectors.tenantId)();


### 📋 Tâches — II.1 TenantInterceptor

- [ ] Créer `TenantInterceptor` (injecte `X-Tenant-ID`) → 0.5J
      📖 Cette section : code complet
- [ ] Enregistrer dans `app.config.ts` → 0.5J
      📖 Cette section : ### Enregistrement
- [ ] Tests unitaires 100% → 0.5J
      📖 Annexe C — Stratégie de Tests

  const tenantReq = tenantId
    ? req.clone({ setHeaders: { 'X-Tenant-ID': tenantId } })
    : req;

  return next(tenantReq);
};
```


## II.1bis — Common : AuditService + AuditDirective (miroir commons-audit)

> **Objectif :** Tracer automatiquement les actions sensibles (décision fraude, révocation clé API, replay DLQ) vers le backend sans polluer le code métier.


### 📋 Tâches — II.1bis AuditService + AuditDirective

- [ ] Créer `AuditService` avec buffer + flush toutes les 10s → 1J
      📖 Cette section : ### AuditService — Envoi en batch
- [ ] Créer `AuditDirective` (`[docaiAudit]`) → 0.5J
      📖 Cette section : ### AuditDirective
- [ ] Ajouter `AuditDirective` sur tous les boutons sensibles (voir liste) → 1J
      📖 Cette section : ### Actions à auditer (liste exhaustive)
- [ ] Tests : buffer, flush, directive → 0.5J
      📖 Cette section : ### Definition of Done II.1bis

### AuditService — Envoi en batch

```typescript
// core/audit/audit.service.ts

export interface AuditEvent {
  action:      string;                        // 'fraud:decision', 'api-key:revoke', 'dlq:replay'
  resourceId?: string;                        // ID de l'entité concernée
  metadata?:   Record<string, unknown>;       // Contexte additionnel
  timestamp:   string;                        // ISO 8601
}

@Injectable({ providedIn: 'root' })
export class AuditService implements OnDestroy {
  private readonly http    = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  // Buffer local — flush toutes les 10s ou à 10 events
  private readonly buffer: AuditEvent[] = [];
  private readonly flushSub = interval(10_000).pipe(
    filter(() => this.buffer.length > 0),
  ).subscribe(() => this.flush());

  log(action: string, resourceId?: string, metadata?: Record<string, unknown>): void {
    this.buffer.push({
      action,
      resourceId,
      metadata,
      timestamp: new Date().toISOString(),
    });
    if (this.buffer.length >= 10) this.flush();
  }

  private flush(): void {
    const events = this.buffer.splice(0);
    this.http.post(`${this.baseUrl}/v1/audit/events`, { events }).subscribe({
      error: (err) => console.warn('[Audit] Flush échoué', err),
    });
  }

  ngOnDestroy(): void {
    if (this.buffer.length > 0) this.flush();   // Flush avant destruction
    this.flushSub.unsubscribe();
  }
}
```

### AuditDirective — Traçage automatique par attribut

```typescript
// core/audit/audit.directive.ts

@Directive({ selector: '[docaiAudit]', standalone: true })
export class AuditDirective {
  private readonly audit = inject(AuditService);

  @Input({ required: true }) docaiAudit!: string;   // Action : 'fraud:decision', 'api-key:revoke'
  @Input() docaiAuditId?: string;                   // ID de la ressource concernée
  @Input() docaiAuditMeta?: Record<string, unknown>; // Metadata optionnelle

  @HostListener('click')
  onClick(): void {
    this.audit.log(this.docaiAudit, this.docaiAuditId, this.docaiAuditMeta);
  }
}
```

### Utilisation dans les templates

```html
<!-- Module 3 — Bouton de décision fraude -->
<button
  docaiAudit="fraud:decision"
  [docaiAuditId]="analysis().id"
  [docaiAuditMeta]="{ decision: 'APPROVED', score: analysis().score }"
  (click)="submitDecision('APPROVED')"
  *docaiHasRole="'FRAUD_REVIEWER'">
  Approuver
</button>

<!-- Module 6 — Révocation clé API -->
<button
  docaiAudit="api-key:revoke"
  [docaiAuditId]="key.id"
  (click)="revokeKey(key.id)"
  *docaiHasRole="'TENANT_ADMIN'">
  Révoquer
</button>

<!-- Module 4 — Replay DLQ -->
<button
  docaiAudit="dlq:replay"
  [docaiAuditId]="message.id"
  [docaiAuditMeta]="{ topic: message.sourceTopic, attempts: message.attemptCount }"
  (click)="replayMessage(message.id)"
  *docaiHasRole="'TENANT_ADMIN'">
  Rejouer
</button>
```

### Actions à auditer (liste exhaustive)

| Action | Module | Déclencheur |
|--------|--------|-------------|
| `fraud:decision` | 3 | APPROVED / REJECTED / ESCALATED |
| `document:upload` | 1 | Upload d'un fichier |
| `api-key:create` | 6 | Création clé API |
| `api-key:revoke` | 6 | Révocation clé API |
| `webhook:delete` | 6 | Suppression webhook |
| `dlq:replay` | 4 | Rejouer un message DLQ |
| `dlq:delete` | 4 | Supprimer un message DLQ |
| `member:revoke` | 0.1 | Révocation d'un membre |
| `read-model:rebuild` | 4 | Reconstruction Read Model (ADR-011) |
| `subscription:cancel` | 7 | Annulation abonnement |

### Definition of Done — II.1bis

- [ ] `AuditService` utilise un buffer avec flush toutes les 10s ET à 10 events
- [ ] `ngOnDestroy` force un flush du buffer restant
- [ ] `AuditDirective` utilisée sur tous les boutons d'actions sensibles (voir liste ci-dessus)
- [ ] `docaiAuditMeta` inclut le contexte utile (score fraude, topic DLQ...)
- [ ] Endpoint `/v1/audit/events` : erreur loggée en console, jamais propagée à l'utilisateur
- [ ] Tests : vérifier que `log()` ajoute au buffer et que flush vide le buffer

---


## II.2 — Common : ProblemDetailParser (miroir commons-api)

```typescript
// core/api/problem-detail.model.ts
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  // Extensions DocAI
  errorCode?: string;
  field?: string;
  retryAfter?: number;
}


### 📋 Tâches — II.2 ProblemDetailParser

- [ ] Créer `ProblemDetailParser` (RFC 7807) → 0.5J
      📖 Cette section : code complet
- [ ] Créer `errorInterceptor` avec switch HTTP codes → 0.5J
      📖 Cette section : ### errorInterceptor
- [ ] Tests unitaires 100% → 0.5J
      📖 Annexe C — Stratégie de Tests

// core/api/error.interceptor.ts
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(Store);
  const snack = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const problem = err.error as ProblemDetail;

      switch (err.status) {
        case 401: store.dispatch(AuthActions.logout()); break;
        case 403: snack.open('Accès refusé — rôle insuffisant', 'Fermer', { duration: 4000 }); break;
        case 429: snack.open(
          `Quota dépassé${problem?.detail ? ' — ' + problem.detail : ''}`,
          'Upgrade',
          { duration: 8000 }
        ); break;
        default:
          if (err.status >= 500)
            snack.open('Erreur serveur — veuillez réessayer', 'Fermer', { duration: 4000 });
      }

      return throwError(() => err);
    })
  );
};
```

## II.3 — Common : SseService (miroir commons-kafka consumer)

> **⚠️ Version précédente remplacée :** `retryWhen` déprécié RxJS 7, `data: unknown` non typé, pas de backoff, pas de partage de connexion multi-feature.


### 📋 Tâches — II.3 SseService

**Phase SSE-A — Types et config (Jour 1)**
- [ ] Définir `SseEvent` union discriminée + 4 payloads typés → 0.5J
      📖 Cette section : ### Typed Payloads
- [ ] Définir `SseConfig` + `SSE_CONFIG` InjectionToken → 0.5J
      📖 Cette section : ### SseConfig — Injection Token

**Phase SSE-B — Implémentation (Jour 2)**
- [ ] Implémenter `openEventSource()` (EventSource + listeners) → 1J
      📖 Cette section : ### SseService — Reconnexion
- [ ] Implémenter `withExponentialBackoff()` → 0.5J
      📖 Cette section : méthode privée backoff

**Phase SSE-C — Helpers et tests (Jour 3)**
- [ ] Implémenter helpers typés (`onDocumentUpdated`, `onFraudAlert`...) → 0.5J
      📖 Cette section : ### Helpers typés
- [ ] Créer `SseStatusComponent` (indicateur topbar) → 0.5J
      📖 Cette section : ### SseStatusComponent
- [ ] Tests : partage connexion, backoff, parsing, cleanup → 1J
      📖 Cette section : ### Tests — SseService

**Critère de passage :** Un seul EventSource par path, reconnexion < 30s.

### Typed Payloads — Contrats stricts avec le backend

```typescript
// core/sse/sse-events.model.ts

export interface DocumentUpdatedPayload {
  documentId: string;
  status:     DocumentStatus;
  pipelineStep: string;
  updatedAt:  string;
}

export interface FraudAlertPayload {
  documentId:  string;
  analysisId:  string;
  score:       number;
  riskLevel:   RiskLevel;
  receivedAt:  string;
}

export interface SummaryUpdatedPayload {
  summary:   DashboardSummary;
  updatedAt: string;
}

export interface QuotaWarningPayload {
  quota:       UsageQuota;
  percentUsed: number;   // Déclenché à 80% et 95%
}

// Union discriminée — typage strict par tag littéral
export type SseEvent =
  | { type: 'DOCUMENT_UPDATED'; data: DocumentUpdatedPayload }
  | { type: 'FRAUD_ALERT';      data: FraudAlertPayload }
  | { type: 'SUMMARY_UPDATED';  data: SummaryUpdatedPayload }
  | { type: 'QUOTA_WARNING';    data: QuotaWarningPayload }
  | { type: 'CONNECTED';        data: null }
  | { type: 'DISCONNECTED';     data: null; reason?: string };
```

### SseConfig — Injection Token

```typescript
// core/sse/sse.config.ts
export interface SseConfig {
  initialDelayMs: number;   // 1000 — délai initial de reconnexion
  maxDelayMs:     number;   // 30000 — max (ADR-011 : 5 min de décalage accepté)
  maxAttempts:    number;   // 10 — puis abandon silencieux
  jitterMs:       number;   // 500 — évite les thundering herds
}

export const DEFAULT_SSE_CONFIG: SseConfig = {
  initialDelayMs: 1000,
  maxDelayMs:     30_000,
  maxAttempts:    10,
  jitterMs:       500,
};

export const SSE_CONFIG = new InjectionToken<SseConfig>('SSE_CONFIG');
```

### SseService — Reconnexion avec backoff exponentiel

```typescript
// core/sse/sse.service.ts
@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly store   = inject(Store);
  private readonly config  = inject(SSE_CONFIG, { optional: true }) ?? DEFAULT_SSE_CONFIG;

  // Un seul EventSource par path — N features partagent la même connexion
  private readonly connections = new Map<string, Observable<SseEvent>>();

  /**
   * Retourne un Observable<SseEvent> partagé pour le path donné.
   * Appels multiples sur le même path = même EventSource (share).
   * La connexion se ferme quand tous les subscribers se désinscrivent.
   */
  connect(path: string): Observable<SseEvent> {
    if (!this.connections.has(path)) {
      const shared$ = this.createConnection(path).pipe(
        share({ connector: () => new ReplaySubject(1), resetOnRefCountZero: true }),
      );
      this.connections.set(path, shared$);
    }
    return this.connections.get(path)!;
  }

  // ─── Helpers typés — utilisés dans les Effects ───────────────────

  onDocumentUpdated(path: string): Observable<DocumentUpdatedPayload> {
    return this.connect(path).pipe(
      filter((e): e is Extract<SseEvent, { type: 'DOCUMENT_UPDATED' }> =>
        e.type === 'DOCUMENT_UPDATED'),
      map((e) => e.data),
    );
  }

  onFraudAlert(path: string): Observable<FraudAlertPayload> {
    return this.connect(path).pipe(
      filter((e): e is Extract<SseEvent, { type: 'FRAUD_ALERT' }> =>
        e.type === 'FRAUD_ALERT'),
      map((e) => e.data),
    );
  }

  onSummaryUpdated(path: string): Observable<SummaryUpdatedPayload> {
    return this.connect(path).pipe(
      filter((e): e is Extract<SseEvent, { type: 'SUMMARY_UPDATED' }> =>
        e.type === 'SUMMARY_UPDATED'),
      map((e) => e.data),
    );
  }

  onQuotaWarning(path: string): Observable<QuotaWarningPayload> {
    return this.connect(path).pipe(
      filter((e): e is Extract<SseEvent, { type: 'QUOTA_WARNING' }> =>
        e.type === 'QUOTA_WARNING'),
      map((e) => e.data),
    );
  }

  // ─── Connexion interne ────────────────────────────────────────────

  private createConnection(path: string): Observable<SseEvent> {
    return this.getToken().pipe(
      switchMap((token) => this.openEventSource(path, token)),
      this.withExponentialBackoff(),
    );
  }

  private openEventSource(path: string, token: string): Observable<SseEvent> {
    return new Observable<SseEvent>((observer) => {
      const url = `${this.baseUrl}${path}?token=${encodeURIComponent(token)}`;
      const es  = new EventSource(url);

      es.onopen = () => observer.next({ type: 'CONNECTED', data: null });

      (['DOCUMENT_UPDATED', 'FRAUD_ALERT', 'SUMMARY_UPDATED', 'QUOTA_WARNING'] as const)
        .forEach((eventType) => {
          es.addEventListener(eventType, (rawEvent) => {
            try {
              const data = JSON.parse((rawEvent as MessageEvent).data);
              observer.next({ type: eventType, data } as SseEvent);
            } catch {
              console.warn(`[SSE] Payload JSON invalide pour ${eventType}`);
            }
          });
        });

      es.onerror = () => {
        observer.next({ type: 'DISCONNECTED', data: null, reason: 'EventSource error' });
        observer.error(new Error('SSE connection lost'));
      };

      return () => {
        es.close();
        this.connections.delete(path);
      };
    });
  }

  private getToken(): Observable<string> {
    const token = this.store.selectSignal(AuthSelectors.accessToken)();
    return token
      ? of(token)
      : throwError(() => new Error('[SSE] Pas de token disponible'));
  }

  /**
   * Backoff exponentiel avec jitter.
   * Tentative 1 : ~1s · 2 : ~2s · 3 : ~4s · ... · max 30s
   * Après maxAttempts : abandon silencieux (complete sans erreur).
   */
  private withExponentialBackoff<T>(): MonoTypeOperatorFunction<T> {
    let attempt = 0;
    const { initialDelayMs, maxDelayMs, maxAttempts, jitterMs } = this.config;

    return (source$: Observable<T>) =>
      source$.pipe(
        retryWhen((errors$) =>
          errors$.pipe(
            mergeMap(() => {
              attempt++;
              if (attempt >= maxAttempts) {
                console.error(`[SSE] Abandon après ${maxAttempts} tentatives`);
                return EMPTY;
              }
              const base   = Math.min(initialDelayMs * 2 ** (attempt - 1), maxDelayMs);
              const jitter = Math.random() * jitterMs;
              const delay  = base + jitter;
              console.warn(`[SSE] Reconnexion dans ${Math.round(delay)}ms (tentative ${attempt}/${maxAttempts})`);
              return timer(delay);
            }),
          )
        ),
        tap(() => { attempt = 0; }),   // Connexion réussie → reset compteur
      );
  }
}
```

### Utilisation dans les Effects — Pattern standard

```typescript
// documents/document.effects.ts
readonly watchDocumentUpdates$ = createEffect(() =>
  this.actions$.pipe(
    ofType(DocumentActions.startSseWatch),
    switchMap(() =>
      this.sse.onDocumentUpdated('/v1/documents/stream').pipe(
        map((payload) => DocumentActions.sseDocumentUpdated({
          documentId: payload.documentId,
          status:     payload.status,
        })),
        takeUntilDestroyed(this.destroyRef),
      )
    ),
  )
);

// fraud/fraud.effects.ts
readonly watchFraudAlerts$ = createEffect(() =>
  this.actions$.pipe(
    ofType(FraudActions.startSseWatch),
    switchMap(() =>
      this.sse.onFraudAlert('/v1/fraud/stream').pipe(
        map((payload) => FraudActions.sseAlertReceived({
          alert: {
            id:         payload.analysisId,
            documentId: payload.documentId,
            score:      payload.score,
            riskLevel:  payload.riskLevel,
            receivedAt: payload.receivedAt,
            read:       false,
          },
        })),
        takeUntilDestroyed(this.destroyRef),
      )
    ),
  )
);
```

### SseStatusComponent — Dumb (affiché dans la Topbar du Shell)

```typescript
// shared/components/sse-status/sse-status.component.ts
@Component({
  selector: 'docai-sse-status',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  template: `
    <div class="flex items-center gap-1.5 text-xs"
         role="status"
         [attr.aria-label]="connected() ? 'Temps réel actif' : 'Reconnexion en cours'">
      <span class="w-2 h-2 rounded-full transition-colors"
            [ngClass]="connected() ? 'bg-green-500 animate-pulse' : 'bg-yellow-400'">
      </span>
      <span class="text-gray-500 hidden sm:inline" aria-hidden="true">
        {{ connected() ? 'Temps réel' : 'Reconnexion...' }}
      </span>
    </div>
  `,
})
export class SseStatusComponent {
  private readonly store = inject(Store);
  protected readonly connected = toSignal(
    this.store.select(DashboardSelectors.sseConnected),
    { initialValue: false },
  );
}
```

### Tests — SseService

```typescript
// core/sse/sse.service.spec.ts
describe('SseService', () => {
  it('partage une seule connexion pour le même path', () => {
    const obs1$ = service.connect('/v1/documents/stream');
    const obs2$ = service.connect('/v1/documents/stream');
    expect(obs1$).toBe(obs2$);  // Même référence Observable
  });

  it('dispatche CONNECTED à l\'ouverture', fakeAsync(() => { /* ... */ }));

  it('retente avec délai exponentiel après erreur EventSource', fakeAsync(() => {
    // Vérifier que le délai double : ~1s → ~2s → ~4s
  }));

  it('parse correctement le payload DOCUMENT_UPDATED', () => { /* ... */ });

  it('ignore les payloads JSON malformés sans propager d\'erreur', () => { /* ... */ });

  it('ferme l\'EventSource quand tous les subscribers se désinscrivent', () => { /* ... */ });

  it('abandon après maxAttempts tentatives', fakeAsync(() => { /* ... */ }));
});
```

### Definition of Done — II.3

- [ ] `SseEvent` est une union discriminée avec payloads typés (jamais `data: unknown`)
- [ ] Backoff exponentiel : délai double à chaque tentative, plafonné à 30s
- [ ] Jitter aléatoire pour éviter les reconnexions synchronisées (thundering herds)
- [ ] Un seul `EventSource` par path — N features partagent la même connexion
- [ ] Token expiré : `getToken()` retourne une erreur loggée proprement
- [ ] `SseStatusComponent` dans la topbar (vert animé = connecté, jaune = reconnexion)
- [ ] `takeUntilDestroyed(this.destroyRef)` dans **tous** les Effects qui consomment le SSE
- [ ] Couverture Jest ≥ 95% (partage connexion, backoff, parsing, cleanup)


## II.4 — Common : OptimisticUpdateService (miroir commons-outbox)

```typescript
// core/store/optimistic-update.service.ts
@Injectable({ providedIn: 'root' })
export class OptimisticUpdateService {
  // Pattern rollback : snapshot → apply → success|rollback
  applyWithRollback<T>(
    snapshot: T,
    applyFn: () => void,
    apiCall$: Observable<T>,
    rollbackFn: (snapshot: T) => void
  ): Observable<T> {
    applyFn(); // Application immédiate (optimistic)
    return apiCall$.pipe(
      catchError((err) => {
        rollbackFn(snapshot); // Rollback si erreur
        return throwError(() => err);
      })
    );
  }
}
```


### 📋 Tâches — II.4 OptimisticUpdateService

- [ ] Implémenter `OptimisticUpdateService` (snapshot + rollback) → 1J
      📖 Cette section : code complet
- [ ] Tester le rollback sur erreur HTTP → 0.5J
      📖 Annexe C — Stratégie de Tests

## II.5 — Common : HasRoleDirective + PlanGateDirective (miroir commons-security)

```typescript
// shared/directives/has-role.directive.ts
@Directive({ selector: '[docaiHasRole]', standalone: true })
export class HasRoleDirective {
  private readonly store = inject(Store);
  private readonly vcr   = inject(ViewContainerRef);
  private readonly tmpl  = inject(TemplateRef<unknown>);
  private rendered = false;


### 📋 Tâches — II.5 HasRole + PlanGate Directives

- [ ] Créer `HasRoleDirective` (`*docaiHasRole`) → 0.5J
      📖 Cette section : code complet
- [ ] Créer `PlanGateDirective` (`*docaiPlanGate`) → 0.5J
      📖 Cette section : code complet
- [ ] Définir hiérarchie plans (FREE < STARTER < PRO < ENTERPRISE) → 0.5J
      📖 Cette section : logique de comparaison
- [ ] Tests unitaires 100% → 0.5J
      📖 Annexe C — Stratégie de Tests

  @Input() set docaiHasRole(role: UserRole) {
    this.store.select(AuthSelectors.hasRole(role)).subscribe((has) => {
      if (has && !this.rendered) {
        this.vcr.createEmbeddedView(this.tmpl);
        this.rendered = true;
      } else if (!has && this.rendered) {
        this.vcr.clear();
        this.rendered = false;
      }
    });
  }
}

// shared/directives/plan-gate.directive.ts
// Masque les features disponibles uniquement sur les plans supérieurs
@Directive({ selector: '[docaiPlanGate]', standalone: true })
export class PlanGateDirective {
  private readonly store = inject(Store);
  private readonly vcr   = inject(ViewContainerRef);
  private readonly tmpl  = inject(TemplateRef<unknown>);
  private rendered = false;

  private readonly planHierarchy: Record<string, number> = {
    FREE: 0, STARTER: 1, PRO: 2, ENTERPRISE: 3,
  };

  @Input() set docaiPlanGate(requiredPlan: string) {
    this.store.select(BillingSelectors.currentPlan).subscribe((plan) => {
      const hasAccess =
        (this.planHierarchy[plan ?? 'FREE'] ?? 0) >=
        (this.planHierarchy[requiredPlan] ?? 99);

      if (hasAccess && !this.rendered) {
        this.vcr.createEmbeddedView(this.tmpl);
        this.rendered = true;
      } else if (!hasAccess && this.rendered) {
        this.vcr.clear();
        this.rendered = false;
      }
    });
  }
}
```

## II.6 — Common : Injection Tokens

```typescript
// core/tokens.ts
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(ENVIRONMENT).apiBaseUrl,
});


### 📋 Tâches — II.6 Injection Tokens

- [ ] Définir `API_BASE_URL`, `KEYCLOAK_CONFIG`, `STRIPE_PUBLIC_KEY` → 0.5J
      📖 Cette section : code complet
- [ ] Configurer dans `app.config.ts` par environment → 0.5J
      📖 Annexe E — Environment Configuration

export const KEYCLOAK_CONFIG = new InjectionToken<KeycloakConfig>('KEYCLOAK_CONFIG', {
  providedIn: 'root',
  factory: () => inject(ENVIRONMENT).keycloak,
});

export const STRIPE_PUBLIC_KEY = new InjectionToken<string>('STRIPE_PUBLIC_KEY', {
  providedIn: 'root',
  factory: () => inject(ENVIRONMENT).stripePublicKey,
});

export const ENVIRONMENT = new InjectionToken<AppEnvironment>('ENVIRONMENT');
```


## II.6bis — Common : PerformanceService & Web Vitals (miroir commons-observability)

> **Objectif :** Envoyer les Core Web Vitals au backend DocAI pour monitoring Grafana — identique au monitoring JVM côté backend.


### 📋 Tâches — II.6bis PerformanceService

- [ ] Installer `web-vitals` → 0.5J
      📖 Cette section : ### Installation
- [ ] Implémenter `PerformanceService.init()` (5 métriques) → 0.5J
      📖 Cette section : ### PerformanceService
- [ ] Enregistrer via `APP_INITIALIZER` dans `app.config.ts` → 0.5J
      📖 Cette section : ### Intégration dans app.config.ts
- [ ] Conditionner à `environment.production` → 0.5J
      📖 Annexe E — Environment Configuration

### Installation

```bash
npm install web-vitals
```

### PerformanceService

```typescript
// core/observability/performance.service.ts
import { onCLS, onFID, onLCP, onTTFB, onFCP, type Metric } from 'web-vitals';

export interface WebVitalPayload {
  name:    'CLS' | 'FID' | 'LCP' | 'TTFB' | 'FCP';
  value:   number;
  rating:  'good' | 'needs-improvement' | 'poor';
  page:    string;    // URL de la page au moment de la mesure
  tenantId?: string;
}

@Injectable({ providedIn: 'root' })
export class PerformanceService {
  private readonly http     = inject(HttpClient);
  private readonly baseUrl  = inject(API_BASE_URL);
  private readonly store    = inject(Store);
  private readonly router   = inject(Router);

  /**
   * À appeler UNE SEULE FOIS dans app.config.ts via APP_INITIALIZER.
   * Les Web Vitals sont mesurés automatiquement par le browser.
   */
  init(): void {
    const send = (metric: Metric) => {
      const payload: WebVitalPayload = {
        name:     metric.name as WebVitalPayload['name'],
        value:    metric.value,
        rating:   metric.rating as WebVitalPayload['rating'],
        page:     this.router.url,
        tenantId: this.store.selectSignal(AuthSelectors.tenantId)() ?? undefined,
      };
      // Fire & forget — ne jamais bloquer l'UI sur un échec de metric
      this.http.post(`${this.baseUrl}/v1/metrics/web-vitals`, payload).subscribe({
        error: () => {},  // Silencieux — pas d'impact utilisateur
      });
    };

    onCLS(send);
    onFID(send);
    onLCP(send);
    onTTFB(send);
    onFCP(send);
  }
}
```

### Intégration dans app.config.ts

```typescript
// app.config.ts — ajouter dans providers[] :
{
  provide:    APP_INITIALIZER,
  useFactory: (perf: PerformanceService) => () => perf.init(),
  deps:       [PerformanceService],
  multi:      true,
}
```

### Seuils Web Vitals cibles (Google Core Web Vitals 2024)

| Métrique | Good | Needs Improvement | Poor | Priorité |
|----------|------|-------------------|------|---------|
| LCP (Largest Contentful Paint) | < 2.5s | 2.5–4s | > 4s | Critique |
| FID (First Input Delay) | < 100ms | 100–300ms | > 300ms | Haute |
| CLS (Cumulative Layout Shift) | < 0.1 | 0.1–0.25 | > 0.25 | Haute |
| TTFB (Time to First Byte) | < 800ms | 800ms–1.8s | > 1.8s | Moyenne |
| FCP (First Contentful Paint) | < 1.8s | 1.8–3s | > 3s | Moyenne |

### Definition of Done — II.6bis

- [ ] `web-vitals` installé et importé
- [ ] `PerformanceService.init()` appelé via `APP_INITIALIZER` dans `app.config.ts`
- [ ] Les 5 métriques sont envoyées (CLS, FID, LCP, TTFB, FCP)
- [ ] Payload inclut `page` et `tenantId` pour filtrage Grafana par tenant
- [ ] Erreur HTTP silencieuse — ne jamais propager à l'utilisateur
- [ ] En production uniquement : conditionner l'init à `environment.production`

---


## II.7 — NgRx Entity Adapter — Patron Universel

**Règle absolue :** Toute collection d'entités utilise `createEntityAdapter`. Jamais de tableau `T[]` brut dans le state.


### 📋 Tâches — II.7 NgRx Entity Adapter

- [ ] **Lire** le patron universel (shape de state) → 0.5J
      📖 Cette section : code complet
- [ ] Appliquer ce patron à chaque feature lors de son développement → ongoing
      📖 Module 1 / Module 2 / Module 3 / Module 4 — sections NgRx

```typescript
// Patron d'état complet (appliqué à TOUTES les features avec collection)

export const entityAdapter = createEntityAdapter<T>({
  selectId: (item) => item.id,
  sortComparer: (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
});

export interface FeatureState extends EntityState<T> {
  loadingIds: string[];
  listLoading: boolean;
  detailLoading: boolean;
  listError: string | null;
  detailError: string | null;
  currentPage: number;
  pageSize: number;
  totalElements: number;
  selectedId: string | null;
}

export const initialFeatureState: FeatureState =
  entityAdapter.getInitialState({
    loadingIds: [], listLoading: false, detailLoading: false,
    listError: null, detailError: null,
    currentPage: 0, pageSize: 20, totalElements: 0,
    selectedId: null,
  });
```

---



## II.8 — Shell Component (Layout principal de l'application)

> **Durée : incluse dans Partie 2 (Commons)**
> **Prérequis : Tokens CSS (I.7) + AuthStore (état uniquement, pas les pages)**
> **Critère de passage : Naviguer entre dashboard, documents, fraude → layout stable. Bannières s'affichent et se retirent selon la priorité.**


### 📋 Tâches — II.8 Shell Component

**Phase Shell-A — Structure (Jour 1)**
- [ ] Créer `ShellComponent` Smart avec toSignal() → 1J
      📖 Cette section : ### ShellComponent — Smart
- [ ] Créer `shell.component.html` (layout flex, skip link) → 0.5J
      📖 Cette section : ### shell.component.html

**Phase Shell-B — Sous-composants (Jour 2)**
- [ ] Créer `ShellSidebarComponent` Dumb (nav + collapse) → 0.5J
      📖 Cette section : ### ShellSidebarComponent
- [ ] Créer `buildNavItems()` filtré par rôle → 0.5J
      📖 Cette section : ### Navigation — Items par rôle
- [ ] Créer `ShellBannerComponent` Dumb (4 types) → 0.5J
      📖 Cette section : ### ShellBanner — Model & Composant

**Phase Shell-C — Logique bannière + tests (Jour 3)**
- [ ] Implémenter `activeBanner` computed (priorité PAST_DUE > quota > trial) → 0.5J
      📖 Cette section : computed activeBanner
- [ ] Sauvegarder collapse sidebar dans localStorage → 0.5J
      📖 Cette section : ### Definition of Done II.8
- [ ] Tests Smart + Tests Dumb → 1J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** Navigation stable, bannière prioritisée, skip link accessible.

### Pourquoi c'est un Common, pas une Feature

Le `ShellComponent` est le wrapper de **toutes** les routes protégées (défini dans app.routes.ts dès Partie 1). Il doit être développé en Partie 2 pour que chaque feature puisse s'y intégrer sans refactoring. Développé trop tard, il force une réécriture du layout de chaque feature.

### Structure des fichiers

```
shared/components/shell/
├── shell.component.ts
├── shell.component.html
├── shell-topbar/
│   ├── shell-topbar.component.ts      ← Dumb
│   └── shell-topbar.component.html
├── shell-sidebar/
│   ├── shell-sidebar.component.ts     ← Dumb
│   ├── shell-sidebar.component.html
│   └── nav-items.builder.ts           ← Fonction pure (testable)
└── shell-banner/
    ├── shell-banner.component.ts      ← Dumb
    ├── shell-banner.component.html
    └── shell-banner.model.ts
```

### ShellComponent — Smart (Container)

```typescript
// shared/components/shell/shell.component.ts
@Component({
  selector: 'docai-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ShellTopbarComponent, ShellSidebarComponent, ShellBannerComponent],
  templateUrl: './shell.component.html',
})
export class ShellComponent implements OnInit {
  private readonly store = inject(Store);

  // ─── State depuis le Store ────────────────────────────────────────
  protected readonly user         = toSignal(this.store.select(AuthSelectors.user));
  protected readonly sseConnected = toSignal(this.store.select(DashboardSelectors.sseConnected), { initialValue: false });
  protected readonly unreadAlerts = toSignal(this.store.select(FraudSelectors.unreadCount),      { initialValue: 0 });
  protected readonly quotaPercent = toSignal(this.store.select(BillingSelectors.quotaPercent),   { initialValue: 0 });
  protected readonly subscription = toSignal(this.store.select(BillingSelectors.subscription));

  // ─── State local ──────────────────────────────────────────────────
  protected readonly sidebarCollapsed = signal<boolean>(
    localStorage.getItem('docai:sidebar:collapsed') === 'true'
  );

  // ─── Navigation filtrée par rôle ─────────────────────────────────
  protected readonly navItems = computed(() => {
    const user = this.user();
    return user ? buildNavItems(user.roles) : [];
  });

  // ─── Bannière active (priorité : PAST_DUE > quota critique > quota warning > trial) ──
  protected readonly activeBanner = computed((): ShellBanner | null => {
    const sub   = this.subscription();
    const quota = this.quotaPercent();

    if (sub?.status === 'PAST_DUE' || sub?.status === 'UNPAID') {
      return {
        type: 'past-due',
        message: 'Paiement en échec — régularisez votre abonnement pour continuer',
        cta: 'Mettre à jour',
        ctaRoute: null,
        ctaExternal: true,   // Ouvre Stripe Portal
        dismissible: false,
      };
    }
    if (quota >= 95) {
      return {
        type: 'quota-critical',
        message: `Quota à ${quota}% — uploads bloqués`,
        cta: 'Upgrader maintenant',
        ctaRoute: '/billing/plans',
        ctaExternal: false,
        dismissible: false,
      };
    }
    if (quota >= 80) {
      return {
        type: 'quota-warning',
        message: `Quota à ${quota}% — pensez à upgrader votre plan`,
        cta: 'Voir les plans',
        ctaRoute: '/billing/plans',
        ctaExternal: false,
        dismissible: true,
      };
    }
    if (sub?.status === 'TRIAL' && sub.currentPeriodEnd) {
      const daysLeft = Math.ceil(
        (new Date(sub.currentPeriodEnd).getTime() - Date.now()) / 86_400_000
      );
      if (daysLeft <= 7) {
        return {
          type: 'trial',
          message: `Période d'essai : ${daysLeft} jour(s) restant(s)`,
          cta: 'Souscrire',
          ctaRoute: '/billing/plans',
          ctaExternal: false,
          dismissible: true,
        };
      }
    }
    return null;
  });

  ngOnInit(): void {
    this.store.dispatch(BillingActions.loadSubscription());
    this.store.dispatch(DashboardActions.loadSummary());
  }

  protected toggleSidebar(): void {
    this.sidebarCollapsed.update((v) => {
      const next = !v;
      localStorage.setItem('docai:sidebar:collapsed', String(next));
      return next;
    });
  }

  protected onLogout(): void {
    this.store.dispatch(AuthActions.logout());
  }

  protected onPortalOpen(): void {
    this.store.dispatch(BillingActions.openCustomerPortal());
  }
}
```

### shell.component.html

```html
<!-- shared/components/shell/shell.component.html -->

<!-- Skip link accessibilité — invisible sauf focus clavier -->
<a href="#main-content"
   class="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2
          focus:z-tooltip focus:px-4 focus:py-2 focus:bg-white focus:rounded-md
          focus:shadow-md focus:text-primary-600 focus:font-medium">
  Aller au contenu principal
</a>

<div class="flex h-screen overflow-hidden bg-gray-50">

  <!-- Sidebar -->
  <docai-shell-sidebar
    [navItems]="navItems()"
    [collapsed]="sidebarCollapsed()"
    (collapsedChange)="toggleSidebar()" />

  <!-- Zone droite : topbar + bannière + contenu -->
  <div class="flex flex-col flex-1 min-w-0 overflow-hidden">

    <!-- Topbar -->
    <docai-shell-topbar
      [user]="user()"
      [sseConnected]="sseConnected()"
      [unreadAlerts]="unreadAlerts()"
      (logout)="onLogout()"
      (toggleSidebar)="toggleSidebar()" />

    <!-- Bannière globale — une seule à la fois, priorité calculée dans le Smart -->
    @if (activeBanner()) {
      <docai-shell-banner
        [banner]="activeBanner()!"
        (ctaClick)="onPortalOpen()"
        (dismiss)="activeBanner.set(null)" />
    }

    <!-- Contenu scrollable -->
    <main id="main-content"
          tabindex="-1"
          aria-label="Contenu principal"
          class="flex-1 overflow-auto p-6">
      <router-outlet />
    </main>

  </div>
</div>
```

### ShellBanner — Model & Composant Dumb

```typescript
// shared/components/shell/shell-banner/shell-banner.model.ts
export interface ShellBanner {
  type:        'past-due' | 'quota-critical' | 'quota-warning' | 'trial';
  message:     string;
  cta:         string;
  ctaRoute:    string | null;   // null = action externe (Stripe Portal via Store)
  ctaExternal: boolean;
  dismissible: boolean;
}

// shell-banner.component.ts
@Component({
  selector: 'docai-shell-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NgClass],
  template: `
    <div class="flex items-center justify-between px-4 h-banner text-sm font-medium border-b"
         role="alert"
         [attr.aria-live]="banner().type === 'past-due' ? 'assertive' : 'polite'"
         [ngClass]="{
           'bg-red-50 text-red-800 border-red-200':    isBlocker(),
           'bg-yellow-50 text-yellow-800 border-yellow-200': banner().type === 'quota-warning',
           'bg-blue-50 text-blue-800 border-blue-200': banner().type === 'trial'
         }">

      <span>{{ banner().message }}</span>

      <div class="flex items-center gap-3 flex-shrink-0 ml-4">
        <!-- CTA externe (Stripe Portal) -->
        @if (banner().ctaExternal) {
          <button (click)="ctaClick.emit()"
                  class="underline hover:no-underline font-semibold">
            {{ banner().cta }}
          </button>
        }
        <!-- CTA interne (routerLink) -->
        @else if (banner().ctaRoute) {
          <a [routerLink]="banner().ctaRoute"
             class="underline hover:no-underline font-semibold">
            {{ banner().cta }}
          </a>
        }

        <!-- Fermer — uniquement si dismissible -->
        @if (banner().dismissible) {
          <button (click)="dismiss.emit()"
                  aria-label="Fermer la bannière"
                  class="opacity-60 hover:opacity-100 transition-opacity p-1 rounded">
            ✕
          </button>
        }
      </div>
    </div>
  `,
})
export class ShellBannerComponent {
  @Input({ required: true }) banner!: ShellBanner;
  @Output() ctaClick = new EventEmitter<void>();
  @Output() dismiss  = new EventEmitter<void>();

  protected readonly isBlocker = computed(() =>
    this.banner?.type === 'past-due' || this.banner?.type === 'quota-critical'
  );
}
```

### Navigation — Items par rôle (fonction pure)

```typescript
// shared/components/shell/shell-sidebar/nav-items.builder.ts

export interface NavItem {
  label:  string;
  route:  string;
  icon:   string;      // Nom icône Angular Material
  roles:  UserRole[];  // Vide = visible par tous les rôles authentifiés
  exact?: boolean;     // true = routerLinkActive exact
}

const ALL_NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard',      route: '/dashboard',  icon: 'dashboard',    roles: [],                                    exact: true },
  { label: 'Documents',      route: '/documents',  icon: 'description',  roles: [] },
  { label: 'Fraude',         route: '/fraud',      icon: 'security',     roles: ['FRAUD_REVIEWER', 'TENANT_ADMIN'] },
  { label: 'Pipeline',       route: '/pipeline',   icon: 'account_tree', roles: ['TENANT_ADMIN'] },
  { label: 'API & Webhooks', route: '/settings',   icon: 'vpn_key',      roles: ['TENANT_ADMIN'] },
  { label: 'Billing',        route: '/billing',    icon: 'credit_card',  roles: ['TENANT_ADMIN'] },
  { label: 'RGPD',           route: '/rgpd',       icon: 'shield',       roles: ['TENANT_ADMIN'] },
];

/** Fonction pure — testable sans Angular */
export function buildNavItems(roles: UserRole[]): NavItem[] {
  return ALL_NAV_ITEMS.filter(
    (item) => item.roles.length === 0 || item.roles.some((r) => roles.includes(r))
  );
}
```

### ShellSidebarComponent — Dumb

```typescript
// shared/components/shell/shell-sidebar/shell-sidebar.component.ts
@Component({
  selector: 'docai-shell-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, NgClass, MatIconModule],
  template: `
    <nav class="flex flex-col h-full bg-white border-r border-gray-200
                transition-all duration-200 flex-shrink-0"
         [style.width]="collapsed() ? 'var(--docai-sidebar-collapsed)' : 'var(--docai-sidebar-width)'"
         [attr.aria-label]="'Navigation principale'"
         [attr.aria-expanded]="!collapsed()">

      <!-- Logo / Marque -->
      <div class="flex items-center gap-3 px-4 border-b border-gray-200 flex-shrink-0"
           [style.height]="'var(--docai-topbar-height)'">
        <div class="w-8 h-8 rounded-lg bg-primary-600 flex-shrink-0"></div>
        @if (!collapsed()) {
          <span class="font-bold text-gray-900 text-lg">DocAI</span>
        }
      </div>

      <!-- Liens de navigation -->
      <ul class="flex-1 overflow-y-auto py-4 space-y-1 px-2" role="list">
        @for (item of navItems(); track item.route) {
          <li>
            <a [routerLink]="item.route"
               routerLinkActive="bg-primary-50 text-primary-700 font-medium"
               [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
               class="flex items-center gap-3 px-3 py-2 rounded-md text-gray-700
                      hover:bg-gray-100 hover:text-gray-900 transition-colors text-sm"
               [attr.title]="collapsed() ? item.label : null"
               [attr.aria-label]="item.label">
              <mat-icon class="flex-shrink-0 text-current !text-[20px]" aria-hidden="true">
                {{ item.icon }}
              </mat-icon>
              @if (!collapsed()) {
                <span class="truncate">{{ item.label }}</span>
              }
            </a>
          </li>
        }
      </ul>

      <!-- Bouton collapse -->
      <div class="flex-shrink-0 p-2 border-t border-gray-200">
        <button (click)="collapsedChange.emit()"
                class="w-full flex items-center justify-center p-2 rounded-md
                       text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
                [attr.aria-label]="collapsed() ? 'Étendre la barre latérale' : 'Réduire la barre latérale'">
          <mat-icon aria-hidden="true">
            {{ collapsed() ? 'chevron_right' : 'chevron_left' }}
          </mat-icon>
        </button>
      </div>
    </nav>
  `,
})
export class ShellSidebarComponent {
  @Input({ required: true }) navItems!: NavItem[];
  @Input({ required: true }) collapsed!: boolean;
  @Output() collapsedChange = new EventEmitter<void>();
}
```

### Definition of Done — II.8

- [ ] `ShellComponent` Smart dispatch `BillingActions.loadSubscription()` au `ngOnInit`
- [ ] Navigation filtrée par rôle via `buildNavItems(user.roles)` (fonction pure testée)
- [ ] Bannière calculée par priorité : `PAST_DUE > quota-critical > quota-warning > trial`
- [ ] Bannières `past-due` et `quota-critical` : `dismissible: false` — ne peuvent pas être fermées
- [ ] Collapse sidebar sauvegardé dans `localStorage` clé `docai:sidebar:collapsed`
- [ ] `routerLinkActive` exact sur `/dashboard`, préfixe sur les autres
- [ ] Skip link `<a href="#main-content">` en tête de template (Lighthouse a11y = 100)
- [ ] `id="main-content"` + `tabindex="-1"` sur `<main>` pour navigation clavier
- [ ] `SseStatusComponent` affiché dans la topbar
- [ ] Tests Smart (ShellComponent) : chaque type de bannière + filtre nav par rôle
- [ ] Tests Dumb (Sidebar, Banner) : @testing-library/angular — pas de Store réel

---


# PARTIE 3 — FONDATIONS SAAS

> **Durée : 4 semaines**
> **Prérequis : Partie 2 terminée (Commons 100% testés)**
> **Critère de passage : Un utilisateur peut s'inscrire, se connecter, voir son plan et configurer le RGPD**
> **Miroir exact de la Partie 3 backend**

---

## Module 0.1 — Inscription & Équipe

> **Parallèle Backend :** Module 0.1 — Inscription & Gestion d'Équipe  
> **Démarrer quand :** Endpoints `/v1/public/signup`, `/v1/team/invite` disponibles + SES configuré  
> **Durée estimée :** 1 semaine


### 📋 Découpage en phases — Module 0.1 (1 semaine)

**Phase 0.1-A — State + Services (Jour 1-2)**
- [ ] Créer `SignupState` + `SignupActions` + Reducers → 1J
      📖 Cette section : ### State Signup
- [ ] Créer `SignupService` (appels HTTP signup, verify, invite) → 0.5J
      📖 Cette section : ### Endpoints consommés
- [ ] Créer `SignupEffects` → 0.5J
      📖 Cette section : ### Effects

**Phase 0.1-B — Composants (Jour 3-4)**
- [ ] Créer `SignupFormComponent` Dumb (email, mot de passe, org) → 0.5J
      📖 Cette section : ### Pages & Composants
- [ ] Créer `PasswordStrengthMeterComponent` Dumb → 0.5J
      📖 Cette section : composants liste
- [ ] Créer `InviteMemberFormComponent` Dumb → 0.5J
      📖 Cette section : composants liste
- [ ] Créer `TeamMembersListComponent` Dumb → 0.5J
      📖 Cette section : composants liste

**Phase 0.1-C — Pages + Tests (Jour 5)**
- [ ] Créer pages Smart (signup, verify, invite, accept) → 0.5J
      📖 Cette section : ### Pages & Composants
- [ ] Tests reducer + effects → 0.5J
      📖 Annexe C — Stratégie de Tests
- [ ] Vérifier DoD → 0.5J
      📖 Cette section : ### Definition of Done — Module 0.1

### Objectif

Permettre à un nouveau client de créer son compte et son organisation, inviter ses collaborateurs et activer son espace DocAI. Correspond au premier contact de l'utilisateur avec le produit.

### Pages & Composants

```
signup/
├── pages/
│   ├── signup-page/                   ← Container : formulaire inscription
│   ├── email-verification-page/       ← Container : écran "vérifiez votre email"
│   ├── account-activation-page/       ← Container : activation via lien email
│   └── invitation-accept-page/        ← Container : accepter une invitation collègue
└── components/
    ├── signup-form/                   ← Dumb : nom, email, mot de passe, nom org
    ├── password-strength-meter/       ← Dumb : barre de force du mot de passe
    ├── email-sent-card/               ← Dumb : confirmation envoi email SES
    ├── team-members-list/             ← Dumb : liste membres + rôles
    ├── invite-member-form/            ← Dumb : email + rôle à inviter
    └── member-role-badge/             ← Dumb : badge ADMIN/ANALYST/VIEWER/REVIEWER
```

### State Signup

```typescript
// signup.actions.ts
export const SignupActions = createActionGroup({
  source: 'Signup',
  events: {
    'Submit Signup':              props<{ email: string; password: string; orgName: string; firstName: string; lastName: string }>(),
    'Submit Signup Success':      props<{ message: string }>(),
    'Submit Signup Failure':      props<{ error: string }>(),
    'Verify Email':               props<{ token: string }>(),
    'Verify Email Success':       emptyProps(),
    'Verify Email Failure':       props<{ error: string }>(),
    'Invite Member':              props<{ email: string; role: UserRole }>(),
    'Invite Member Success':      props<{ email: string }>(),
    'Invite Member Failure':      props<{ error: string }>(),
    'Accept Invitation':          props<{ token: string; password: string }>(),
    'Accept Invitation Success':  emptyProps(),
    'Accept Invitation Failure':  props<{ error: string }>(),
    'Revoke Member':              props<{ userId: string }>(),
    'Revoke Member Success':      props<{ userId: string }>(),
  },
});
```

### Endpoints consommés

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/v1/public/signup` | Créer un compte (sans auth) |
| POST | `/v1/public/verify-email` | Valider le token email |
| POST | `/v1/public/invitation/accept` | Accepter une invitation |
| GET | `/v1/team/members` | Lister les membres de l'équipe |
| POST | `/v1/team/invite` | Inviter un collaborateur |
| DELETE | `/v1/team/members/{id}` | Révoquer un accès |

### Emails déclenchés (côté backend — à afficher côté frontend)

| Email backend | Page frontend d'atterrissage |
|---------------|------------------------------|
| `welcome.html` | `/dashboard` après activation |
| `email-verification.html` | `/auth/verify?token=xxx` |
| `invitation.html` | `/auth/invitation/accept?token=xxx` |
| `account-revoked.html` | `/auth/login` (session invalidée) |

### Definition of Done — Module 0.1

- [ ] Formulaire signup : validation email, force MDP, nom organisation
- [ ] Page "Email envoyé" affichée après signup
- [ ] Activation via lien email fonctionnelle (`/auth/verify?token=xxx`)
- [ ] Page d'invitation : nouveau mot de passe + activation compte
- [ ] Liste membres avec rôles — protégée `*docaiHasRole="'TENANT_ADMIN'"`
- [ ] Invitation envoyée avec succès (feedback visuel)
- [ ] Révocation membre : `ConfirmDialog` obligatoire
- [ ] Tests reducer : chaque action signup testée

---

## Module 0.2 — Login / Logout / 2FA

> **Parallèle Backend :** Module 0.2 — Login / Logout / 2FA  
> **Démarrer quand :** Keycloak realm `docai` configuré avec les 5 rôles  
> **Durée estimée :** 1 semaine  
> **C'est LA fondation de tout — à implémenter avant tous les autres modules**


### 📋 Découpage en phases — Module 0.2 (1 semaine)

**Phase 0.2-A — Auth Keycloak (Jour 1-2)**
- [ ] Configurer Keycloak PKCE dans `app.config.ts` → 1J
      📖 Cette section : ### Configuration Keycloak
- [ ] Créer `authGuard` (bloque routes protégées) → 0.5J
      📖 Cette section : ### authGuard
- [ ] Créer `AuthStore` (user, roles, token, tenantId) → 0.5J
      📖 Cette section : ### NgRx — Auth State

**Phase 0.2-B — 2FA (Jour 3-4)**
- [ ] Page TOTP (QR code + codes de secours) → 1J
      📖 Cette section : ### 2FA — TOTP
- [ ] Page saisie code TOTP au login → 0.5J
      📖 Cette section : composants 2FA
- [ ] Page reset mot de passe → 0.5J
      📖 Cette section : ### Reset Password

**Phase 0.2-C — Tests (Jour 5)**
- [ ] Tests AuthStore + authGuard → 0.5J
      📖 Annexe C — Stratégie de Tests
- [ ] Vérifier DoD → 0.5J
      📖 Annexe D — Données de Test (`admin@acme-corp.test / Test1234!`)

### State Auth

```typescript
// auth.model.ts
export interface UserProfile {
  sub: string;
  email: string;
  tenantId: string;
  roles: UserRole[];
  firstName?: string;
  lastName?: string;
  twoFactorEnabled: boolean;
}

export type UserRole = 'TENANT_ADMIN' | 'ANALYST' | 'VIEWER' | 'FRAUD_REVIEWER';

// auth.state.ts
export interface AuthState {
  user: UserProfile | null;
  authenticated: boolean;
  loading: boolean;
  error: string | null;
  twoFactorRequired: boolean;
  accessToken: string | null;
}

export const initialAuthState: AuthState = {
  user: null, authenticated: false, loading: false,
  error: null, twoFactorRequired: false, accessToken: null,
};

// auth.actions.ts
export const AuthActions = createActionGroup({
  source: 'Auth',
  events: {
    'Init':                    emptyProps(),
    'Init Success':            props<{ user: UserProfile; token: string }>(),
    'Init Failure':            props<{ error: string }>(),
    'Login':                   emptyProps(),
    'Logout':                  emptyProps(),
    'Token Refreshed':         props<{ token: string }>(),
    'Two Factor Required':     emptyProps(),
    'Two Factor Verified':     props<{ user: UserProfile }>(),
    'Two Factor Enable':       emptyProps(),
    'Two Factor Enable Success': props<{ qrCodeUrl: string; backupCodes: string[] }>(),
    'Two Factor Disable':      emptyProps(),
    'Password Reset Request':  props<{ email: string }>(),
    'Password Reset Confirm':  props<{ token: string; newPassword: string }>(),
    'Password Change':         props<{ currentPassword: string; newPassword: string }>(),
  },
});

// auth.selectors.ts
const selectAuthState = createFeatureSelector<AuthState>('auth');

export const AuthSelectors = {
  user:           createSelector(selectAuthState, (s) => s.user),
  authenticated:  createSelector(selectAuthState, (s) => s.authenticated),
  loading:        createSelector(selectAuthState, (s) => s.loading),
  tenantId:       createSelector(selectAuthState, (s) => s.user?.tenantId ?? null),
  roles:          createSelector(selectAuthState, (s) => s.user?.roles ?? []),
  accessToken:    createSelector(selectAuthState, (s) => s.accessToken),
  twoFactorRequired: createSelector(selectAuthState, (s) => s.twoFactorRequired),
  hasRole: (role: UserRole) =>
    createSelector(selectAuthState, (s) => s.user?.roles.includes(role) ?? false),
  isAdmin: createSelector(selectAuthState, (s) =>
    s.user?.roles.includes('TENANT_ADMIN') ?? false),
};
```

### Auth Interceptor + Error Interceptor

```typescript
// auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(KeycloakService);
  return from(keycloak.getToken()).pipe(
    switchMap((token) => {
      const authReq = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(authReq);
    })
  );
};
```

### Pages Auth

```
auth/
├── pages/
│   ├── login-page/                    ← Container (redirect Keycloak PKCE)
│   ├── two-factor-page/               ← Container : saisie code TOTP
│   ├── password-reset-page/           ← Container : demander reset
│   ├── password-reset-confirm-page/   ← Container : nouveau MDP via token
│   └── profile-page/                  ← Container : profil + changement MDP + 2FA
└── components/
    ├── two-factor-setup/              ← Dumb : QR code + codes de secours
    ├── backup-codes-display/          ← Dumb : 10 codes de secours (copie unique)
    └── session-expired-dialog/        ← Dumb : dialog expiration session
```

### ADR-006 — Impact Frontend (Cache JWKS)

> Le backend cache les clés JWKS Keycloak 1 heure. Côté frontend :
> - Le refresh token est géré automatiquement par `keycloak-angular`
> - En cas d'erreur 401 persistante → dispatch `AuthActions.logout()` (l'intercepteur gère)
> - Pas de gestion manuelle du JWKS côté frontend — Keycloak-Angular s'en charge

### Definition of Done — Module 0.2

- [ ] Keycloak PKCE configuré — realm `docai`, client `docai-frontend`
- [ ] `authGuard` bloque toutes les routes protégées
- [ ] `authInterceptor` injecte le JWT dans chaque requête HTTP
- [ ] `tenantInterceptor` injecte `X-Tenant-ID` dans chaque requête
- [ ] `errorInterceptor` gère 401/403/429/5xx avec messages RFC 7807
- [ ] `*docaiHasRole` et `*docaiPlanGate` fonctionnels
- [ ] Page 2FA : saisie code TOTP + codes de secours affichés UNE SEULE FOIS
- [ ] Page profil : changement MDP + activation/désactivation 2FA
- [ ] Reducers et selectors couverts à 100%
- [ ] Données de test : `admin@acme-corp.test / Test1234!` fonctionne

---

## Module 0.3 — RGPD & Privacy

> **Parallèle Backend :** Module 0.3 — RGPD & Privacy  
> **Démarrer quand :** Endpoints `/v1/rgpd/*` disponibles  
> **Durée estimée :** 1 semaine


### 📋 Découpage en phases — Module 0.3 (1 semaine)

**Phase 0.3-A — State + UI rétention (Jour 1-2)**
- [ ] Créer `RgpdState` + Actions + Reducers → 0.5J
      📖 Cette section : ### NgRx — RGPD State
- [ ] Page `/rgpd/retention` (sélecteur durée) → 1J
      📖 Cette section : ### Pages & Composants

**Phase 0.3-B — Export + Effacement (Jour 3-4)**
- [ ] Bouton export données (`POST /v1/rgpd/export`) → 0.5J
      📖 Cette section : endpoints liste
- [ ] Bouton effacement avec `ConfirmDialog` (saisie nom org) → 1J
      📖 Cette section : ### Règles métier
- [ ] `AuditDirective` sur les actions sensibles → 0.5J
      📖 II.1bis — AuditService : action `rgpd:erasure-request`

**Phase 0.3-C — Tests (Jour 5)**
- [ ] Tests reducer + pages → 0.5J
      📖 Annexe C — Stratégie de Tests

### Objectif

Permettre au `TENANT_ADMIN` de gérer la conformité RGPD : configuration de la rétention, export des données, demande d'effacement. Ces pages sont accessibles uniquement par le `TENANT_ADMIN`.

### Pages & Composants

```
rgpd/
├── pages/
│   ├── rgpd-settings-page/            ← Container : hub RGPD
│   ├── retention-policy-page/         ← Container : configurer la durée de rétention
│   ├── data-export-page/              ← Container : demander un export JSON
│   └── data-deletion-page/            ← Container : demander l'effacement
└── components/
    ├── retention-slider/              ← Dumb : slider 30–365 jours
    ├── export-history-list/           ← Dumb : liste exports avec lien S3 signé
    ├── deletion-history-list/         ← Dumb : historique effacements
    ├── deletion-confirm-form/         ← Dumb : confirmation "SUPPRIMER MES DONNÉES"
    └── rgpd-audit-panel/              ← Dumb : audit trail RGPD (lecture seule)
```

### State RGPD

```typescript
// rgpd.actions.ts
export const RgpdActions = createActionGroup({
  source: 'Rgpd',
  events: {
    'Load Retention Policy':          emptyProps(),
    'Load Retention Policy Success':  props<{ retentionDays: number }>(),
    'Update Retention Policy':        props<{ retentionDays: number }>(),
    'Update Retention Policy Success': props<{ retentionDays: number }>(),

    'Request Data Export':            emptyProps(),
    'Request Data Export Success':    props<{ message: string }>(),
    'Request Data Export Failure':    props<{ error: string }>(),
    'Load Export History':            emptyProps(),
    'Load Export History Success':    props<{ exports: DataExport[] }>(),

    'Request Data Deletion':          props<{ confirmationText: string }>(),
    'Request Data Deletion Success':  props<{ message: string }>(),
    'Request Data Deletion Failure':  props<{ error: string }>(),
    'Load Deletion Reports':          emptyProps(),
    'Load Deletion Reports Success':  props<{ reports: DeletionReport[] }>(),
  },
});
```

### Endpoints consommés

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/v1/rgpd/retention-policy` | Consulter la politique de rétention |
| PUT | `/v1/rgpd/retention-policy` | Configurer la durée (30–365 jours) |
| POST | `/v1/rgpd/export` | Demander un export JSON (async) |
| GET | `/v1/rgpd/exports` | Historique des exports |
| DELETE | `/v1/rgpd/data` | Demander l'effacement complet du tenant |
| GET | `/v1/rgpd/deletion-reports` | Historique des suppressions |
| DELETE | `/v1/profile/account` | Suppression du compte utilisateur individuel |

### UX — Points critiques

- **Export de données :** Bouton "Demander l'export" → affiche "Vous recevrez un email dans les 24h avec le lien de téléchargement"
- **Effacement complet tenant :** Formulaire de confirmation avec saisie textuelle obligatoire `"SUPPRIMER MES DONNÉES"` (pas juste un checkbox)
- **Suppression compte individuel :** Saisie du mot de passe actuel + confirmation textuelle `"SUPPRIMER MON COMPTE"` — deux champs distincts
- **Durée de rétention :** Slider 30–365 jours avec prévisualisation de la date de suppression
- **Données PII :** Jamais affichées en clair dans l'UI — `[CHIFFRÉ]` ou masquées

### Suppression de compte individuel (BR-RGP-020 à BR-RGP-025)

Page accessible depuis le profil utilisateur — **tous les rôles, pas uniquement TENANT_ADMIN**.

```typescript
// Règles métier BR-RGP à respecter côté frontend
// BR-RGP-020 : tout utilisateur peut supprimer son compte
// BR-RGP-021 : mot de passe actuel + confirmation textuelle requis
// BR-RGP-022 : compte désactivé immédiatement → AuthActions.logout() dispatché
// BR-RGP-024 : si l'utilisateur est le SEUL admin du tenant → bloquer + message explicite
// BR-RGP-025 : le TENANT_ADMIN est notifié quand un membre supprime son compte (SSE)

// Actions à ajouter dans RgpdActions
'Delete Own Account':         props<{ currentPassword: string; confirmationText: string }>(),
'Delete Own Account Success': emptyProps(),   // → dispatch AuthActions.logout()
'Delete Own Account Failure': props<{ error: string }>(),
// Cas BR-RGP-024 : error.code === 'LAST_ADMIN_CANNOT_DELETE'
```

```
profile/
└── pages/
    └── profile-page/                  ← Container
        └── delete-account-section/    ← Dumb : formulaire suppression compte
            ├── Input mot de passe actuel
            ├── Input confirmation "SUPPRIMER MON COMPTE"
            └── Bouton rouge "Supprimer mon compte" (désactivé si les deux champs ne sont pas remplis)
```

### NFR — RGPD (miroir backend)

| ID | Exigence frontend | Cible |
|----|------------------|-------|
| NFR-RGP-001 | Page rétention : sauvegarde confirmée en < 1s | 100% |
| NFR-RGP-002 | Droit à l'effacement : feedback HTTP 202 immédiat affiché | 100% |
| NFR-RGP-003 | Export : message "email sous 24h" affiché dès la confirmation | 100% |
| NFR-RGP-004 | Suppression compte : logout immédiat après succès (BR-RGP-022) | 100% |

### Definition of Done — Module 0.3

- [ ] Page rétention : slider 30–365 jours + sauvegarde + confirmation
- [ ] Export : demande async + feedback "email sous 24h" + historique avec liens
- [ ] Effacement tenant : saisie `"SUPPRIMER MES DONNÉES"` + feedback async (HTTP 202)
- [ ] **Suppression compte individuel** : mot de passe + saisie `"SUPPRIMER MON COMPTE"` + logout immédiat
- [ ] BR-RGP-024 : si dernier admin → message d'erreur explicite, bouton désactivé
- [ ] Audit trail RGPD : lecture seule, non modifiable
- [ ] Toutes les pages tenant protégées `*docaiHasRole="'TENANT_ADMIN'"` (sauf suppression compte individuelle)

---

## Module 0.4 — Billing & Abonnements

> **Parallèle Backend :** Module 0.4 — Billing & Abonnements (Stripe)  
> **Démarrer quand :** Endpoints `/v1/billing/*` + Stripe configuré  
> **Durée estimée :** 1 semaine  
> **Important :** Jamais de numéro de CB dans l'application — redirect Stripe uniquement


### 📋 Découpage en phases — Module 0.4 (1 semaine)

**Phase 0.4-A — State + Plans (Jour 1-2)**
- [ ] Créer `BillingState` + Actions + Selectors → 1J
      📖 Cette section : ### NgRx — Billing State
- [ ] Page `/billing/plans` (4 plans côte à côte) → 1J
      📖 Cette section : ### Pages & Composants

**Phase 0.4-B — Stripe + Quota (Jour 3-4)**
- [ ] Checkout Stripe (`POST /v1/billing/checkout`) → 0.5J
      📖 Cette section : ### Stripe Checkout
- [ ] Pages succès/annulation Stripe → 0.5J
      📖 Cette section : composants liste
- [ ] `QuotaBar` + bannière Shell à 80% et 95% → 0.5J
      📖 II.8 — Shell Component : activeBanner

**Phase 0.4-C — État abonnement + Tests (Jour 5)**
- [ ] State machine lifecycle (TRIAL → ACTIVE → PAST_DUE → CANCELED) → 0.5J
      📖 Cette section : ### State Machine Billing
- [ ] Tests reducer 100% → 0.5J
      📖 Annexe C — Stratégie de Tests

### Objectif

Permettre au `TENANT_ADMIN` de consulter son plan actuel, s'abonner ou changer de plan via Stripe Checkout, et accéder au Customer Portal Stripe pour gérer ses moyens de paiement.

### Plans (miroir backend)

| Plan | Documents/mois | Prix | Feature gate |
|------|---------------|------|--------------|
| FREE | 50 | 0€ | Features de base |
| STARTER | 500 | 29€/mois | `*docaiPlanGate="'STARTER'"` |
| PRO | 10 000 | 149€/mois | `*docaiPlanGate="'PRO'"` |
| ENTERPRISE | Illimité | Sur devis | `*docaiPlanGate="'ENTERPRISE'"` |

### Pages & Composants

```
billing/
├── pages/
│   ├── plans-page/                    ← Container : comparatif plans + CTA upgrade
│   ├── checkout-success-page/         ← Container : confirmation paiement Stripe
│   ├── checkout-cancel-page/          ← Container : annulation checkout
│   └── billing-overview-page/         ← Container : plan actuel + usage + factures
└── components/
    ├── plan-comparison-card/          ← Dumb : une carte plan (features, prix, CTA)
    ├── current-plan-panel/            ← Dumb : plan actif + quota + prochaine facturation
    ├── usage-quota-display/           ← Dumb : docs utilisés / limite + barre colorée
    ├── invoice-list/                  ← Dumb : liste factures téléchargeables
    ├── upgrade-banner/                ← Dumb : bannière "Passez à PRO" si > 80% quota
    └── plan-feature-list/             ← Dumb : liste features par plan avec icônes
```

### State Billing

```typescript
// billing.model.ts
export type PlanType = 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE';
export type SubscriptionStatus = 'ACTIVE' | 'TRIAL' | 'PAST_DUE' | 'CANCELED' | 'UNPAID';

export interface Subscription {
  id: string;
  plan: PlanType;
  status: SubscriptionStatus;
  currentPeriodEnd: string;
  cancelAtPeriodEnd: boolean;
  documentsUsed: number;
  documentsLimit: number;
}

export interface Invoice {
  id: string;
  amount: number;
  currency: string;
  status: 'PAID' | 'OPEN' | 'VOID';
  createdAt: string;
  pdfUrl: string;
}

// billing.actions.ts
export const BillingActions = createActionGroup({
  source: 'Billing',
  events: {
    'Load Subscription':          emptyProps(),
    'Load Subscription Success':  props<{ subscription: Subscription }>(),
    'Load Subscription Failure':  props<{ error: string }>(),

    'Create Checkout Session':    props<{ plan: PlanType }>(),
    'Create Checkout Session Success': props<{ checkoutUrl: string }>(),
    'Create Checkout Session Failure': props<{ error: string }>(),

    'Open Customer Portal':       emptyProps(),
    'Open Customer Portal Success': props<{ portalUrl: string }>(),

    'Load Invoices':              emptyProps(),
    'Load Invoices Success':      props<{ invoices: Invoice[] }>(),

    'Cancel Subscription':        emptyProps(),
    'Cancel Subscription Success': props<{ subscription: Subscription }>(),
  },
});

// billing.selectors.ts
export const BillingSelectors = {
  subscription:  createSelector(selectBillingState, (s) => s.subscription),
  currentPlan:   createSelector(selectBillingState, (s) => s.subscription?.plan ?? 'FREE'),
  quotaPercent:  createSelector(selectBillingState, (s) => {
    if (!s.subscription) return 0;
    return Math.round((s.subscription.documentsUsed / s.subscription.documentsLimit) * 100);
  }),
  isQuotaWarning: createSelector(selectBillingState, (s) => {
    if (!s.subscription) return false;
    const pct = (s.subscription.documentsUsed / s.subscription.documentsLimit) * 100;
    return pct >= 80;
  }),
  isQuotaCritical: createSelector(selectBillingState, (s) => {
    if (!s.subscription) return false;
    const pct = (s.subscription.documentsUsed / s.subscription.documentsLimit) * 100;
    return pct >= 95;
  }),
  invoices:      createSelector(selectBillingState, (s) => s.invoices),
};
```

### Effect — Stripe Checkout (redirect)

```typescript
// Jamais de paiement dans l'app — redirect vers Stripe uniquement
readonly createCheckout$ = createEffect(() =>
  this.actions$.pipe(
    ofType(BillingActions.createCheckoutSession),
    switchMap(({ plan }) =>
      this.api.createCheckoutSession(plan).pipe(
        tap(({ checkoutUrl }) => window.location.href = checkoutUrl), // Redirect Stripe
        map(({ checkoutUrl }) => BillingActions.createCheckoutSessionSuccess({ checkoutUrl })),
        catchError((err) =>
          of(BillingActions.createCheckoutSessionFailure({ error: err.error?.detail ?? 'Erreur' }))
        )
      )
    )
  )
);

readonly openPortal$ = createEffect(() =>
  this.actions$.pipe(
    ofType(BillingActions.openCustomerPortal),
    switchMap(() =>
      this.api.createPortalSession().pipe(
        tap(({ portalUrl }) => window.open(portalUrl, '_blank')), // Ouvre Stripe Portal
        map(({ portalUrl }) => BillingActions.openCustomerPortalSuccess({ portalUrl })),
        catchError(() => of({ type: '__NOOP__' }))
      )
    )
  )
);
```

### Endpoints consommés

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/v1/billing/subscription` | Plan actuel + usage |
| POST | `/v1/billing/checkout` | Créer session Stripe Checkout |
| POST | `/v1/billing/portal` | Créer session Stripe Customer Portal |
| GET | `/v1/billing/invoices` | Liste des factures |
| DELETE | `/v1/billing/subscription` | Annuler l'abonnement |

### ADR-009 — Impact Frontend (Downgrade → Lecture seule)

> Quand un tenant passe en mode lecture seule (quota dépassé ou paiement échoué) :
> - Le backend retourne `HTTP 429` avec `errorCode: QUOTA_EXCEEDED`
> - L'`errorInterceptor` affiche un snack bar avec bouton "Upgrade"
> - Le bouton redirige vers `/billing/plans`
> - Les boutons d'upload sont désactivés via `*docaiPlanGate`
> - Une bannière `UpgradeBannerComponent` s'affiche dans le shell

### Definition of Done — Module 0.4

- [ ] Page plans : 4 plans côte à côte avec features et CTA
- [ ] Checkout : redirect Stripe (jamais de CB dans l'app)
- [ ] Retour checkout : pages `/billing/success` et `/billing/cancel`
- [ ] Customer Portal : lien "Gérer mon abonnement" ouvre Stripe Portal
- [ ] Quota : barre colorée (vert < 70%, orange 70–90%, rouge > 90%)
- [ ] Bannière upgrade affichée si quota > 80%
- [ ] `*docaiPlanGate` désactive les features des plans supérieurs
- [ ] ADR-009 : état lecture seule bien géré (upload désactivé + bannière)

---

# PARTIE 4 — PIPELINE DE TRAITEMENT

> **Durée : 10 semaines**
> **Prérequis : Partie 3 validée (Auth + Billing fonctionnels)**
> **Ordre obligatoire : Module 1 → 2 → 3 → 4**
> **Miroir exact de la Partie 4 backend**

---

## Module 1 — Upload & Reconnaissance

> **Parallèle Backend :** Module 1 — Reconnaissance de Documents  
> **Démarrer quand :** `POST /v1/documents` disponible  
> **Durée estimée :** 3 semaines (phases 1.1 + 1.2)


### 📋 Découpage en phases — Module 1 (3 semaines)

**Phase 1.1 — Socle état (Semaine 1)**
- [ ] Créer `DocumentState` + `DocumentActions` + Reducers → 1J
      📖 Cette section : ### NgRx — Document State
- [ ] Créer `DocumentService` (upload, liste, détail) → 0.5J
      📖 Cette section : ### Endpoints consommés
- [ ] Créer `DocumentEffects` (load, upload, SSE watch) → 1J
      📖 Cette section : ### Effects
- [ ] Tests unitaires store 100% → 1J
      📖 Annexe C — Stratégie de Tests

**Phase 1.2 — UI Upload + Liste (Semaine 2)**
- [ ] Créer `FileDropZoneComponent` Dumb (drag & drop, validation) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Créer page liste documents (paginée, skeleton, empty state) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Créer `StatusBadgeComponent` + `RiskBadgeComponent` → 0.5J
      📖 I.7 — Design System : tokens risk levels / statuts
- [ ] Créer filtres combinables (statut, type, risque, date) → 0.5J
      📖 Cette section : ### Règles métier côté frontend

**Phase 1.3 — Temps réel + Détail (Semaine 3)**
- [ ] Brancher SSE `DOCUMENT_UPDATED` sur la liste → 0.5J
      📖 II.3 — SseService : ### onDocumentUpdated
- [ ] Créer page détail document (métadonnées + timeline pipeline) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Créer `PipelineTimelineComponent` Dumb (réutilisable Module 4) → 1J
      📖 Module 4 — Pipeline : ### PipelineTimelineComponent
- [ ] Tests E2E upload complet → 0.5J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** Upload → statut temps réel → détail visible sans refresh.

### Models

```typescript
// document.model.ts
export type DocumentStatus =
  | 'PENDING' | 'CLASSIFYING' | 'CLASSIFIED'
  | 'EXTRACTING' | 'EXTRACTED' | 'ANALYZING_FRAUD'
  | 'COMPLETED' | 'FAILED' | 'NEEDS_REVIEW';

export type DocumentType =
  | 'FACTURE' | 'CNI' | 'PASSEPORT' | 'CONTRAT'
  | 'BULLETIN_SALAIRE' | 'ORDONNANCE' | 'RIB' | 'INCONNU';

export type RiskLevel = 'FAIBLE' | 'MODERE' | 'ELEVE' | 'CRITIQUE';

export interface Document {
  id: string;
  tenantId: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  status: DocumentStatus;
  type: DocumentType | null;
  classificationConfidence: number | null;   // 0.0–1.0
  riskScore: number | null;                   // 0–100
  riskLevel: RiskLevel | null;
  pipelineSteps: PipelineStep[];
  contentHash: string | null;                 // SHA-256 pour détection doublons
  createdAt: string;
  updatedAt: string;
}

export interface PipelineStep {
  name: string;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
}

export interface DocumentFilter {
  status?: DocumentStatus;
  type?: DocumentType;
  riskLevel?: RiskLevel;
  dateFrom?: string;
  dateTo?: string;
}
```

### NgRx — Document State

```typescript
// document.state.ts
export const documentAdapter = createEntityAdapter<Document>({
  selectId: (doc) => doc.id,
  sortComparer: (a, b) =>
    new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
});

export interface DocumentState extends EntityState<Document> {
  loadingIds: string[];
  listLoading: boolean;
  detailLoading: boolean;
  uploadLoading: boolean;
  listError: string | null;
  detailError: string | null;
  uploadError: string | null;
  currentPage: number;
  pageSize: number;
  totalElements: number;
  activeFilter: DocumentFilter | null;
  selectedId: string | null;
  uploadProgress: number;      // 0–100
}

export const initialDocumentState: DocumentState =
  documentAdapter.getInitialState({
    loadingIds: [], listLoading: false, detailLoading: false, uploadLoading: false,
    listError: null, detailError: null, uploadError: null,
    currentPage: 0, pageSize: 20, totalElements: 0,
    activeFilter: null, selectedId: null, uploadProgress: 0,
  });

// document.actions.ts
export const DocumentActions = createActionGroup({
  source: 'Document',
  events: {
    'Load Documents':         props<{ filter?: DocumentFilter; page?: number }>(),
    'Load Documents Success': props<{ documents: Document[]; total: number; page: number }>(),
    'Load Documents Failure': props<{ error: string }>(),

    'Load Document':          props<{ id: string }>(),
    'Load Document Success':  props<{ document: Document }>(),
    'Load Document Failure':  props<{ error: string }>(),

    'Upload Document':        props<{ file: File; metadata?: Record<string, string>; idempotencyKey: string }>(),
    'Upload Progress':        props<{ progress: number }>(),
    'Upload Success':         props<{ document: Document }>(),
    'Upload Failure':         props<{ error: string }>(),

    'Sse Document Updated':   props<{ document: Document }>(),

    'Select Document':        props<{ id: string }>(),
    'Clear Selected':         emptyProps(),
    'Apply Filter':           props<{ filter: DocumentFilter }>(),
    'Reset Filter':           emptyProps(),
  },
});

// document.reducer.ts
export const documentReducer = createReducer(
  initialDocumentState,

  on(DocumentActions.loadDocuments, (state) => ({ ...state, listLoading: true, listError: null })),
  on(DocumentActions.loadDocumentsSuccess, (state, { documents, total, page }) =>
    documentAdapter.setAll(documents, { ...state, listLoading: false, totalElements: total, currentPage: page })
  ),
  on(DocumentActions.loadDocumentsFailure, (state, { error }) => ({ ...state, listLoading: false, listError: error })),

  on(DocumentActions.uploadDocument, (state) => ({ ...state, uploadLoading: true, uploadProgress: 0, uploadError: null })),
  on(DocumentActions.uploadProgress, (state, { progress }) => ({ ...state, uploadProgress: progress })),
  on(DocumentActions.uploadSuccess, (state, { document }) =>
    documentAdapter.addOne(document, { ...state, uploadLoading: false, uploadProgress: 100 })
  ),
  on(DocumentActions.uploadFailure, (state, { error }) => ({ ...state, uploadLoading: false, uploadError: error })),

  // SSE — pas de loading, mise à jour silencieuse
  on(DocumentActions.sseDocumentUpdated, (state, { document }) =>
    documentAdapter.upsertOne(document, state)
  ),

  on(DocumentActions.selectDocument, (state, { id }) => ({ ...state, selectedId: id })),
  on(DocumentActions.clearSelected, (state) => ({ ...state, selectedId: null })),
  on(DocumentActions.applyFilter, (state, { filter }) => ({ ...state, activeFilter: filter, currentPage: 0 })),
  on(DocumentActions.resetFilter, (state) => ({ ...state, activeFilter: null, currentPage: 0 })),
);
```

### Décomposition Composants — Module 1

```
documents/
├── containers/
│   ├── document-list-page/            ← Smart : dispatch loadDocuments, select liste
│   ├── document-detail-page/          ← Smart : dispatch loadDocument, select selectedId
│   └── upload-page/                   ← Smart : orchestre l'upload
└── presentational/
    ├── document-table/                ← Dumb : table + tri + pagination
    ├── document-status-badge/         ← Dumb : badge coloré par statut
    ├── document-type-chip/            ← Dumb : chip type + icône
    ├── pipeline-timeline/             ← Dumb : étapes pipeline avec durées
    ├── upload-dropzone/               ← Dumb : drag & drop + validation
    ├── upload-file-preview/           ← Dumb : nom, taille, type sélectionné
    ├── upload-progress-bar/           ← Dumb : barre 0→100%
    ├── upload-success-card/           ← Dumb : résumé après upload réussi
    └── document-filter-bar/           ← Dumb : filtres status/type/riskLevel/dates
```

### Règles métier côté frontend (miroir BR backend)

| Règle Backend | Implémentation Frontend |
|--------------|------------------------|
| BR-REC-001 : Formats PDF, PNG, JPEG, TIFF, WEBP | Validation `accept=".pdf,.png,.jpg,.jpeg,.tiff,.webp"` + vérification MIME |
| BR-REC-002 : Max 20 MB | Validation taille avant upload (pas 25 MB comme avant) |
| BR-REC-004 : Idempotence `X-Idempotency-Key` | Génération UUID v4 à chaque nouvel upload, envoyé en header |
| BR-REC-008 : Traitement asynchrone | SSE → mise à jour statut sans rechargement |

### Definition of Done — Module 1

- [ ] `documentAdapter` trié par `createdAt` DESC
- [ ] Validation format : PDF, PNG, JPEG, TIFF, WEBP — max **20 MB** (BR-REC-002)
- [ ] Upload avec `X-Idempotency-Key` généré automatiquement (BR-REC-004)
- [ ] Progression 0→100% via `HttpEventType.UploadProgress`
- [ ] SSE : changement statut reflété sans rechargement
- [ ] Timeline pipeline visible sur la page détail (étapes + durées)
- [ ] Reducers : couverture 100%

---

## Module 2 — Extraction & Visualisation

> **Parallèle Backend :** Module 2 — Extraction d'Informations (phases 2.1 à 2.3)  
> **Démarrer quand :** `GET /v1/documents/{id}/extraction` disponible  
> **Durée estimée :** 3 semaines


### 📋 Découpage en phases — Module 2 (3 semaines)

**Phase 2.1 — Socle état extraction (Semaine 1)**
- [ ] Créer `ExtractionState` + Actions + Reducers → 1J
      📖 Cette section : ### NgRx — Extraction State
- [ ] Créer `ExtractionService` (load, patch champ) → 0.5J
      📖 Cette section : ### Endpoints consommés
- [ ] Implémenter `OptimisticUpdateService` pour la correction inline → 1J
      📖 II.4 — OptimisticUpdateService : snapshot + rollback
- [ ] Tests store 100% (rollback inclus) → 1J
      📖 Annexe C — Stratégie de Tests

**Phase 2.2 — UI Visualisation (Semaine 2)**
- [ ] Créer page `/documents/:id/extraction` (layout 2 colonnes) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Créer `ExtractionFieldRowComponent` Dumb (valeur + confiance + état) → 1J
      📖 Cette section : ### Codes de validation à afficher
- [ ] Intégrer PDF viewer (`@defer`, pdf.js) → 1J
      📖 Cette section : `@defer` sur PDF viewer

**Phase 2.3 — Correction + Edge cases (Semaine 3)**
- [ ] Correction inline avec optimistic update + rollback visuel → 1J
      📖 Cette section : ### NgRx — Extraction State + Optimistic Update
- [ ] Compteurs VALID / INVALID / CORRECTED / NEEDS_REVIEW → 0.5J
      📖 Cette section : ### Résumé de validation
- [ ] Champs PII affichés `[CHIFFRÉ]` (ADR-005) → 0.5J
      📖 Annexe A — ADR-005 : chiffrement PII
- [ ] Tests E2E correction complète → 0.5J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** Correction inline visible immédiatement, rollback automatique si erreur.

### Models Extraction

```typescript
// extraction.model.ts
export interface ExtractionResult {
  documentId: string;
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  fields: ExtractionField[];
  ocrConfidence: number;
  llmConfidence: number;
  processingTimeMs: number;
  extractedAt: string;
}

export interface ExtractionField {
  key: string;
  label: string;
  value: string | null;
  confidence: number;            // 0.0–1.0
  correctedValue?: string;       // Après correction manuelle
  correctedBy?: string;
  correctedAt?: string;
  validationStatus: 'VALID' | 'INVALID' | 'NEEDS_REVIEW' | 'CORRECTED';
  validationCode?: string;       // Ex: 'DATA_SIRET_INVALID', 'DATA_IBAN_INVALID'
}
```

### NgRx — Extraction State + Optimistic Update

```typescript
// extraction.state.ts
export const extractionAdapter = createEntityAdapter<ExtractionResult>({
  selectId: (e) => e.documentId,
});

export interface ExtractionState extends EntityState<ExtractionResult> {
  loadingIds: string[];
  correctionLoadingKeys: string[];          // 'documentId:fieldKey'
  correctionErrors: Record<string, string>;
  snapshotBeforeCorrection: ExtractionResult | null; // Pour rollback
}

// extraction.reducer.ts — Optimistic Update
on(ExtractionActions.saveCorrection, (state, { documentId, fieldKey, value }) => {
  const key      = `${documentId}:${fieldKey}`;
  const existing = state.entities[documentId];
  if (!existing) return { ...state, correctionLoadingKeys: [...state.correctionLoadingKeys, key] };

  // Snapshot avant modification (pour rollback)
  const snapshot = existing;
  const updated: ExtractionResult = {
    ...existing,
    fields: existing.fields.map((f) =>
      f.key === fieldKey
        ? { ...f, correctedValue: value, validationStatus: 'CORRECTED' }
        : f
    ),
  };
  return extractionAdapter.upsertOne(updated, {
    ...state,
    correctionLoadingKeys: [...state.correctionLoadingKeys, key],
    snapshotBeforeCorrection: snapshot,
  });
}),

// Rollback si erreur
on(ExtractionActions.saveCorrectionFailure, (state, { key }) => {
  if (state.snapshotBeforeCorrection) {
    return extractionAdapter.upsertOne(state.snapshotBeforeCorrection, {
      ...state,
      correctionLoadingKeys: state.correctionLoadingKeys.filter((k) => k !== key),
      snapshotBeforeCorrection: null,
    });
  }
  return { ...state, correctionLoadingKeys: state.correctionLoadingKeys.filter((k) => k !== key) };
}),
```

### Décomposition Composants — Module 2

```
documents/ (ajouts module 2)
├── containers/
│   └── extraction-detail-page/        ← Smart : dispatch loadExtraction
└── presentational/
    ├── extraction-header/             ← Dumb : status, confiances, temps traitement
    ├── extraction-fields-panel/       ← Dumb : liste champs extraits
    │   └── extraction-field-row/      ← Dumb : un champ + score + édition
    │       ├── confidence-indicator/  ← Dumb : badge vert/orange/rouge
    │       ├── validation-code-badge/ ← Dumb : badge 'SIRET invalide' / 'IBAN invalide'
    │       └── correction-inline/     ← Dumb : input inline éditable
    ├── extraction-document-preview/   ← Dumb : PDF/image original (pdf.js)
    └── extraction-validation-summary/ ← Dumb : VALID/INVALID/NEEDS_REVIEW/CORRECTED counts
```

### Codes de validation à afficher (miroir backend)

| Code backend | Libellé affiché | Couleur |
|-------------|-----------------|---------|
| `DATA_SIRET_INVALID` | SIRET invalide (INSEE) | Rouge |
| `DATA_IBAN_INVALID` | IBAN invalide | Rouge |
| `DATA_TVA_INVALID` | N° TVA invalide | Rouge |
| `DATA_ADDRESS_NOT_FOUND` | Adresse introuvable (BAN) | Orange |
| `DATA_RPPS_NOT_FOUND` | RPPS médecin introuvable | Orange |
| `DATA_AMOUNT_INCONSISTENT` | Montant incohérent | Orange |

### Definition of Done — Module 2

- [ ] `extractionAdapter` avec `selectId: (e) => e.documentId`
- [ ] Optimistic update correction → rollback automatique si erreur HTTP
- [ ] Confidence indicator : ≥ 0.8 vert, 0.5–0.8 orange, < 0.5 rouge
- [ ] Codes de validation affichés avec libellé humain (pas le code brut)
- [ ] Correction inline protégée par `*docaiHasRole="'ANALYST'"`
- [ ] Preview PDF à côté des champs extraits

---

## Module 3 — Fraude & Révision Humaine

> **Parallèle Backend :** Module 3 — Détection de Fraude (phases 3.1 à 3.3)  
> **Démarrer quand :** `GET /v1/fraud/review-queue` et `POST /v1/fraud/{id}/decision` disponibles  
> **Durée estimée :** 3 semaines


### 📋 Découpage en phases — Module 3 (3 semaines)

**Phase 3.1 — Socle état fraude (Semaine 1)**
- [ ] Créer `FraudState` + Actions + Reducers → 1J
      📖 Cette section : ### NgRx — Fraude State
- [ ] Créer `FraudService` (review queue, décision) → 0.5J
      📖 Cette section : ### Endpoints consommés
- [ ] Implémenter state machine révision (PENDING → APPROVED / REJECTED / ESCALATED) → 1J
      📖 Cette section : ### State Machine de Révision
- [ ] Tests store 100% → 1J
      📖 Annexe C — Stratégie de Tests

**Phase 3.2 — UI Queue + Détail (Semaine 2)**
- [ ] Créer page `/fraud` (liste paginée triée par score DESC) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Créer `FraudScoreGaugeComponent` Dumb (jauge 0-100) → 0.5J
      📖 Cette section : composants liste
- [ ] Créer page détail analyse (signaux par catégorie) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Bannière "Analyse partielle" si `isPartialAnalysis = true` → 0.5J
      📖 Cette section : ### Règles métier

**Phase 3.3 — Décisions + Alertes SSE (Semaine 3)**
- [ ] Boutons APPROUVER / REJETER / ESCALADER + `ConfirmDialog` → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] `AuditDirective` sur chaque bouton décision → 0.5J
      📖 II.1bis — AuditService : action `fraud:decision`
- [ ] Brancher SSE `FRAUD_ALERT` sur badge notifications topbar → 1J
      📖 II.3 — SseService : ### onFraudAlert
- [ ] Tests E2E décision complète → 0.5J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** Décision immuable après soumission, alerte SSE < 2s, audit tracé.

### Models Fraude

```typescript
// fraud.model.ts
export type ReviewDecision = 'APPROVED' | 'REJECTED' | 'ESCALATED';
export type ReviewStatus   = 'PENDING_REVIEW' | 'REVIEWING' | 'APPROVED' | 'REJECTED' | 'ESCALATED';

export interface FraudAnalysis {
  id: string;
  documentId: string;
  score: number;                 // 0–100 (-1 si analyse partielle)
  riskLevel: RiskLevel;
  signals: FraudSignal[];
  reviewStatus: ReviewStatus;
  reviewDecision?: ReviewDecision;
  reviewerId?: string;
  reviewedAt?: string;
  reviewNotes?: string;
  isPartialAnalysis: boolean;    // Flag analyse incomplète (ADR backend)
  createdAt: string;
}

export interface FraudSignal {
  type: string;                  // 'META_EDITOR_SUSPICIOUS', 'DATA_IBAN_INVALID', etc.
  weight: number;                // Contribution au score
  description: string;
  evidence: Record<string, unknown>;
  category: 'METADATA' | 'DATA' | 'VISUAL';
}

export interface FraudAlert {
  id: string;
  documentId: string;
  score: number;
  riskLevel: RiskLevel;
  receivedAt: string;
  read: boolean;
}
```

### NgRx — Fraude State

```typescript
// fraud.state.ts
export const fraudAdapter = createEntityAdapter<FraudAnalysis>({
  selectId: (f) => f.id,
  sortComparer: (a, b) => b.score - a.score,   // Plus dangereux en premier
});

export interface FraudState extends EntityState<FraudAnalysis> {
  queueLoading: boolean;
  queueError: string | null;
  decisionLoadingIds: string[];
  decisionErrors: Record<string, string>;
  selectedId: string | null;
  alerts: FraudAlert[];                          // Max 50 en mémoire
  totalInQueue: number;
  currentPage: number;
}

// fraud.actions.ts
export const FraudActions = createActionGroup({
  source: 'Fraud',
  events: {
    'Load Queue':              props<{ page?: number }>(),
    'Load Queue Success':      props<{ analyses: FraudAnalysis[]; total: number; page: number }>(),
    'Load Queue Failure':      props<{ error: string }>(),

    'Submit Decision':         props<{ analysisId: string; decision: ReviewDecision; notes?: string }>(),
    'Submit Decision Success': props<{ analysis: FraudAnalysis }>(),
    'Submit Decision Failure': props<{ analysisId: string; error: string }>(),

    'Sse Alert Received':      props<{ alert: FraudAlert }>(),
    'Mark Alert Read':         props<{ alertId: string }>(),
    'Select Analysis':         props<{ id: string }>(),
  },
});

// fraud.reducer.ts
export const fraudReducer = createReducer(
  fraudAdapter.getInitialState({
    queueLoading: false, queueError: null,
    decisionLoadingIds: [], decisionErrors: {},
    selectedId: null,
    alerts: [],
    totalInQueue: 0, currentPage: 0,
  }),

  on(FraudActions.loadQueue, (state) => ({ ...state, queueLoading: true, queueError: null })),
  on(FraudActions.loadQueueSuccess, (state, { analyses, total, page }) =>
    fraudAdapter.setAll(analyses, { ...state, queueLoading: false, totalInQueue: total, currentPage: page })
  ),

  on(FraudActions.submitDecision, (state, { analysisId }) => ({
    ...state, decisionLoadingIds: [...state.decisionLoadingIds, analysisId],
  })),
  on(FraudActions.submitDecisionSuccess, (state, { analysis }) =>
    fraudAdapter.upsertOne(analysis, {
      ...state,
      decisionLoadingIds: state.decisionLoadingIds.filter((id) => id !== analysis.id),
    })
  ),
  on(FraudActions.submitDecisionFailure, (state, { analysisId, error }) => ({
    ...state,
    decisionLoadingIds: state.decisionLoadingIds.filter((id) => id !== analysisId),
    decisionErrors: { ...state.decisionErrors, [analysisId]: error },
  })),

  on(FraudActions.sseAlertReceived, (state, { alert }) => ({
    ...state,
    alerts: [alert, ...state.alerts].slice(0, 50),  // Max 50 alertes
  })),
  on(FraudActions.markAlertRead, (state, { alertId }) => ({
    ...state,
    alerts: state.alerts.map((a) => a.id === alertId ? { ...a, read: true } : a),
  })),
);
```

### State Machine de Révision — Respect côté UI

```
PENDING_REVIEW → boutons APPROUVER / REJETER / ESCALADER actifs
REVIEWING      → boutons actifs (reviewer a pris en charge)
APPROVED       → tous les boutons désactivés (décision immuable)
REJECTED       → tous les boutons désactivés (décision immuable)
ESCALATED      → bouton second reviewer affiché
```

```typescript
// fraud-decision-panel.component.ts — State Machine respectée
protected get buttonsDisabled(): boolean {
  const status = this.analysis().reviewStatus;
  return status === 'APPROVED' || status === 'REJECTED' || this.loading();
}

protected get showEscalationNote(): boolean {
  return this.analysis().reviewStatus === 'ESCALATED';
}
```

### Décomposition Composants — Module 3

```
fraud/
├── containers/
│   ├── fraud-queue-page/              ← Smart : dispatch loadQueue
│   └── fraud-detail-page/            ← Smart : dispatch loadAnalysis + submitDecision
└── presentational/
    ├── fraud-queue-list/              ← Dumb : liste paginée avec filtres
    │   └── fraud-queue-item/          ← Dumb : une ligne (score, type, date, statut)
    ├── fraud-score-gauge/             ← Dumb : jauge 0–100 colorée
    ├── fraud-signals-list/            ← Dumb : signaux groupés par catégorie
    │   └── fraud-signal-card/         ← Dumb : un signal + poids + evidence
    ├── fraud-partial-analysis-banner/ ← Dumb : bannière "Analyse incomplète"
    ├── fraud-decision-panel/          ← Dumb : APPROVED/REJECTED/ESCALATED + notes
    ├── fraud-alert-bell/              ← Dumb : badge notifications SSE
    └── fraud-alert-list/             ← Dumb : liste alertes dans le menu
```

### Selectors Fraude

```typescript
export const FraudSelectors = {
  all:           fraudAdapter.getSelectors(selectFraudState).selectAll,
  queueLoading:  createSelector(selectFraudState, (s) => s.queueLoading),
  totalInQueue:  createSelector(selectFraudState, (s) => s.totalInQueue),
  recentAlerts:  createSelector(selectFraudState, (s) => s.alerts.slice(0, 10)),
  unreadCount:   createSelector(selectFraudState, (s) => s.alerts.filter((a) => !a.read).length),
  criticalCount: createSelector(selectFraudState, (s) =>
    s.alerts.filter((a) => a.riskLevel === 'CRITIQUE' && !a.read).length),
};
```

### Definition of Done — Module 3

- [ ] `fraudAdapter` trié par score DESC (`CRITIQUE` toujours en premier)
- [ ] Score -1 affiché comme "Analyse incomplète" (pas comme 0)
- [ ] State Machine respectée : boutons désactivés après décision
- [ ] Décision protégée par `*docaiHasRole="'FRAUD_REVIEWER'"`
- [ ] Alerte SSE → badge notification incrémenté en < 2s
- [ ] Signaux groupés par catégorie (METADATA / DATA / VISUAL)
- [ ] Bannière "Analyse partielle" si `isPartialAnalysis = true`

---

## Module 4 — Pipeline & Monitoring

> **Parallèle Backend :** Module 4 — Orchestration & Pipeline (phases 4.1 à 4.3)
> **Démarrer quand :** `GET /v1/admin/dlq` et `GET /v1/admin/pipeline/stats` disponibles
> **Durée estimée :** 2 semaines


### 📋 Découpage en phases — Module 4 (2 semaines)

**Phase 4.1 — Stats + DLQ (Semaine 1)**
- [ ] Créer `PipelineState` + Actions + Selectors (Entity Adapter) → 1J
      📖 Cette section : ### NgRx — Pipeline State complet
- [ ] Page `/pipeline` : 4 KPI cards + skeleton → 0.5J
      📖 Cette section : ### Décomposition Composants
- [ ] Liste DLQ paginée + badge tentatives rouge si = 3 → 1J
      📖 Cette section : ### DlqItemComponent
- [ ] `AuditDirective` sur replay + delete → 0.5J
      📖 II.1bis — AuditService : actions `dlq:replay`, `dlq:delete`

**Phase 4.2 — Actions + Rebuild (Semaine 2)**
- [ ] Replay avec `ConfirmDialog` + désactivé si 3 tentatives (BR-ORC-011) → 1J
      📖 Cette section : ### DlqItemComponent — Actions avec confirmation
- [ ] Bouton "Reconstruire Read Model" + `ConfirmDialog` + spinner (ADR-011) → 1J
      📖 Cette section : ### read-model-rebuild-panel
- [ ] Réutiliser `PipelineTimelineComponent` depuis Module 1 → 0.5J
      📖 Cette section : ### PipelineTimelineComponent — Réutilisable
- [ ] Tests reducer 100% → 0.5J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** Replay désactivé après 3 tentatives, rebuild trace l'audit.

### Objectif

Donner au `TENANT_ADMIN` une visibilité complète sur le pipeline de traitement : KPIs en temps réel, messages en Dead Letter Queue, et outils de maintenance (replay, rebuild read model). La timeline de traitement est également réutilisée dans les pages détail document (Module 1/2).

### Endpoints consommés

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/v1/admin/pipeline/stats` | KPIs : docs en cours, taux d'erreur, temps moyen |
| GET | `/v1/admin/dlq` | Liste messages DLQ paginée |
| POST | `/v1/admin/dlq/{id}/replay` | Rejouer un message (BR-ORC-011 : max 3 tentatives) |
| DELETE | `/v1/admin/dlq/{id}` | Supprimer un message de la DLQ |
| POST | `/v1/admin/read-model/rebuild` | Reconstruire le Read Model (ADR-011) |

### Models

```typescript
// pipeline.model.ts
export interface DlqMessage {
  id:           string;
  documentId:   string;
  sourceTopic:  string;
  errorReason:  string;
  attemptCount: number;    // BR-ORC-011 : max 3 tentatives avant abandon
  failedAt:     string;
}

export interface PipelineStats {
  documentsInProgress:   number;
  documentsFailedToday:  number;
  averageProcessingMs:   number;
  dlqSize:               number;
  lastReadModelRebuild?: string;  // ISO 8601
}
```

### NgRx — Pipeline State complet

```typescript
// pipeline.state.ts
export const dlqAdapter = createEntityAdapter<DlqMessage>({
  selectId:     (m) => m.id,
  sortComparer: (a, b) => new Date(b.failedAt).getTime() - new Date(a.failedAt).getTime(),
});

export interface PipelineState extends EntityState<DlqMessage> {
  stats:             PipelineStats | null;
  statsLoading:      boolean;
  statsError:        string | null;
  listLoading:       boolean;
  listError:         string | null;
  replayingIds:      string[];
  deletingIds:       string[];
  totalDlq:          number;
  currentPage:       number;
  rebuildInProgress: boolean;     // ADR-011 : reconstruire le Read Model
}

export const initialPipelineState: PipelineState = dlqAdapter.getInitialState({
  stats: null, statsLoading: false, statsError: null,
  listLoading: false, listError: null,
  replayingIds: [], deletingIds: [],
  totalDlq: 0, currentPage: 0,
  rebuildInProgress: false,
});

// pipeline.actions.ts
export const PipelineActions = createActionGroup({
  source: 'Pipeline',
  events: {
    'Load Stats':              emptyProps(),
    'Load Stats Success':      props<{ stats: PipelineStats }>(),
    'Load Stats Failure':      props<{ error: string }>(),

    'Load Dlq':                props<{ page?: number }>(),
    'Load Dlq Success':        props<{ messages: DlqMessage[]; total: number; page: number }>(),
    'Load Dlq Failure':        props<{ error: string }>(),

    'Replay Message':          props<{ id: string }>(),
    'Replay Message Success':  props<{ id: string }>(),
    'Replay Message Failure':  props<{ id: string; error: string }>(),

    'Delete Message':          props<{ id: string }>(),
    'Delete Message Success':  props<{ id: string }>(),
    'Delete Message Failure':  props<{ id: string; error: string }>(),

    'Rebuild Read Model':         emptyProps(),           // ADR-011
    'Rebuild Read Model Success': emptyProps(),
    'Rebuild Read Model Failure': props<{ error: string }>(),
  },
});

// pipeline.selectors.ts
export const PipelineSelectors = {
  all:               dlqAdapter.getSelectors(selectPipelineState).selectAll,
  stats:             createSelector(selectPipelineState, (s) => s.stats),
  statsLoading:      createSelector(selectPipelineState, (s) => s.statsLoading),
  listLoading:       createSelector(selectPipelineState, (s) => s.listLoading),
  totalDlq:          createSelector(selectPipelineState, (s) => s.totalDlq),
  replayingIds:      createSelector(selectPipelineState, (s) => s.replayingIds),
  deletingIds:       createSelector(selectPipelineState, (s) => s.deletingIds),
  rebuildInProgress: createSelector(selectPipelineState, (s) => s.rebuildInProgress),
  isReplaying:       (id: string) =>
    createSelector(selectPipelineState, (s) => s.replayingIds.includes(id)),
};
```

### Décomposition Composants — Module 4

```
pipeline/
├── containers/
│   ├── pipeline-overview-page/       ← Smart : charge stats + SSE watch
│   └── dlq-page/                     ← Smart : charge DLQ paginée
└── presentational/
    ├── pipeline-stats-grid/          ← Dumb : 4 KPI cards (en cours, erreurs, temps moyen, DLQ size)
    │   └── pipeline-stat-card/       ← Dumb : un KPI (label + valeur + tendance)
    ├── pipeline-timeline/            ← Dumb : réutilisé dans Module 1/2 détail document
    │   └── pipeline-step-badge/      ← Dumb : PENDING/RUNNING/DONE/FAILED + durée
    ├── dlq-list/                     ← Dumb : liste DLQ paginée
    │   └── dlq-item/                 ← Dumb : un message + topic + raison + tentatives + actions
    └── read-model-rebuild-panel/     ← Dumb : bouton rebuild + date dernier rebuild (ADR-011)
```

### PipelineTimelineComponent — Réutilisable (Module 1, 2, 4)

```typescript
// shared/components/pipeline-timeline/pipeline-timeline.component.ts
// DUMB — utilisé dans la page détail document ET dans le monitoring pipeline

@Component({
  selector: 'docai-pipeline-timeline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass, DatePipe],
  template: `
    <ol class="flex flex-col gap-2" aria-label="Étapes du pipeline">
      @for (step of steps(); track step.name) {
        <li class="flex items-start gap-3">
          <!-- Icône statut -->
          <span class="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 text-xs font-bold"
                [ngClass]="{
                  'bg-gray-100 text-gray-400': step.status === 'PENDING',
                  'bg-blue-100 text-blue-600 animate-pulse': step.status === 'RUNNING',
                  'bg-green-100 text-green-700': step.status === 'DONE',
                  'bg-red-100 text-red-700':   step.status === 'FAILED'
                }"
                [attr.aria-label]="step.name + ' : ' + step.status">
            @switch (step.status) {
              @case ('DONE')    { ✓ }
              @case ('FAILED')  { ✗ }
              @case ('RUNNING') { ● }
              @default          { ○ }
            }
          </span>

          <!-- Détail étape -->
          <div class="flex-1 min-w-0">
            <span class="text-sm font-medium text-gray-700">{{ step.name }}</span>
            @if (step.durationMs) {
              <span class="text-xs text-gray-400 ml-2">{{ step.durationMs }}ms</span>
            }
            @if (step.status === 'RUNNING') {
              <span class="text-xs text-blue-500 ml-2">En cours...</span>
            }
          </div>
        </li>
      }
    </ol>
  `,
})
export class PipelineTimelineComponent {
  @Input({ required: true }) steps!: PipelineStep[];
  protected readonly steps = input.required<PipelineStep[]>();
}
```

### DlqItemComponent — Actions avec confirmation

```typescript
// pipeline/presentational/dlq-item/dlq-item.component.ts
@Component({
  selector: 'docai-dlq-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, NgClass, AuditDirective],
  template: `
    <div class="docai-card p-4 flex items-start gap-4">
      <div class="flex-1 min-w-0">
        <p class="text-sm font-medium text-gray-900 truncate">{{ message().documentId }}</p>
        <p class="text-xs text-gray-500 mt-1">Topic : {{ message().sourceTopic }}</p>
        <p class="text-xs text-red-600 mt-1">{{ message().errorReason }}</p>
        <div class="flex items-center gap-3 mt-2">
          <span class="text-xs text-gray-400">{{ message().failedAt | date:'dd/MM HH:mm' }}</span>
          <span class="docai-badge"
                [ngClass]="message().attemptCount >= 3 ? 'bg-red-50 text-red-700' : 'bg-yellow-50 text-yellow-700'">
            {{ message().attemptCount }}/3 tentatives
          </span>
        </div>
      </div>

      <div class="flex gap-2 flex-shrink-0">
        <!-- Replay — désactivé si déjà 3 tentatives -->
        <button
          [disabled]="replaying() || message().attemptCount >= 3"
          docaiAudit="dlq:replay"
          [docaiAuditId]="message().id"
          [docaiAuditMeta]="{ topic: message().sourceTopic, attempts: message().attemptCount }"
          (click)="onReplay()"
          class="px-3 py-1.5 text-xs rounded-md bg-primary-50 text-primary-700
                 hover:bg-primary-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          {{ replaying() ? 'Replay...' : 'Rejouer' }}
        </button>

        <!-- Supprimer -->
        <button
          [disabled]="deleting()"
          docaiAudit="dlq:delete"
          [docaiAuditId]="message().id"
          (click)="onDelete()"
          class="px-3 py-1.5 text-xs rounded-md bg-red-50 text-red-700
                 hover:bg-red-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          {{ deleting() ? 'Suppression...' : 'Supprimer' }}
        </button>
      </div>
    </div>
  `,
})
export class DlqItemComponent {
  @Input({ required: true }) message!: DlqMessage;
  @Input() replaying = input(false);
  @Input() deleting  = input(false);
  @Output() replay = new EventEmitter<string>();
  @Output() delete = new EventEmitter<string>();

  protected readonly message  = input.required<DlqMessage>();
  protected readonly replaying = input(false);
  protected readonly deleting  = input(false);

  protected onReplay(): void { this.replay.emit(this.message().id); }
  protected onDelete(): void { this.delete.emit(this.message().id); }
}
```

### Definition of Done — Module 4

- [ ] `PipelineStats` chargé au `ngOnInit` de la page overview
- [ ] `PipelineTimelineComponent` réutilisé dans Module 1/2 (page détail document) — pas de duplication
- [ ] DLQ : liste paginée + raison d'échec + compteur tentatives (rouge si = 3)
- [ ] Replay désactivé si `attemptCount >= 3` (BR-ORC-011)
- [ ] `ConfirmDialog` obligatoire avant Replay ET avant Delete
- [ ] `AuditDirective` sur tous les boutons (replay, delete, rebuild)
- [ ] Bouton "Reconstruire le Read Model" : `ConfirmDialog` + spinner pendant `rebuildInProgress`
- [ ] Boutons protégés par `*docaiHasRole="'TENANT_ADMIN'"`
- [ ] Reducers + selectors couverts à 100%


---

# PARTIE 5 — PRODUIT

> **Durée : 5 semaines**
> **Prérequis : Partie 4 fonctionnelle (pipeline complet)**
> **Miroir exact de la Partie 5 backend**

---

## Module 5 — Dashboard & SSE

> **Parallèle Backend :** Module 5 — Dashboard & Reporting  
> **Démarrer quand :** `GET /v1/dashboard/summary` + SSE `GET /v1/dashboard/stream` disponibles  
> **Durée estimée :** 2 semaines


### 📋 Découpage en phases — Module 5 (2 semaines)

**Phase 5.1 — State + KPIs (Semaine 1)**
- [ ] Créer `DashboardState` + Actions + Selectors → 1J
      📖 Cette section : ### NgRx — Dashboard State
- [ ] Page `/dashboard` : KPIs + skeleton (< 100ms NFR-DSH-001) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Section quota : docs, API calls, stockage → 0.5J
      📖 Cette section : ### QuotaBar dashboard

**Phase 5.2 — Temps réel (Semaine 2)**
- [ ] Brancher SSE `SUMMARY_UPDATED` sur les KPIs → 0.5J
      📖 II.3 — SseService : ### onSummaryUpdated
- [ ] Brancher SSE `QUOTA_WARNING` → QuotaBar + bannière Shell → 0.5J
      📖 II.3 — SseService : ### onQuotaWarning
- [ ] Indicateur "Dernière mise à jour il y a X secondes" → 0.5J
      📖 Cette section : ### Décomposition Composants
- [ ] Tests SSE dashboard → 0.5J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** KPIs mis à jour sans spinner après SSE, quota temps réel.

### Models Dashboard

```typescript
// pagination.model.ts — Format standard BR-PAG-001 à BR-PAG-008
// Miroir exact du format ApiResponse<T> du backend commons-api
export interface PageMetadata {
  number: number;           // Page courante (commence à 0)
  size: number;             // Taille de la page
  totalElements: number;    // Total des éléments
  totalPages: number;       // Total des pages
  first: boolean;           // Première page ?
  last: boolean;            // Dernière page ?
}

export interface ApiPageResponse<T> {
  data: T[];
  page: PageMetadata;
}

// Règles BR-PAG à respecter côté frontend :
// BR-PAG-001 : paramètres page, size, sort sur tous les appels liste
// BR-PAG-002 : size max = 100 (le backend retourne 400 si > 100)
// BR-PAG-003 : size par défaut = 20
// BR-PAG-006 : tri par défaut = createdAt,desc sauf indication contraire

// dashboard.model.ts
export interface DashboardSummary {
  totalDocuments: number;
  byStatus: Record<DocumentStatus, number>;
  byType: Record<DocumentType, number>;
  byRiskLevel: Record<RiskLevel, number>;
  averageProcessingTimeMs: number;
  successRate: number;
  fraudDetectionRate: number;
  periodStart: string;
  periodEnd: string;
}

export interface UsageQuota {
  plan: PlanType;
  documentsUsed: number;
  documentsLimit: number;
  apiCallsUsed: number;
  apiCallsLimit: number;
  storageUsedBytes: number;
  storageLimitBytes: number;
  periodEnd: string;
}
```

### NgRx — Dashboard State (scalaires — pas d'EntityAdapter)

```typescript
// dashboard.state.ts
export interface DashboardState {
  summary: DashboardSummary | null;
  quota: UsageQuota | null;
  summaryLoading: boolean;
  quotaLoading: boolean;
  summaryError: string | null;
  lastRefreshed: string | null;
  sseConnected: boolean;
}

// dashboard.actions.ts
export const DashboardActions = createActionGroup({
  source: 'Dashboard',
  events: {
    'Load Summary':         emptyProps(),
    'Load Summary Success': props<{ summary: DashboardSummary }>(),
    'Load Summary Failure': props<{ error: string }>(),
    'Load Quota':           emptyProps(),
    'Load Quota Success':   props<{ quota: UsageQuota }>(),
    'Sse Connected':        emptyProps(),
    'Sse Disconnected':     emptyProps(),
    'Sse Summary Updated':  props<{ summary: DashboardSummary }>(),  // SSE : PAS de loading
    'Sse Quota Warning':    props<{ quota: UsageQuota }>(),           // SSE : alerte quota
  },
});

// dashboard.reducer.ts
on(DashboardActions.sseSummaryUpdated, (state, { summary }) => ({
  ...state,
  summary,
  lastRefreshed: new Date().toISOString(),
  summaryLoading: false,  // SSE ne déclenche JAMAIS summaryLoading = true
})),
on(DashboardActions.sseQuotaWarning, (state, { quota }) => ({
  ...state,
  quota,                  // Mise à jour quota en temps réel
})),
```

### Endpoints consommés — Module 5

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/v1/dashboard/summary` | KPIs du tenant |
| GET | `/v1/dashboard/documents` | Liste paginée Read Model (BR-PAG-001) |
| GET | `/v1/dashboard/documents/{id}` | Détail document Read Model |
| GET | `/v1/analytics/usage` | Usage quota temps réel |
| GET | `/v1/analytics/fraud-trends` | Tendances fraude sur période |
| GET | `/v1/dashboard/stream` | SSE flux temps réel |

### NFR — Dashboard (miroir backend)

| ID | Exigence frontend | Cible |
|----|------------------|-------|
| NFR-DSH-001 | Rendu liste 100 documents | < 200ms |
| NFR-DSH-002 | Délai affichage après event SSE | < 2s |
| NFR-DSH-003 | Réponse dashboard summary | < 100ms (P95) |

### Décomposition Composants — Module 5

```
dashboard/
├── containers/
│   └── dashboard-page/                ← Smart
└── presentational/
    ├── kpi-grid/                      ← Dumb : 4 MetricCard (docs, temps moyen, fraudes, succès)
    │   └── metric-card/               ← Dumb : label + valeur + tendance
    ├── documents-recent-panel/        ← Container (réutilise DocumentSelectors)
    ├── fraud-alerts-panel/            ← Container (réutilise FraudSelectors)
    │   └── fraud-alert-mini-list/     ← Dumb : alertes triées par sévérité
    ├── activity-chart/                ← Dumb : barres d'activité (@defer)
    ├── type-distribution-chart/       ← Dumb : répartition par type (@defer)
    ├── quota-panel/                   ← Dumb : barres de progression par ressource
    │   └── quota-bar/                 ← Dumb : label + used/total + couleur
    ├── sse-status-indicator/          ← Dumb : point vert "Temps réel" / orange "Reconnexion"
    └── upgrade-banner/                ← Dumb : bannière si quota > 80% (ADR-009)
```

### Definition of Done — Module 5

- [ ] KPIs actualisés via SSE (zéro polling)
- [ ] `sseSummaryUpdated` ne déclenche pas `summaryLoading = true`
- [ ] Indicateur SSE : vert si connecté, orange si reconnexion en cours
- [ ] Quota : vert < 70%, orange 70–90%, rouge > 90%
- [ ] Bannière upgrade si quota > 80% → lien `/billing/plans`
- [ ] `@defer` sur les composants chart (chargement à la demande)
- [ ] Dashboard responsive mobile + desktop

---

## Module 6 — API Management

> **Parallèle Backend :** Module 6 — Intégrations & API Publique  
> **Démarrer quand :** `GET /v1/api-keys`, `POST /v1/webhooks` disponibles  
> **Durée estimée :** 2 semaines


### 📋 Découpage en phases — Module 6 (2 semaines)

**Phase 6.1 — Clés API (Semaine 1)**
- [ ] Créer `SettingsState` + Actions + Selectors → 0.5J
      📖 Cette section : ### NgRx — Settings State
- [ ] Page liste clés API (préfixe, scopes, expiration, dernier usage) → 1J
      📖 Cette section : ### Décomposition Composants
- [ ] Dialog création clé (plaintext affiché UNE SEULE FOIS + copier) → 1J
      📖 Cette section : ### Création clé API
- [ ] Révocation avec `ConfirmDialog` + optimistic update → 0.5J
      📖 Cette section : ### Révocation
- [ ] `AuditDirective` : `api-key:create` et `api-key:revoke` → 0.5J
      📖 II.1bis — AuditService : liste des actions

**Phase 6.2 — Webhooks (Semaine 2)**
- [ ] Formulaire webhook (URL + sélection événements) → 1J
      📖 Cette section : ### Webhooks
- [ ] Bouton "Tester" + affichage résultat → 0.5J
      📖 Cette section : ### Test webhook
- [ ] Historique livraisons (statut HTTP + date) → 0.5J
      📖 Cette section : composants liste
- [ ] `AuditDirective` : `webhook:delete` → 0.5J
      📖 II.1bis — AuditService : liste des actions

**Critère de passage :** Clé créée affichée une seule fois, `clearNewKey` dispatché à la fermeture.

### Models API Management

```typescript
// api-key.model.ts
export interface ApiKey {
  id: string;
  name: string;
  prefix: string;                // Ex: 'sk-docai-abc1'
  plainTextKey?: string;         // UNIQUEMENT à la création — jamais en DB
  scopes: ApiScope[];
  createdAt: string;
  expiresAt?: string;
  lastUsedAt?: string;
  revokedAt?: string;
}

export type ApiScope =
  | 'documents:read' | 'documents:write'
  | 'fraud:read' | 'webhooks:manage';

// webhook.model.ts
export interface Webhook {
  id: string;
  url: string;
  events: WebhookEvent[];
  active: boolean;
  secret: string;               // UNIQUEMENT à la création
  createdAt: string;
  lastDeliveryAt?: string;
  failureCount: number;
}

export type WebhookEvent =
  | 'document.completed' | 'document.failed'
  | 'fraud.detected' | 'fraud.decision_made';
```

### NgRx — Settings State

```typescript
// settings.state.ts
export const apiKeyAdapter  = createEntityAdapter<ApiKey>({
  selectId: (k) => k.id,
  sortComparer: (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
});
export const webhookAdapter = createEntityAdapter<Webhook>({ selectId: (w) => w.id });

export interface ApiKeyState extends EntityState<ApiKey> {
  loading: boolean;
  creating: boolean;
  newKeyPlainText: string | null;   // Affiché UNE SEULE FOIS
}

// settings.actions.ts
export const SettingsActions = createActionGroup({
  source: 'Settings',
  events: {
    'Load Api Keys':           emptyProps(),
    'Load Api Keys Success':   props<{ keys: ApiKey[] }>(),
    'Create Api Key':          props<{ name: string; scopes: ApiScope[]; expiresAt?: string }>(),
    'Create Api Key Success':  props<{ key: ApiKey }>(),
    'Revoke Api Key':          props<{ id: string }>(),
    'Revoke Api Key Success':  props<{ id: string }>(),
    'Clear New Key':           emptyProps(),              // Effacement après fermeture dialog

    'Load Webhooks':           emptyProps(),
    'Load Webhooks Success':   props<{ webhooks: Webhook[] }>(),
    'Create Webhook':          props<{ url: string; events: WebhookEvent[] }>(),
    'Create Webhook Success':  props<{ webhook: Webhook }>(),
    'Test Webhook':            props<{ id: string }>(),
    'Test Webhook Success':    props<{ id: string }>(),
    'Delete Webhook':          props<{ id: string }>(),
    'Delete Webhook Success':  props<{ id: string }>(),
  },
});
```

### Décomposition Composants — Module 6

```
settings/
├── containers/
│   ├── api-keys-page/                 ← Smart
│   └── webhooks-page/                 ← Smart
└── presentational/
    ├── api-key-list/                  ← Dumb : liste clés avec préfixe, scopes, dates
    ├── api-key-created-dialog/        ← Dumb : clé plaintext affichée UNE SEULE FOIS
    ├── api-key-scope-selector/        ← Dumb : checkboxes scopes
    ├── webhook-list/                  ← Dumb : liste webhooks + failureCount
    ├── webhook-events-selector/       ← Dumb : checkboxes événements
    └── webhook-delivery-list/         ← Dumb : historique livraisons
```

### Definition of Done — Module 6

- [ ] `apiKeyAdapter` + `webhookAdapter` avec `createEntityAdapter`
- [ ] Clé API plaintext affichée UNE SEULE FOIS → `clearNewKey` dispatché à la fermeture
- [ ] Révocation clé : `ConfirmDialog` obligatoire
- [ ] Test webhook : bouton "Tester" + résultat de livraison affiché
- [ ] Toutes les actions protégées par `*docaiHasRole="'TENANT_ADMIN'"`

---

## Module 7 — Billing UI Complet

> **Parallèle Backend :** Module 7 — Billing Complet  
> **Démarrer quand :** Tous les endpoints `/v1/billing/*` disponibles, Stripe Live configuré  
> **Durée estimée :** 1 semaine


### 📋 Découpage en phases — Module 7 (1 semaine)

**Phase 7.1 — Abonnement + Stripe Portal (Jour 1-3)**
- [ ] Page `/billing` : plan actuel + statut + prochaine échéance → 1J
      📖 Cette section : ### Pages & Composants
- [ ] Statuts colorés (ACTIVE / TRIAL / PAST_DUE / CANCELED) → 0.5J
      📖 I.7 — Design System : tokens couleurs plans
- [ ] Bouton "Gérer" → Stripe Customer Portal → 0.5J
      📖 Cette section : ### Stripe Portal

**Phase 7.2 — Cycles de vie + Tests (Jour 4-5)**
- [ ] Countdown J-7 et J-3 pour fin de trial → 0.5J
      📖 Cette section : ### Trial countdown
- [ ] Statut PAST_DUE → upload désactivé + tooltip → 0.5J
      📖 II.8 — Shell Component : bannière past-due non fermable
- [ ] Annulation avec `ConfirmDialog` + `AuditDirective` → 0.5J
      📖 II.1bis — AuditService : action `subscription:cancel`
- [ ] Tests reducer lifecycle complet → 0.5J
      📖 Annexe C — Stratégie de Tests

**Critère de passage :** Upload bloqué si PAST_DUE, annulation tracée dans l'audit.

### Objectif

Compléter l'expérience billing avec : gestion du cycle de vie de l'abonnement, affichage des overage, emails de facturation, et scénarios de downgrade.

### Pages & Composants Module 7

```
billing/ (ajouts module 7)
├── containers/
│   ├── subscription-lifecycle-page/   ← Smart : état actuel + actions (cancel, reactivate)
│   └── overage-page/                  ← Smart : docs supplémentaires facturés
└── presentational/
    ├── subscription-status-card/      ← Dumb : statut ACTIVE/TRIAL/PAST_DUE/CANCELED
    ├── next-billing-panel/            ← Dumb : prochaine échéance + montant prévu
    ├── overage-breakdown/             ← Dumb : docs en excès + tarif unitaire
    ├── trial-countdown/               ← Dumb : J-7 / J-3 avant fin trial
    └── past-due-warning/              ← Dumb : bannière paiement échoué + CTA portal
```

### Scénarios de cycle de vie à gérer

| Statut Stripe | Affichage frontend | Action disponible |
|--------------|-------------------|------------------|
| `ACTIVE` | Badge vert "Actif" | Gérer (Stripe Portal) |
| `TRIAL` | Badge bleu "Essai + countdown" | Souscrire maintenant |
| `PAST_DUE` | Bannière rouge "Paiement échoué" | Mettre à jour CB (Portal) |
| `CANCELED` | Badge gris "Résilié" | Se réabonner |
| `UNPAID` | Bannière rouge + lecture seule | Régulariser |

### ADR-009 — Lecture seule si `PAST_DUE` ou `UNPAID`

- Upload désactivé : `UploadDropzoneComponent` grisé + tooltip "Régularisez votre abonnement"
- `*docaiPlanGate` bloque toutes les features payantes
- Bannière `PastDueWarningComponent` dans le Shell

### Definition of Done — Module 7

- [ ] Tous les statuts Stripe affichés avec la bonne UI
- [ ] Countdown trial affiché J-7 et J-3 (email backend → event SSE → mise à jour UI)
- [ ] Bannière paiement échoué avec lien Stripe Portal
- [ ] Annulation : `ConfirmDialog` + "Votre accès est maintenu jusqu'au X"
- [ ] Lecture seule si `PAST_DUE` / `UNPAID` (ADR-009 respecté)

---

# ANNEXES

---

## Annexe A — 11 ADR Backend → Impact Frontend

> Miroir de l'Annexe E du `DOCAI_BACKEND_MASTER_SPECKIT_F.md` v15.0

| ADR | Titre | Impact Frontend |
|-----|-------|----------------|
| **ADR-001** | Rate Limiting Lua atomique (Bucket4j + Valkey) | Afficher le `retryAfter` du 429 dans le snack bar. Bouton "Upgrade" si quota dépassé → `/billing/plans` |
| **ADR-002** | Clé partition Kafka = documentId | Aucun impact direct — garantit l'ordre des SSE reçus côté frontend |
| **ADR-003** | TTL cache Valkey avec jitter | Aucun impact direct — évite les cache stampede côté backend |
| **ADR-004** | Avro + Apicurio Schema Registry | Aucun impact direct — les payloads SSE sont en JSON |
| **ADR-005** | Chiffrement PII via AWS KMS | Afficher `[CHIFFRÉ]` pour les champs PII non accessibles. Ne jamais logger les valeurs PII |
| **ADR-006** | Cache JWKS Keycloak 1h | Keycloak-Angular gère le refresh. Si 401 persistant → `AuthActions.logout()` |
| **ADR-007** | AbortMultipartUpload S3 si > 5 parties | Afficher une erreur explicite si upload interrompu > 5 parties (fichier trop grand ou connexion coupée) |
| **ADR-008** | Virtual Threads Java 21 | Aucun impact frontend |
| **ADR-009** | Downgrade → lecture seule si quota dépassé | `*docaiPlanGate` désactive les features. Bannière upgrade. Upload grisé. Redirect `/billing/plans` sur 429 |
| **ADR-010** | Archivage S3 Glacier après 90j | Afficher "Document archivé — téléchargement sous 12h" si statut ARCHIVED |
| **ADR-011** | Read Model réconciliation toutes les 5min | Accepter un délai de 5 min d'éventuelles incohérences sur les listes. SSE est la source de vérité temps réel. Endpoint admin `POST /v1/admin/read-model/rebuild` : bouton "Reconstruire le Read Model" dans la page pipeline (TENANT_ADMIN uniquement) |

---

## Annexe A.2 — Feature Flags (miroir backend)

> Le backend utilise Unleash pour les feature flags. Le frontend doit les consommer via un endpoint dédié ou les lire dans le JWT/config.

| Flag backend | Impact frontend | Comportement si `false` |
|-------------|-----------------|------------------------|
| `billing.enabled` | Masquer toutes les pages billing | Pages `/billing/*` redirigent vers `/dashboard` |
| `fraud.v2.enabled` | Activer le nouveau scoring fraude | Afficher l'ancien format de signaux |
| `dashboard.search.enabled` | Afficher la barre de recherche full-text | Masquer le composant `SearchBarComponent` |
| `maintenance.mode` | Kill switch global | Page de maintenance affichée, toutes routes bloquées |

```typescript
// feature-flags.service.ts (Core singleton)
@Injectable({ providedIn: 'root' })
export class FeatureFlagsService {
  private readonly http    = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  // Chargé au démarrage de l'app (APP_INITIALIZER)
  loadFlags(): Observable<Record<string, boolean>> {
    return this.http.get<Record<string, boolean>>(`${this.baseUrl}/v1/config/features`);
  }

  isEnabled(flag: string): boolean {
    return this.flags[flag] ?? false;
  }
}

// Utilisation dans app.config.ts
{
  provide: APP_INITIALIZER,
  useFactory: (flags: FeatureFlagsService) => () => flags.loadFlags(),
  deps: [FeatureFlagsService],
  multi: true,
}
```

---

## Annexe B — Standards & Clean Code Angular

### Nommage des fichiers (obligatoire)

```
feature.model.ts                  Interfaces TypeScript
feature.state.ts                  EntityState + adapter + initialState
feature.actions.ts                createActionGroup
feature.reducer.ts                createReducer
feature.effects.ts                createEffect
feature.selectors.ts              createSelector
feature-list-page.component.ts    Container (smart)
feature-detail-page.component.ts  Container (smart)
feature-table.component.ts        Presentational (dumb)
feature-card.component.ts         Presentational (dumb)
feature-api.service.ts            API service
feature-detail.resolver.ts        Resolver
feature.guard.ts                  Guard
feature.routes.ts                 Routes lazy
feature.reducer.spec.ts           Tests reducer
feature.selectors.spec.ts         Tests selectors
feature.effects.spec.ts           Tests effects
feature-api.service.spec.ts       Tests service
```

### Règles de code (miroir backend — méthodes ≤ 20 lignes)

| Règle | Angular |
|-------|---------|
| Méthodes ≤ 20 lignes | Idem backend — extraire dans des helpers privés |
| Composants ≤ 200 lignes | Décomposer en sous-composants |
| Zéro `any` TypeScript | Toujours typer — `unknown` si nécessaire |
| Pas de `subscribe()` dans les Containers | `toSignal()` uniquement |
| Jamais `inject(Store)` dans un Dumb | Violation architecture — rejet en review |
| Pas de `console.log` en prod | Utiliser `PerformanceService` ou supprimer |

### Convention de nommage des Actions

```typescript
// Format auto-généré par createActionGroup :
DocumentActions.loadDocuments         // '[Document] Load Documents'
DocumentActions.loadDocumentsSuccess  // '[Document] Load Documents Success'
DocumentActions.loadDocumentsFailure  // '[Document] Load Documents Failure'
BillingActions.createCheckoutSession  // '[Billing] Create Checkout Session'
```

---

## Annexe C — Stratégie de Tests

### Pyramide de tests

```
                 ┌─────────────────────────┐
                 │   E2E Playwright         │  Parcours critiques : signup, upload,
                 │   (10-20 tests)          │  décision fraude, checkout Stripe
                 └────────────┬────────────┘
              ┌───────────────┴───────────────┐
              │  Intégration                  │  Composants + Store combinés
              │  @testing-library/angular     │  (20-40 tests par feature)
              └───────────────┬───────────────┘
    ┌──────────────────────────┴──────────────────────────┐
    │  Unitaires Jest (80% du volume)                     │
    │  Reducers 100% · Selectors 100% · Effects 100%      │
    └─────────────────────────────────────────────────────┘
```

### Tests Reducers (patron universel)

```typescript
// RÈGLE : chaque action testée indépendamment
// RÈGLE : jamais de Store réel dans les tests de reducer

describe('documentReducer', () => {
  it('should return initial state on unknown action', () => {
    expect(documentReducer(undefined, { type: '__UNKNOWN__' }))
      .toEqual(initialDocumentState);
  });

  it('should set uploadLoading=true on uploadDocument', () => {
    const state = documentReducer(
      initialDocumentState,
      DocumentActions.uploadDocument({ file: new File([], 'test.pdf'), idempotencyKey: 'key-1' })
    );
    expect(state.uploadLoading).toBe(true);
    expect(state.uploadProgress).toBe(0);
    expect(state.uploadError).toBeNull();
  });

  it('should upsertOne on sseDocumentUpdated without touching loading', () => {
    const existing = createTestDocument({ id: 'doc-1', status: 'CLASSIFYING' });
    const updated  = { ...existing, status: 'CLASSIFIED' as DocumentStatus };
    let state = documentReducer(
      initialDocumentState,
      DocumentActions.uploadSuccess({ document: existing })
    );
    state = documentReducer(state, DocumentActions.sseDocumentUpdated({ document: updated }));
    expect(state.entities['doc-1']?.status).toBe('CLASSIFIED');
    expect(state.listLoading).toBe(false);   // SSE ne déclenche pas de loading
  });
});
```

### Tests Selectors (avec .projector())

```typescript
// RÈGLE : jamais de Store réel — .projector() uniquement
describe('AuthSelectors', () => {
  it('hasRole: true when role matches', () => {
    const state = { ...initialAuthState, user: createTestUser({ roles: ['FRAUD_REVIEWER'] }) };
    expect(AuthSelectors.hasRole('FRAUD_REVIEWER').projector(state)).toBe(true);
  });

  it('hasRole: false when role is missing', () => {
    const state = { ...initialAuthState, user: createTestUser({ roles: ['ANALYST'] }) };
    expect(AuthSelectors.hasRole('TENANT_ADMIN').projector(state)).toBe(false);
  });
});

describe('BillingSelectors', () => {
  it('quotaPercent: 68% if used=3412 limit=5000', () => {
    const sub = createTestSubscription({ documentsUsed: 3412, documentsLimit: 5000 });
    const state = { ...initialBillingState, subscription: sub };
    expect(BillingSelectors.quotaPercent.projector(state)).toBe(68);
  });

  it('isQuotaWarning: true if usage >= 80%', () => {
    const sub = createTestSubscription({ documentsUsed: 4100, documentsLimit: 5000 });
    const state = { ...initialBillingState, subscription: sub };
    expect(BillingSelectors.isQuotaWarning.projector(state)).toBe(true);
  });
});
```

### Tests Effects

```typescript
describe('DocumentEffects', () => {
  let actions$: Observable<Action>;
  let effects: DocumentEffects;
  let apiSpy: jest.SpyInstance;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DocumentEffects,
        provideMockActions(() => actions$),
        { provide: DocumentApiService, useValue: { getDocuments: jest.fn(), uploadDocument: jest.fn() } },
        { provide: SseService, useValue: { connect: jest.fn(() => EMPTY) } },
        provideMockStore({ initialState: { auth: { ...initialAuthState, authenticated: true } } }),
      ],
    });
    effects = TestBed.inject(DocumentEffects);
    apiSpy  = jest.spyOn(TestBed.inject(DocumentApiService), 'getDocuments');
  });

  it('should dispatch loadDocumentsSuccess on API success', () => {
    const docs = [createTestDocument()];
    apiSpy.mockReturnValue(of({ items: docs, total: 1, page: 0 }));
    actions$ = of(DocumentActions.loadDocuments({}));
    effects.loadDocuments$.subscribe((action) => {
      expect(action).toEqual(
        DocumentActions.loadDocumentsSuccess({ documents: docs, total: 1, page: 0 })
      );
    });
  });

  it('should dispatch loadDocumentsFailure on API error', () => {
    apiSpy.mockReturnValue(throwError(() => ({ error: { detail: 'Erreur' } })));
    actions$ = of(DocumentActions.loadDocuments({}));
    effects.loadDocuments$.subscribe((action) => {
      expect(action.type).toBe(DocumentActions.loadDocumentsFailure.type);
    });
  });
});
```

### Tests E2E Playwright — Parcours critiques

```typescript
// e2e/signup.spec.ts
test('Inscription complète', async ({ page }) => {
  await page.goto('/signup');
  await page.fill('[data-testid="email"]', 'test@acme.test');
  await page.fill('[data-testid="password"]', 'Test1234!');
  await page.fill('[data-testid="orgName"]', 'ACME Corp');
  await page.click('[data-testid="submit-signup"]');
  await expect(page.locator('[data-testid="email-sent-card"]')).toBeVisible();
});

// e2e/upload.spec.ts
test('Upload document et suivi SSE', async ({ page }) => {
  await login(page, 'analyst@acme-corp.test', 'Test1234!');
  await page.goto('/documents/upload');
  await page.setInputFiles('[data-testid="file-input"]', 'fixtures/facture.pdf');
  await expect(page.locator('[data-testid="upload-progress"]')).toBeVisible();
  await expect(page.locator('[data-testid="upload-success"]')).toBeVisible({ timeout: 10000 });
});

// e2e/fraud.spec.ts
test('Décision de révision fraude', async ({ page }) => {
  await login(page, 'reviewer@acme-corp.test', 'Test1234!');
  await page.goto('/fraud');
  await page.click('[data-testid="fraud-queue-item"]:first-child');
  await page.fill('[data-testid="review-notes"]', 'Document falsifié');
  await page.click('[data-testid="reject-btn"]');
  await expect(page.locator('[data-testid="decision-confirmed"]')).toBeVisible();
});
```

---

## Annexe D — Données de Test (Miroir backend)

### Comptes préconfigurés (correspondance exacte backend)

| Email | Rôle | Tenant | Mot de passe | Usage |
|-------|------|--------|-------------|-------|
| `admin@acme-corp.test` | TENANT_ADMIN | acme-corp | `Test1234!` | Gestion complète |
| `analyst@acme-corp.test` | ANALYST | acme-corp | `Test1234!` | Upload documents |
| `viewer@acme-corp.test` | VIEWER | acme-corp | `Test1234!` | Lecture seule |
| `reviewer@acme-corp.test` | FRAUD_REVIEWER | acme-corp | `Test1234!` | Queue révision |
| `admin@beta-assur.test` | TENANT_ADMIN | beta-assur | `Test1234!` | Test isolation |

### Helper de test universel

```typescript
// test-helpers.ts
export function createTestDocument(overrides: Partial<Document> = {}): Document {
  return {
    id: 'doc-test-1',
    tenantId: 'acme-corp',
    fileName: 'test.pdf',
    fileSize: 102400,
    mimeType: 'application/pdf',
    status: 'PENDING',
    type: null,
    classificationConfidence: null,
    riskScore: null,
    riskLevel: null,
    pipelineSteps: [],
    contentHash: null,
    createdAt: '2026-05-01T10:00:00Z',
    updatedAt: '2026-05-01T10:00:00Z',
    ...overrides,
  };
}

export function createTestUser(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    sub: 'usr-test-1',
    email: 'analyst@acme-corp.test',
    tenantId: 'acme-corp',
    roles: ['ANALYST'],
    twoFactorEnabled: false,
    ...overrides,
  };
}

export function createTestSubscription(overrides: Partial<Subscription> = {}): Subscription {
  return {
    id: 'sub-test-1',
    plan: 'PRO',
    status: 'ACTIVE',
    currentPeriodEnd: '2026-06-01T00:00:00Z',
    cancelAtPeriodEnd: false,
    documentsUsed: 1000,
    documentsLimit: 10000,
    ...overrides,
  };
}
```

### Tenants de test — 3 tenants préconfigurés (miroir backend BR-SEED-001 à 005)

| Tenant | tenant_id | Plan | Quota | Usage | Cas de test |
|--------|-----------|------|-------|-------|-------------|
| ACME Corp | `acme-corp` | Pro | 10 000 docs | 1 000 | Tests fonctionnels principaux |
| Beta Assurances | `beta-assur` | Starter | 500 docs | 50 | Tests isolation tenant |
| **Gamma RH** | `gamma-rh` | Starter | 500 docs | **490** | **Tests quota presque dépassé — bannière upgrade + ADR-009** |

> **Gamma RH est critique pour tester :** bannière "Vous avez utilisé 98% de votre quota", bouton upload grisé au-delà de 500, snack bar 429 avec retryAfter, redirect vers `/billing/plans`.

---

## Annexe E — Environment Configuration

```typescript
// environment.ts
export const environment: AppEnvironment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:8180',
    realm: 'docai',
    clientId: 'docai-frontend',
  },
  stripePublicKey: 'pk_test_CHANGE_ME',
  sseReconnectDelayMs: 3000,
  uploadMaxSizeMB: 20,                       // BR-REC-002 : max 20 MB
};

// environment.prod.ts
export const environment: AppEnvironment = {
  production: true,
  apiBaseUrl: 'https://api.docai.fr',
  keycloak: {
    url: 'https://auth.docai.fr',
    realm: 'docai',
    clientId: 'docai-frontend',
  },
  stripePublicKey: 'pk_live_CHANGE_ME',
  sseReconnectDelayMs: 5000,
  uploadMaxSizeMB: 20,
};
```

---

## Annexe F — Checklist Pull Request Frontend

```markdown
## Checklist Architecture
- [ ] Composant Dumb : zéro inject(Store) — uniquement @Input/@Output
- [ ] Composant Smart : toSignal() pour la lecture du state
- [ ] changeDetection: OnPush sur tous les composants
- [ ] standalone: true sur tous les composants
- [ ] @if / @for (jamais *ngIf / *ngFor)
- [ ] Méthodes ≤ 20 lignes, composants ≤ 200 lignes

## Checklist NgRx
- [ ] Nouvelle collection → createEntityAdapter configuré
- [ ] Actions dans createActionGroup (jamais createAction isolé)
- [ ] Reducer : chaque action testée indépendamment
- [ ] Selectors : testés avec .projector() (sans Store réel)
- [ ] Effects : succès ET erreur testés avec provideMockActions

## Checklist Tests
- [ ] Reducers ≥ 100% couverture
- [ ] Selectors ≥ 100% couverture
- [ ] Services : HttpTestingController
- [ ] Composants Dumb : @testing-library/angular
- [ ] Aucun test n'importe le Store réel

## Checklist Sécurité
- [ ] Actions admin protégées par *docaiHasRole
- [ ] Features premium protégées par *docaiPlanGate
- [ ] Clé API plaintext : effacée du state après fermeture dialog (clearNewKey)
- [ ] Pas de données PII dans les logs (pas de console.log avec données utilisateur)
- [ ] Jamais de numéro de CB dans l'app (redirect Stripe uniquement)

## Checklist ADR
- [ ] ADR-001 : retryAfter affiché sur 429 + bouton Upgrade
- [ ] ADR-005 : champs PII affichés [CHIFFRÉ] si non déchiffrés
- [ ] ADR-009 : lecture seule si PAST_DUE / quota dépassé (bannière + upload grisé)
- [ ] ADR-011 : délai 5 min accepté sur les listes (SSE = source de vérité)

## Checklist Performance
- [ ] track sur tous les @for
- [ ] @defer pour composants > 50kB (charts, pdf viewer)
- [ ] Bundle budget : ng build sans warning
- [ ] Lighthouse Accessibility = 100 en local
```

---

## Annexe G — Commandes de Développement

```bash
# Dev avec proxy (évite les CORS)
ng serve --proxy-config proxy.conf.json

# proxy.conf.json
# {
#   "/v1/*":  { "target": "http://localhost:8080", "changeOrigin": true },
#   "/auth/*": { "target": "http://localhost:8180", "changeOrigin": true }
# }

# Tests
npm test                                    # Tous les tests Jest
npm test -- --watch                         # Mode watch
npm test -- --coverage                      # Rapport coverage
npm test -- --testPathPattern=billing       # Feature spécifique

# Build
ng build --configuration=production         # Build prod avec vérification budgets
npx ng-bundle-analyzer                      # Analyser les chunks

# Qualité
npm run lint                                # ESLint
npm run format                              # Prettier
npm run type-check                          # tsc --noEmit

# E2E
npm run e2e                                 # Playwright (app dev en cours)
npm run e2e:ci                              # Mode headless CI
npx playwright show-trace trace.zip         # Analyser un échec E2E
```

---

## Annexe I — i18n Préparation (Backlog v2)

> **Statut :** Non prioritaire pour le lancement. Miroir de l'Annexe G.2 du backend v15.0.
> **Lib choisie : `@ngx-translate/core`** (voir ADR ci-dessous)
> **À préparer maintenant** sans implémenter, pour éviter une réécriture complète en v2.

### ADR — Choix de la lib i18n : ngx-translate vs Angular i18n natif

| Critère | `@ngx-translate/core` ✅ | Angular i18n natif |
|---------|------------------------|--------------------|
| Lazy loading par feature | Oui — fichiers JSON par module | Non — un build par locale |
| Changement de langue runtime | Oui — sans recharger la page | Non — rebuild complet |
| Compatibilité Signals | Oui | Oui |
| Strings dans TypeScript | `translate.instant('key')` | Impossible sans pipe |
| Complexité setup | Faible | Élevée (xliff, build config) |
| Conclusion | **Choisi** | ❌ Écarté |

**Decision :** `@ngx-translate/core` pour la flexibilité runtime et la compatibilité lazy loading.

### Installation (à faire en v2)

```bash
npm install @ngx-translate/core @ngx-translate/http-loader
```

### Règles à respecter dès maintenant (BR-I18N)

| ID | Règle | Implémentation Angular |
|----|-------|----------------------|
| BR-I18N-001 | Jamais de strings UI codées en dur | Utiliser des constantes dans `i18n/keys.ts` — prêt pour `TranslateService` |
| BR-I18N-002 | Dates et montants : ISO 8601 et ISO 4217 dans les appels API | `DatePipe` format `'yyyy-MM-ddTHH:mm:ssZ'`, `CurrencyPipe` code ISO |
| BR-I18N-003 | La langue du tenant est configurable | Champ `language` dans `UserProfile` → stocké dans le Store |

```typescript
// i18n/keys.ts — centraliser TOUTES les strings UI (prêt pour ngx-translate)
export const I18N = {
  errors: {
    quota_exceeded:  'errors.quota_exceeded',
    upload_failed:   'errors.upload_failed',
    server_error:    'errors.server_error',
  },
  actions: {
    close:    'actions.close',
    retry:    'actions.retry',
    upgrade:  'actions.upgrade',
    confirm:  'actions.confirm',
    cancel:   'actions.cancel',
  },
  fraud: {
    approve:  'fraud.approve',
    reject:   'fraud.reject',
    escalate: 'fraud.escalate',
  },
} as const;

// MAL — string hardcodée :
snack.open('Quota dépassé', 'Fermer', { duration: 4000 });

// BIEN — clé centralisée (prêt pour translate.instant en v2) :
import { I18N } from '@core/i18n/keys';
// En v1 : utiliser les valeurs par défaut françaises depuis un fichier de constantes
// En v2 : remplacer par translate.instant(I18N.errors.quota_exceeded)
snack.open(FR_STRINGS[I18N.errors.quota_exceeded], FR_STRINGS[I18N.actions.close], { duration: 4000 });
```

```typescript
// i18n/fr.ts — strings françaises par défaut (v1, remplacé par JSON en v2)
export const FR_STRINGS: Record<string, string> = {
  'errors.quota_exceeded': 'Quota dépassé',
  'errors.upload_failed':  'Échec de l'upload',
  'errors.server_error':   'Erreur serveur — veuillez réessayer',
  'actions.close':         'Fermer',
  'actions.retry':         'Réessayer',
  'actions.upgrade':       'Upgrader',
  'actions.confirm':       'Confirmer',
  'actions.cancel':        'Annuler',
  'fraud.approve':         'Approuver',
  'fraud.reject':          'Rejeter',
  'fraud.escalate':        'Escalader',
};
```

**Langues cibles v2 :** français (FR — défaut), anglais (EN), espagnol (ES), allemand (DE).


---

## Annexe J — Tests de Performance Playwright (Backlog v2)

> Miroir de l'Annexe G.1 du backend v15.0 — seuils Playwright équivalents aux seuils k6 backend.

### Seuils par module (cibles P95)

| Module | Action | Seuil P95 | Outil |
|--------|--------|-----------|-------|
| Module 1 — Upload | Upload 5MB PDF + réponse 201 | < 2s | Playwright `page.waitForResponse` |
| Module 2 — Extraction | Affichage liste champs après navigation | < 500ms | Playwright `page.waitForSelector` |
| Module 5 — Dashboard | Rendu KPIs au chargement | < 100ms | Playwright `performance.timing` |
| Module 6 — API Keys | Affichage liste clés | < 300ms | Playwright `page.waitForSelector` |

```typescript
// e2e/performance/upload-perf.spec.ts
test('Upload P95 < 2s', async ({ page }) => {
  await login(page, 'analyst@acme-corp.test', 'Test1234!');
  await page.goto('/documents/upload');

  const start = Date.now();
  await page.setInputFiles('[data-testid="file-input"]', 'fixtures/facture-5mb.pdf');
  await page.waitForSelector('[data-testid="upload-success"]');
  const duration = Date.now() - start;

  expect(duration).toBeLessThan(2000);   // P95 < 2s (BR-PERF-001)
});

test('Dashboard P95 < 100ms', async ({ page }) => {
  await login(page, 'admin@acme-corp.test', 'Test1234!');

  const start = Date.now();
  await page.goto('/dashboard');
  await page.waitForSelector('[data-testid="kpi-grid"]');
  const duration = Date.now() - start;

  expect(duration).toBeLessThan(100);    // NFR-DSH-001
});
```

### Quand exécuter

- Avant chaque release production (staging uniquement — BR-PERF-001)
- Hebdomadaire via job CI planifié
- Jamais en production

---

## Annexe K — Guide Onboarding Développeur Frontend

> **Objectif : environnement fonctionnel en moins d'une heure.**
> Miroir de l'Annexe F.6 du backend v15.0.

### Étape 1 — Prérequis (10 min)

1. Installer Node.js 22 LTS (`node -v` → `22.x.x`)
2. Installer Angular CLI 21 (`npm install -g @angular/cli@21`)
3. S'assurer que le backend est démarré (`docker compose up -d` dans le dépôt backend)
4. Cloner le dépôt frontend

### Étape 2 — Installation (5 min)

```bash
npm ci --prefer-offline
cp .env.example .env.local
# Remplir KEYCLOAK_URL, API_BASE_URL, STRIPE_PUBLIC_KEY
```

### Étape 3 — Démarrage (2 min)

```bash
ng serve --proxy-config proxy.conf.json
# → http://localhost:4200
```

Se connecter avec `admin@acme-corp.test / Test1234!`

### Étape 4 — Vérifications (5 min)

| Vérification | Résultat attendu |
|-------------|-----------------|
| `http://localhost:4200` | Dashboard avec KPIs |
| Connexion `admin@acme-corp.test` | Profil TENANT_ADMIN visible |
| Upload un PDF | Statut `PENDING` → `CLASSIFIED` via SSE |
| Page `/billing/plans` | 4 plans visibles |
| Page `/fraud` | Queue de révision visible |

### Étape 5 — Lecture obligatoire (15 min)

1. Sections I.1 à I.5 (architecture Feature-First + analogie hexagonale)
2. Partie 2 — Commons Angular (7 commons — ne jamais réimplémenter)
3. Annexe A — 11 ADR frontend (décisions critiques)
4. Annexe F — Checklist PR (obligatoire avant chaque merge)

### Étape 6 — Premier commit

```bash
# Format Conventional Commits obligatoire
git commit -m "feat(documents): add upload progress bar"
git commit -m "fix(fraud): handle partial analysis score -1"
git commit -m "test(billing): add checkout selector tests"
```

---

## Annexe H — Definition of Done Globale

> Miroir du Definition of Done backend — mêmes standards, langage différent.

- [ ] `standalone: true` + `ChangeDetectionStrategy.OnPush` sur tout composant
- [ ] Toute collection utilise `createEntityAdapter<T>` (jamais de tableau brut)
- [ ] Toute action dans un `createActionGroup` (jamais `createAction` isolé)
- [ ] Reducers : couverture 100% — chaque action testée
- [ ] Selectors : testés avec `.projector()` uniquement (sans Store réel)
- [ ] Effects : succès ET erreur testés avec `provideMockActions`
- [ ] Composants Dumb : testés avec `@testing-library/angular`
- [ ] Pagination : format `ApiPageResponse<T>` respecté (BR-PAG-001 à 008)
- [ ] Feature Flags : `FeatureFlagsService` consulté avant affichage des features gated
- [ ] Strings UI : jamais codées en dur (prêt pour i18n — BR-I18N-001)
- [ ] Lighthouse Accessibility = 100
- [ ] Bundle budget respecté (`ng build` sans warning)
- [ ] ESLint : 0 warning, 0 erreur
- [ ] TypeScript strict : `tsc --noEmit` sans erreur
- [ ] Conventional Commits respectés : `feat(billing): add checkout page`
- [ ] ADR frontend respectés (voir Annexe A)
