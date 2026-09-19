import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve, sep } from 'node:path';
import process from 'node:process';

/**
 * 把 ESLint JSON 中的每条消息转换为 `QualityGateTask` 接受的通用 finding。
 * 输入文件路径由当前前端项目的 Gradle 映射声明，不扫描 Vben 上游包。
 */
const [projectPrefix, outputPath, ...inputPaths] = process.argv.slice(2);
const findings = [];

for (const inputPath of inputPaths) {
  const results = JSON.parse(await readFile(inputPath, 'utf8'));
  for (const result of results) {
    const sourcePath = relative(process.cwd(), resolve(result.filePath))
      .split(sep)
      .join('/');
    const repositoryPath = [projectPrefix, sourcePath]
      .filter(Boolean)
      .join('/');
    for (const message of result.messages) {
      findings.push({
        ruleId: `ESLINT-${message.ruleId ?? 'FATAL'}`,
        severity: message.severity === 2 ? 'ERROR' : 'WARNING',
        path: repositoryPath,
        line: message.line ?? 0,
        message: message.message,
      });
    }
  }
}

findings.sort((left, right) =>
  [left.severity, left.ruleId, left.path, left.line, left.message]
    .join('\0')
    .localeCompare(
      [
        right.severity,
        right.ruleId,
        right.path,
        right.line,
        right.message,
      ].join('\0'),
    ),
);

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, `${JSON.stringify(findings, null, 2)}\n`, 'utf8');
