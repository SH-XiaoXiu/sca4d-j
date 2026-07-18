import { Buffer } from "node:buffer";
import { createHash, generateKeyPairSync, randomBytes, sign } from "node:crypto";
import { createServer } from "node:http";
import process from "node:process";
import { URL, URLSearchParams } from "node:url";

const host = "127.0.0.1";
const port = Number(process.env.SCA4DJ_MOCK_OIDC_PORT ?? 19090);
const issuer = `http://${host}:${port}`;
const clientId = "sca4d-e2e";
const clientSecret = "sca4d-e2e-secret";
const redirectUri = "http://127.0.0.1:8087/auth/oidc/callback";
const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const publicJwk = publicKey.export({ format: "jwk" });
publicJwk.alg = "RS256";
publicJwk.kid = "sca4d-e2e-key";
publicJwk.use = "sig";
const authorizationCodes = new Map();
const accessTokens = new Set();

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

function json(response, status, body) {
  const content = Buffer.from(JSON.stringify(body));
  response.writeHead(status, {
    "Cache-Control": "no-store",
    "Content-Length": content.length,
    "Content-Type": "application/json"
  });
  response.end(content);
}

function idToken(nonce) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", kid: publicJwk.kid, typ: "JWT" }));
  const payload = base64Url(JSON.stringify({
    iss: issuer,
    sub: "sca4d-e2e-subject",
    aud: clientId,
    iat: now,
    exp: now + 300,
    nonce,
    name: "OIDC E2E User",
    preferred_username: "oidc-e2e",
    email: "oidc-e2e@example.test",
    email_verified: true
  }));
  const content = `${header}.${payload}`;
  return `${content}.${sign("RSA-SHA256", Buffer.from(content), privateKey).toString("base64url")}`;
}

const server = createServer((request, response) => {
  const url = new URL(request.url ?? "/", issuer);
  if (request.method === "GET" && url.pathname === "/.well-known/openid-configuration") {
    json(response, 200, {
      issuer,
      authorization_endpoint: `${issuer}/authorize`,
      token_endpoint: `${issuer}/token`,
      userinfo_endpoint: `${issuer}/userinfo`,
      jwks_uri: `${issuer}/jwks`,
      scopes_supported: ["openid", "profile", "email"],
      response_types_supported: ["code"],
      subject_types_supported: ["public"],
      id_token_signing_alg_values_supported: ["RS256"],
      token_endpoint_auth_methods_supported: ["client_secret_basic"],
      code_challenge_methods_supported: ["S256"]
    });
    return;
  }
  if (request.method === "GET" && url.pathname === "/authorize") {
    const state = url.searchParams.get("state");
    const nonce = url.searchParams.get("nonce");
    const challenge = url.searchParams.get("code_challenge");
    const requestedRedirectUri = url.searchParams.get("redirect_uri");
    const scopes = (url.searchParams.get("scope") ?? "").split(" ");
    if (
      url.searchParams.get("response_type") !== "code"
      || url.searchParams.get("client_id") !== clientId
      || requestedRedirectUri !== redirectUri
      || url.searchParams.get("code_challenge_method") !== "S256"
      || !scopes.includes("openid")
      || !state
      || !nonce
      || !challenge
    ) {
      json(response, 400, { error: "invalid_request" });
      return;
    }
    const code = randomBytes(32).toString("base64url");
    authorizationCodes.set(code, { nonce, challenge, redirectUri: requestedRedirectUri });
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
        expires_in: 300,
        id_token: idToken(authorization.nonce)
      });
    });
    return;
  }
  if (request.method === "GET" && url.pathname === "/jwks") {
    json(response, 200, { keys: [publicJwk] });
    return;
  }
  if (request.method === "GET" && url.pathname === "/userinfo") {
    const token = request.headers.authorization?.replace(/^Bearer /, "");
    if (!token || !accessTokens.has(token)) {
      json(response, 401, { error: "invalid_token" });
      return;
    }
    json(response, 200, {
      sub: "sca4d-e2e-subject",
      name: "OIDC E2E User",
      preferred_username: "oidc-e2e",
      email: "oidc-e2e@example.test",
      email_verified: true
    });
    return;
  }
  json(response, 404, { error: "not_found" });
});

server.listen(port, host, () => {
  process.stdout.write(`Mock OIDC Provider ready at ${issuer}\n`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
