import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { loadEnvFile } from "node:process";

const workspace = resolve(import.meta.dirname, "..");
const environmentFile = resolve(workspace, "../.env");
if (existsSync(environmentFile)) {
  loadEnvFile(environmentFile);
}
const generated = resolve(workspace, "common/src/generated/openapi.ts");
const temporary = mkdtempSync(join(tmpdir(), "sca4d-openapi-"));
const candidate = join(temporary, "openapi.ts");
const source = process.env.SCA4D_OPENAPI_URL ?? "http://localhost:8080/v3/api-docs";

try {
  execFileSync(
    "pnpm",
    ["--filter", "@sca4d/admin-common", "exec", "openapi-typescript", source, "-o", candidate],
    {
      cwd: workspace,
      stdio: "inherit"
    }
  );
  if (readFileSync(generated, "utf8") !== readFileSync(candidate, "utf8")) {
    throw new Error("OpenAPI 类型已漂移，请运行 pnpm api:generate 并提交结果");
  }
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
