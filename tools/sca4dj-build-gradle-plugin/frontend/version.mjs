import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/** 读取 Gradle 清单；独立 Vite 构建先调用同一个 Gradle 生成任务。 */
export function buildVersion(repository, component) {
  let file = process.env.SCA4DJ_BUILD_MANIFEST;
  if (!file) {
    execFileSync(
      resolve(repository, 'gradlew'),
      [
        ':generateBuildManifest',
        '--console=plain',
        '--no-daemon',
        '--max-workers=1',
      ],
      {
        cwd: repository,
        stdio: 'pipe',
        timeout: 180000,
      },
    );
    file = resolve(repository, 'build/version/build-manifest.json');
  }
  const source = JSON.parse(readFileSync(file, 'utf8'));
  for (const field of [
    'product',
    'releaseVersion',
    'releaseId',
    'sourceRevision',
    'buildMode',
    'buildVersion',
    'builtAt',
  ]) {
    if (typeof source[field] !== 'string' || !source[field])
      throw new Error(`Build manifest missing ${field}`);
  }
  if (
    !Number.isInteger(source.apiContractVersion) ||
    source.apiContractVersion <= 0
  )
    throw new Error('Invalid API contract');
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd: repository,
    encoding: 'utf8',
  }).trim();
  if (revision !== source.sourceRevision)
    throw new Error('Build manifest belongs to a different source revision');
  if (
    process.env.CI_PIPELINE_ID &&
    source.releaseId !==
      (process.env.SCA4DJ_RELEASE_ID || process.env.CI_PIPELINE_ID)
  ) {
    throw new Error('Build manifest belongs to a different release');
  }
  const builtAt = new Date().toISOString();
  return Object.freeze({
    ...source,
    component,
    builtAt,
    componentBuildId: `${source.releaseId}.${component}.${revision}.${builtAt.replace(/[^0-9]/gu, '')}`,
  });
}

/** 在 JS 和静态发布目录中保存相同清单；不依赖部署配置改写版本。 */
export function versionPlugin(manifest) {
  return {
    name: 'sca4dj-build-version',
    config() {
      return { define: { __BUILD_MANIFEST__: JSON.stringify(manifest) } };
    },
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'build-manifest.json',
        source: JSON.stringify(manifest, null, 2),
      });
    },
  };
}
