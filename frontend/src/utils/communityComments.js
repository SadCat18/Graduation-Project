export function normalizeCommentThreads(items) {
  if (!Array.isArray(items)) return []
  const roots = []
  const repliesByParent = new Map()

  for (const item of items) {
    if (!item) continue
    const parentId = Number(item.parentId || 0)
    const normalized = {
      ...item,
      replies: Array.isArray(item.replies) ? [...item.replies] : []
    }
    if (!parentId) {
      roots.push(normalized)
    } else {
      if (!repliesByParent.has(parentId)) repliesByParent.set(parentId, [])
      repliesByParent.get(parentId).push(normalized)
    }
  }

  return roots.map(root => ({
    ...root,
    replies: [...(root.replies || []), ...(repliesByParent.get(Number(root.commentId)) || [])]
  }))
}
