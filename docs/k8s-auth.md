# Kubernetes CI Authentication (TASK-SEC-002)

## Authentication Method

CI workflows `03-deploy-staging.yml` and `04-deploy-production.yml` authenticate to Kubernetes
using a **base64-encoded kubeconfig** stored as a GitHub Actions secret. The kubeconfig embeds
a service account token with the minimum required RBAC permissions.

## Required GitHub Secrets

| Secret Name | Used By | Contents |
|-------------|---------|----------|
| `KUBE_CONFIG_STAGING` | `03-deploy-staging.yml` | Base64-encoded kubeconfig for staging cluster |
| `KUBE_CONFIG_PRODUCTION` | `04-deploy-production.yml` | Base64-encoded kubeconfig for production cluster |

To create the secret value:
```bash
kubectl config view --minify --flatten | base64 -w0
```

## CI Service Account RBAC

The kubeconfig must reference a service account with **minimum required permissions** in the
`docai-staging` / `docai-production` namespace only:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ci-deploy
  namespace: docai-staging
rules:
  - apiGroups: [apps]
    resources: [deployments]
    verbs: [get, patch]         # kubectl set image + rollout status
  - apiGroups: [""]
    resources: [pods]
    verbs: [get, list, watch]   # rollout status watch
```

## Negative Verification

Run the following to confirm the CI service account cannot escalate to cluster-admin:

```bash
kubectl auth can-i delete namespaces \
  --as=system:serviceaccount:docai-staging:ci-deploy
# Expected output: no
```

## Future: OIDC Federation

If the cluster supports OIDC federation (IRSA on EKS, Workload Identity on GKE), replace the
kubeconfig secret with OIDC configuration:

- **EKS (IRSA)**: add `AWS_ROLE_ARN` secret; `id-token: write` is pre-positioned in workflow permissions but currently unused — switching to OIDC eliminates the `KUBE_CONFIG_STAGING` / `KUBE_CONFIG_PRODUCTION` secret dependency
- **GKE (Workload Identity)**: add `GCP_SERVICE_ACCOUNT` and `GCP_WORKLOAD_IDENTITY_PROVIDER` secrets

OIDC federation eliminates long-lived credentials entirely and is the preferred approach when
the target cluster supports it.
