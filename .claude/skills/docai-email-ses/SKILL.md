---
name: docai-email-ses
description: Implémente l'adapter email Amazon SES avec templates Thymeleaf dans DocAI. Utiliser quand on demande d'envoyer un email, d'implémenter un template email, de configurer Amazon SES, ou d'ajouter une notification email (bienvenue, quota, facturation, invitation).
---

# DocAI — Adapter Email Amazon SES

## Templates disponibles — catalogue complet

| Template | Déclencheur | Module |
|----------|------------|--------|
| `welcome` | Inscription réussie | 0.1 |
| `email-verification` | Inscription | 0.1 |
| `invitation` | TENANT_ADMIN invite un collègue | 0.1 |
| `password-reset` | Mot de passe oublié | 0.2 |
| `password-changed` | MDP modifié | 0.4 |
| `quota-warning-80` | 80% quota atteint | 7 |
| `quota-warning-95` | 95% quota atteint | 7 |
| `subscription-activated` | Paiement Stripe OK | 7 |
| `invoice` | 1er du mois | 7 |
| `payment-failed` | Stripe payment_failed | 7 |
| `trial-ending-7days` | J-7 avant fin FREE | 7 |
| `trial-ending-3days` | J-3 avant fin FREE | 7 |
| `trial-expired` | Expiration FREE | 7 |
| `account-revoked` | Révocation accès | 0 |
| `support-access-request` | Agent support demande accès | 0 |
| `data-deletion-confirmed` | Effacement RGPD | 0 |

## Port — interface domaine

```java
public interface EmailNotificationPort {
    void send(EmailMessage message);
}

public record EmailMessage(
    String to,           // Email destinataire
    String template,     // Nom du template (ex: "quota-warning-80")
    Map<String, Object> variables, // Variables dynamiques
    String tenantId      // Pour les logs (jamais logguer l'email)
) {}
```

## Adapter Amazon SES + Thymeleaf

```java
@Component
public class AmazonSesEmailAdapter implements EmailNotificationPort {

    private final SesClient sesClient;
    private final TemplateEngine templateEngine;  // Thymeleaf
    private static final String FROM = "noreply@docai.fr";

    @Override
    public void send(EmailMessage message) {
        try {
            // 1. Charger le template HTML via Thymeleaf
            String htmlBody = renderTemplate(message.template() + "/fr", message.variables());
            String textBody = renderTemplate(message.template() + "-text/fr", message.variables());

            // 2. Construire la requête SES
            SendEmailRequest request = SendEmailRequest.builder()
                .destination(d -> d.toAddresses(message.to()))
                .message(m -> m
                    .subject(c -> c.data(getSubject(message.template(), message.variables())))
                    .body(b -> b
                        .html(c -> c.data(htmlBody).charset("UTF-8"))
                        .text(c -> c.data(textBody).charset("UTF-8")) // Fallback texte brut
                    )
                )
                .source(FROM)
                .build();

            sesClient.sendEmail(request);

            // Log avec PII masqués (BR-EMAIL-002)
            log.info("Email sent template={} tenantId={} recipient=[PII_MASKED]",
                message.template(), message.tenantId());

        } catch (SesException e) {
            log.error("Email send failed template={} tenantId={} reason={}",
                message.template(), message.tenantId(), e.getMessage());
            // Ne pas propager l'exception — l'email est best-effort
        }
    }

    private String renderTemplate(String templatePath, Map<String, Object> variables) {
        Context context = new Context(Locale.FRENCH);
        // Variables communes à tous les templates
        context.setVariable("appUrl", "https://app.docai.fr");
        context.setVariable("supportEmail", "support@docai.fr");
        context.setVariable("year", LocalDate.now().getYear());
        // Variables spécifiques
        variables.forEach(context::setVariable);
        return templateEngine.process("email-templates/" + templatePath, context);
    }
}
```

## Structure des templates Thymeleaf

```
src/main/resources/email-templates/
├── fr/
│   ├── quota-warning-80.html
│   ├── quota-warning-80-text.txt
│   ├── subscription-activated.html
│   ├── subscription-activated-text.txt
│   └── ... (tous les templates)
└── layout/
    ├── base.html        ← Layout commun (header logo, footer légal)
    └── base-text.txt
```

## Template HTML — structure obligatoire

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{email-templates/layout/base :: layout(~{::content})}">
<body>
<th:block th:fragment="content">
    <h1>Votre quota DocAI atteint 80%</h1>
    <p>Bonjour <span th:text="${firstName}">Alice</span>,</p>
    <p>Vous avez utilisé <strong th:text="${usagePercent}">80</strong>%
       de votre quota mensuel de <span th:text="${quotaLimit}">500</span> documents.</p>

    <!-- Un seul CTA par email -->
    <a th:href="${upgradeUrl}" class="cta-button">Upgrader mon plan</a>

    <!-- Lien désinscription obligatoire (BR-EMAIL-003) -->
    <p><a th:href="${unsubscribeUrl}">Se désabonner des notifications</a></p>
</th:block>
</body>
</html>
```

## Variables dynamiques par template

```java
// quota-warning-80
Map.of(
    "firstName", "[PII_MASKED]",   // Récupéré depuis Keycloak, pas loggué
    "tenantName", "ACME Corp",
    "usagePercent", 80,
    "quotaLimit", 500,
    "documentsUsed", 400,
    "renewalDate", "1er juin 2026",
    "upgradeUrl", "https://app.docai.fr/billing/upgrade"
)

// subscription-activated
Map.of(
    "firstName", "[PII_MASKED]",
    "planName", "Pro",
    "quotaPerMonth", 10000,
    "renewalDate", "1er juillet 2026",
    "dashboardUrl", "https://app.docai.fr/dashboard"
)
```

## Configuration SES

```yaml
# application.yml
spring:
  mail:
    host: email-smtp.eu-west-3.amazonaws.com
    port: 587
    username: ${SES_SMTP_USERNAME}
    password: ${SES_SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

# application-dev.yml — Sandbox (emails non envoyés en DEV)
docai:
  email:
    sandbox: true      # BR-EMAIL-004 : sandbox en DEV et STAGING
```

## Checklist

- [ ] Chaque template a une version HTML et texte brut (BR-EMAIL-001)
- [ ] Destinataire jamais loggué — `[PII_MASKED]` (BR-EMAIL-002)
- [ ] Lien désinscription dans chaque email (BR-EMAIL-003)
- [ ] SES en mode sandbox en DEV/STAGING (BR-EMAIL-004)
- [ ] Variables dynamiques passées via `Map<String, Object>` (jamais en dur)
- [ ] Envoi best-effort — exception SES catchée, pas propagée
- [ ] Template texte brut fallback pour clients sans HTML
- [ ] Test : vérifier que l'email est envoyé au bon template au bon déclencheur
- [ ] Monitoring SES : bounce rate < 5%, complaint rate < 0.1%
