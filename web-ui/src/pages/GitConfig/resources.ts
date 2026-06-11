// Groups changed files into the Ignition config resources they belong to — a resource is the
// directory holding resource.json (or a single unary-resource.json) plus its data files — so
// lists show impacted resources rather than a flat file listing. Shared by the uncommitted
// Changes panel and the History commit expand.

export interface ChangedFile {
  path: string;
  type: string; // status (ADDED/MODIFIED/DELETED/UNTRACKED) or commit (ADD/MODIFY/DELETE/…) vocab
}

export interface ResourceGroup {
  resource: string; // resource directory, e.g. "config/resources/core/.../device/TestSimulator"
  changeType: string; // aggregated, in the same vocabulary as the input types
  files: ChangedFile[]; // member files
  diffPath: string; // representative data file shown when the resource is clicked
}

const isMeta = (p: string) =>
  p.endsWith("/resource.json") ||
  p.endsWith("/unary-resource.json") ||
  p.endsWith("/thumbnail.png");

export const groupByResource = (files: ChangedFile[]): ResourceGroup[] => {
  const byDir = new Map<string, ChangedFile[]>();
  files.forEach((f) => {
    const slash = f.path.lastIndexOf("/");
    const dir = slash >= 0 ? f.path.slice(0, slash) : f.path;
    const list = byDir.get(dir) || [];
    list.push(f);
    byDir.set(dir, list);
  });

  const out: ResourceGroup[] = [];
  byDir.forEach((group, dir) => {
    // Mixed member types: the resource manifest's own change decides (manifest added/deleted
    // means the whole resource was), falling back to the first member's type.
    const types = new Set(group.map((g) => g.type.toUpperCase()));
    let changeType = group[0].type.toUpperCase();
    if (types.size > 1) {
      const meta = group.find((g) => isMeta(g.path));
      if (meta) {
        changeType = meta.type.toUpperCase();
      }
    }
    const data = group.find((g) => !isMeta(g.path)) || group[0];
    out.push({ resource: dir, changeType, files: group, diffPath: data.path });
  });

  out.sort((a, b) => a.resource.localeCompare(b.resource));
  return out;
};

export const displayName = (resource: string) =>
  resource.replace(/^config\//, "");
