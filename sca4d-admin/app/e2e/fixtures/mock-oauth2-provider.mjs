import { Buffer } from "node:buffer";
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:http";
import process from "node:process";
import { URL, URLSearchParams } from "node:url";

const host = "127.0.0.1";
const port = Number(process.env.SCA4DJ_MOCK_OAUTH2_PORT ?? 19091);
const issuer = `http://${host}:${port}`;
const clientId = "sca4d-oauth2-e2e";
const clientSecret = "sca4d-oauth2-e2e-secret";
const redirectUri = "http://127.0.0.1:8087/auth/oauth2/callback";
const authorizationCodes = new Map();
const accessTokens = new Set();

function json(response, status, body) {
  const content = Buffer.from(JSON.stringify(body));
  response.writeHead(status, {
    "Cache-Control": "no-store",
    "Content-Length": content.length,
    "Content-Type": "application/json"
  });
  response.end(content);
}

const server = createServer((request, response) => {
  const url = new URL(request.url ?? "/", issuer);
  if (request.method === "GET" && url.pathname === "/authorize") {
    const state = url.searchParams.get("state");
    const challenge = url.searchParams.get("code_challenge");
    const requestedRedirectUri = url.searchParams.get("redirect_uri");
    if (
      url.searchParams.get("response_type") !== "code"
      || url.searchParams.get("client_id") !== clientId
      || requestedRedirectUri !== redirectUri
      || url.searchParams.get("code_challenge_method") !== "S256"
      || !state
      || !challenge
    ) {
      json(response, 400, { error: "invalid_request" });
      return;
    }
    const code = randomBytes(32).toString("base64url");
    authorizationCodes.set(code, { challenge, redirectUri: requestedRedirectUri });
    const callback = new URL(requestedRedirectUri);
    callback.searchParams.set("code", code);
    callback.searchParams.set("state", state);
    response.writeHead(302, { "Cache-Control": "no-store", Location: callback.toString() });
    response.end();
    return;
  }
  if (request.method === "POST" && url.pathname === "/token") {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      const expectedAuthorization = `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString("base64")}`;
      const form = new URLSearchParams(Buffer.concat(chunks).toString("utf8"));
      const code = form.get("code");
      const authorization = code ? authorizationCodes.get(code) : null;
      const verifier = form.get("code_verifier") ?? "";
      const challenge = createHash("sha256").update(verifier).digest("base64url");
      if (
        request.headers.authorization !== expectedAuthorization
        || form.get("grant_type") !== "authorization_code"
        || form.get("redirect_uri") !== redirectUri
        || !authorization
        || authorization.challenge !== challenge
      ) {
        json(response, 400, { error: "invalid_grant" });
        return;
      }
      authorizationCodes.delete(code);
      const accessToken = randomBytes(32).toString("base64url");
      accessTokens.add(accessToken);
      json(response, 200, {
        access_token: accessToken,
        token_type: "Bearer",
        expires_in: 300
      });
    });
    return;
  }
  if (request.method === "GET" && url.pathname === "/profile") {
    const token = request.headers.authorization?.replace(/^Bearer /, "");
    if (!token || !accessTokens.has(token)) {
      json(response, 401, { error: "invalid_token" });
      return;
    }
    json(response, 200, {
      id: "oauth2-e2e-subject",
      name: "OAuth2 E2E User",
      email: "oauth2-e2e@example.test",
      email_verified: true
    });
    return;
  }
  json(response, 404, { error: "not_found" });
});

server.listen(port, host, () => {
  process.stdout.write(`Mock OAuth2 Provider ready at ${issuer}\n`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
