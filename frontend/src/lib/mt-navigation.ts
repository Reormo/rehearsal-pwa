/** Parent destinations for the single common-header back button on MT routes. */
export function isMtPath(pathname:string):boolean {
  return pathname==="/mt" || pathname.startsWith("/mt/");
}
export function mtBackTarget(pathname:string):string|null {
  if(pathname==="/mt" || pathname==="/mt/host")return "/my";
  if(pathname.startsWith("/mt/host/"))return "/mt/host";
  if(pathname.startsWith("/mt/"))return "/mt";
  return null;
}
