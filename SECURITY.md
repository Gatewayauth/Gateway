# Security Policy

## Reporting a vulnerability

**Do not open a public issue for security vulnerabilities.**

Report privately via GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability):

1. Go to the **Security** tab of this repository.
2. Click **Report a vulnerability**.
3. Fill in the advisory form with steps to reproduce and impact.

We aim to acknowledge reports within **72 hours** and to provide a remediation
timeline within **7 days**. Please give us reasonable time to fix an issue
before any public disclosure.

## Scope

In scope: authentication, session handling, token issuance/validation, MFA,
OIDC provider behaviour, and any code in this repository.

Out of scope: findings that require a compromised host or physical access,
issues in third-party dependencies without a demonstrated exploit path here,
and social-engineering attacks.

## Supported versions

The `main` branch and the latest published release receive security fixes.
Older tags are not maintained.

## Disclosure

Once a fix ships, we publish a GitHub Security Advisory crediting the reporter
(unless anonymity is requested).
