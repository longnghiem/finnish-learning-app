/**
 * True when `pathname` is the page `linkTo` points at, or a page below it.
 *
 * @param pathname the current path (the router location's `pathname`), without the query string
 * @param linkTo the link's path, e.g. `/admin`
 */
export function isPathActive(pathname: string, linkTo: string): boolean {
  // filter(Boolean) drops the empty strings before and after the slashes
  const pathSegments = pathname.split('/').filter(Boolean)
  const linkSegments = linkTo.split('/').filter(Boolean)
  return (
    linkSegments.length <= pathSegments.length &&
    linkSegments.every((segment, index) => segment === pathSegments[index])
  )
}
