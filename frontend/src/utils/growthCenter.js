export function calculateLevelProgress({ level, exp, nextLevelNeedExp, maxLevel = 10 }) {
  const currentLevel = Math.max(1, Number(level) || 1)
  const currentExp = Number(exp) || 0
  const bandEnd = Number(nextLevelNeedExp) || 0
  if (currentLevel >= maxLevel || bandEnd <= 0) return 100

  const bandStart = (currentLevel - 1) * 100
  const bandSize = bandEnd - bandStart
  if (bandSize <= 0) return currentExp >= bandEnd ? 100 : 0

  const progress = ((currentExp - bandStart) / bandSize) * 100
  return Math.round(Math.min(100, Math.max(0, progress)))
}

export function createLatestRequestGate() {
  let latestToken = 0
  return {
    begin() {
      latestToken += 1
      return latestToken
    },
    isCurrent(token) {
      return token === latestToken
    },
    invalidate() {
      latestToken += 1
    }
  }
}

export function findNewlyUnlocked(before, after) {
  const seenCodes = new Set(
    (Array.isArray(before) ? before : []).filter(item => item?.unlocked).map(item => item.code)
  )
  return (Array.isArray(after) ? after : []).filter(item => {
    if (!item?.unlocked || seenCodes.has(item.code)) return false
    seenCodes.add(item.code)
    return true
  })
}
