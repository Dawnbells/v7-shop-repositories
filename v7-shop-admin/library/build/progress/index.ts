import progress from 'vite-plugin-vitebar'

export const createProgress = (env: Record<string, string>) => {
  const projectName = 'XYZ Mall'
  return progress({ env, projectName })
}
