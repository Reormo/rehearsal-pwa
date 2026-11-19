/** Keep client-side routing consistent with Next.js static export's trailingSlash URLs. */
export function normalizeAppPath(pathname:string):string {
  const cleaned=(pathname.split("?")[0]??pathname).split("#")[0]??pathname;
  return cleaned.replace(/\/index\.html$/,"").replace(/\.html$/,"").replace(/\/+$/,"") || "/";
}
export function isMtPath(pathname:string):boolean {
  const p=normalizeAppPath(pathname);
  return p==="/mt" || p.startsWith("/mt/");
}
export function mtBackTarget(pathname:string):string|null {
  const p=normalizeAppPath(pathname);
  if(p==="/mt" || p==="/mt/host")return "/my";
  if(p.startsWith("/mt/host/"))return "/mt/host";
  if(p.startsWith("/mt/"))return "/mt";
  return null;
}
