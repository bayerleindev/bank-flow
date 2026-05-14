# Security Policy

Bank Flow is a study project and is not production banking software.

## Reporting A Vulnerability

If you find a security issue, avoid publishing exploit details in a public issue. Open a minimal private report through the repository host if available, or contact the maintainer listed by the project owner.

Include:

- Affected service or script.
- Reproduction steps.
- Impact.
- Suggested fix, if known.

## Sensitive Data

Do not commit real credentials, account holder data, bank data or personally identifiable information. Examples in this repository must use mock data only.

Local defaults such as Postgres and immudb passwords are development values and must not be reused in shared or production environments.
