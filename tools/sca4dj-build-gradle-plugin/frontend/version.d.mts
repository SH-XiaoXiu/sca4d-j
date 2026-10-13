export interface BuildManifest {
  readonly product: string;
  readonly releaseVersion: string;
  readonly releaseId: string;
  readonly component: string;
  readonly componentBuildId: string;
  readonly sourceRevision: string;
  readonly builtAt: string;
  readonly buildMode: string;
  readonly buildVersion: string;
  readonly apiContractVersion: number;
}
export function buildVersion(
  repository: string,
  component: string,
): BuildManifest;
export function versionPlugin(manifest: BuildManifest): {
  name: string;
  config(): { define: Record<string, string> };
  generateBundle(this: {
    emitFile(file: { type: 'asset'; fileName: string; source: string }): void;
  }): void;
};
