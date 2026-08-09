# IMS frontend

Responsive React, TypeScript, and Vite SPA for the Insurance Management System.

## Local development

```bash
cp .env.example .env
npm install
npm run dev
```

The frontend expects the Spring JSON REST API. Set `VITE_API_BASE_URL` to its `/api` base URL. Authentication consumes `{ user, accessToken, refreshToken, expiresIn }`. Both tokens are intentionally kept in memory rather than browser storage. Expired access tokens are refreshed once and replayed; a full page reload requires sign-in again.

## API contract

- `POST /auth/login`, `POST /auth/register`, `POST /auth/refresh`, `POST /auth/logout`
- `GET /users/me`, `GET /users/customers`
- `GET/POST /plans`, `PUT/DELETE /plans/:id`
- `GET/POST /policies`
- `GET/POST /claims`, `PATCH /claims/:id/status`

Protected requests send `Authorization: Bearer <accessToken>`. Spring problem-detail errors are displayed from `detail`, with `message` and `error` as fallbacks. Claim transitions follow `SUBMITTED → UNDER_REVIEW → APPROVED → PAID`, with `UNDER_REVIEW → REJECTED` as the rejection path.

## GitHub Pages

The app uses hash routing, which works on static GitHub Pages without a custom 404 redirect. Configure the repository path when building:

```bash
VITE_APP_BASE=/repository-name/ VITE_APP_BASENAME=/ npm run build
```

`VITE_APP_BASE` controls Vite asset URLs. `VITE_APP_BASENAME` optionally prefixes routes inside the URL hash. For a custom domain, leave both as `/`.

## Quality checks

```bash
npm run lint
npm test
npm run build
```
