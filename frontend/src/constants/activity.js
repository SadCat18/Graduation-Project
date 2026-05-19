export const ACTIVITY_STATUS = {
  PENDING_REVIEW: '0',
  REVIEW_REJECTED: '1',
  SIGNUP_OPEN: '2',
  FULL: '3',
  CANCELED: '4',
  ENDED: '5'
}

export const ACTIVITY_STATUS_LABEL = {
  [ACTIVITY_STATUS.PENDING_REVIEW]: '待审核',
  [ACTIVITY_STATUS.REVIEW_REJECTED]: '审核驳回',
  [ACTIVITY_STATUS.SIGNUP_OPEN]: '报名中',
  [ACTIVITY_STATUS.FULL]: '已满员',
  [ACTIVITY_STATUS.CANCELED]: '已取消',
  [ACTIVITY_STATUS.ENDED]: '已结束'
}

export const SIGN_STATUS = {
  PENDING_CONFIRM: '0',
  APPROVED: '1',
  REJECTED: '2',
  CANCELED: '3'
}

export const SIGN_STATUS_LABEL = {
  [SIGN_STATUS.PENDING_CONFIRM]: '待确认',
  [SIGN_STATUS.APPROVED]: '已通过',
  [SIGN_STATUS.REJECTED]: '已拒绝',
  [SIGN_STATUS.CANCELED]: '已取消'
}

export function normalizeActivityStatus(raw) {
  if (raw === '0' || raw === '1') return raw === '0' ? ACTIVITY_STATUS.SIGNUP_OPEN : ACTIVITY_STATUS.ENDED
  return raw || ACTIVITY_STATUS.PENDING_REVIEW
}
