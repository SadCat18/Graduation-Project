<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import { api } from '../api'
import { ACTIVITY_STATUS, ACTIVITY_STATUS_LABEL, SIGN_STATUS_LABEL, normalizeActivityStatus } from '../constants/activity'
import { getRole } from '../utils/auth'
import { normalizeMediaUrl } from '../utils/url'

const profile = reactive({
  username: '',
  avatar: '',
  gender: '',
  skateStyle: '',
  phone: '',
  bio: ''
})

const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const videoForm = reactive({ title: '', cover: '', url: '', intro: '' })

const messages = ref([])
const dashboard = ref({})
const videos = ref([])
const signedActivities = ref([])
const role = getRole()

const avatarFileInputRef = ref(null)
const selectedAvatarFile = ref(null)
const avatarPreviewUrl = ref('')
const savingProfile = ref(false)

const videoFileInputRef = ref(null)
const coverFileInputRef = ref(null)
const selectedVideoFile = ref(null)
const selectedCoverFile = ref(null)
const videoUploading = ref(false)

function sanitizePhone(value) {
  const phone = String(value || '').trim()
  return /^1\d{10}$/.test(phone) ? phone : ''
}

function normalizeGender(value) {
  return value === '男' || value === '女' ? value : ''
}

function handleAvatarError() {
  profile.avatar = ''
}

async function loadProfile() {
  const user = await api.profile()
  profile.username = user?.username || ''
  profile.avatar = normalizeMediaUrl(user?.avatar)
  profile.gender = normalizeGender(user?.gender)
  profile.skateStyle = user?.skateStyle || ''
  profile.phone = sanitizePhone(user?.phone)
  profile.bio = user?.bio || ''
}

async function loadOthers() {
  const [msg, board, videoList, signedList] = await Promise.all([
    api.messages(),
    api.dashboard(),
    api.videos(),
    api.mySignedActivities()
  ])
  messages.value = msg || []
  dashboard.value = board || {}
  videos.value = videoList || []
  signedActivities.value = signedList || []
}

async function saveProfile() {
  const phone = String(profile.phone || '').trim()
  if (phone && !/^1\d{10}$/.test(phone)) {
    alert('鎵嬫満鍙锋牸寮忎笉姝ｇ‘锛岃杈撳叆 11 浣嶆墜鏈哄彿')
    return
  }

  savingProfile.value = true
  try {
    if (selectedAvatarFile.value) {
      const avatarUrl = await api.uploadPostImage(selectedAvatarFile.value)
      profile.avatar = normalizeMediaUrl(avatarUrl)
      clearSelectedAvatar()
    }

    await api.updateProfile({
      avatar: profile.avatar || null,
      gender: profile.gender || null,
      skateStyle: profile.skateStyle?.trim() || null,
      phone: phone || null,
      bio: profile.bio?.trim() || null
    })

    await loadProfile()
    alert('个人资料已保存')
  } catch (e) {
    alert(e?.message || '淇濆瓨澶辫触锛岃绋嶅悗閲嶈瘯')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  await api.updatePassword(passwordForm)
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  alert('密码已更新')
}

async function markRead(id) {
  await api.readMessage(id)
  await loadOthers()
}

function triggerAvatarSelect() {
  avatarFileInputRef.value?.click()
}

function clearSelectedAvatar() {
  if (avatarPreviewUrl.value) {
    URL.revokeObjectURL(avatarPreviewUrl.value)
  }
  avatarPreviewUrl.value = ''
  selectedAvatarFile.value = null
  if (avatarFileInputRef.value) avatarFileInputRef.value.value = ''
}

function onSelectAvatar(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('璇烽€夋嫨鍥剧墖鏂囦欢')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    alert('澶村儚鍥剧墖涓嶈兘瓒呰繃 10MB')
    return
  }

  clearSelectedAvatar()
  selectedAvatarFile.value = file
  avatarPreviewUrl.value = URL.createObjectURL(file)
}

function triggerVideoSelect() {
  videoFileInputRef.value?.click()
}

function triggerCoverSelect() {
  coverFileInputRef.value?.click()
}

function onSelectVideo(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('video/')) {
    alert('璇烽€夋嫨瑙嗛鏂囦欢')
    return
  }
  if (file.size > 300 * 1024 * 1024) {
    alert('瑙嗛澶у皬涓嶈兘瓒呰繃 300MB')
    return
  }
  selectedVideoFile.value = file
  if (videoFileInputRef.value) videoFileInputRef.value.value = ''
}

function onSelectCover(event) {
  const file = (event.target.files || [])[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('璇烽€夋嫨鍥剧墖鏂囦欢')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    alert('灏侀潰鍥剧墖涓嶈兘瓒呰繃 10MB')
    return
  }
  selectedCoverFile.value = file
  if (coverFileInputRef.value) coverFileInputRef.value.value = ''
}

function clearVideoSelection() {
  selectedVideoFile.value = null
}

function clearCoverSelection() {
  selectedCoverFile.value = null
}

async function createVideo() {
  if (!videoForm.title.trim()) {
    alert('请填写视频标题')
    return
  }
  if (!selectedVideoFile.value && !videoForm.url.trim()) {
    alert('璇蜂笂浼犳湰鍦拌棰戞枃浠舵垨濉啓瑙嗛鍦板潃')
    return
  }

  videoUploading.value = true
  try {
    let finalVideoUrl = videoForm.url.trim()
    let finalCoverUrl = videoForm.cover.trim()

    if (selectedVideoFile.value) {
      finalVideoUrl = await api.uploadVideoFile(selectedVideoFile.value)
    }
    if (selectedCoverFile.value) {
      finalCoverUrl = await api.uploadPostImage(selectedCoverFile.value)
    }

    await api.createVideo({
      title: videoForm.title.trim(),
      url: normalizeMediaUrl(finalVideoUrl),
      cover: normalizeMediaUrl(finalCoverUrl),
      intro: videoForm.intro.trim()
    })

    videoForm.title = ''
    videoForm.url = ''
    videoForm.cover = ''
    videoForm.intro = ''
    selectedVideoFile.value = null
    selectedCoverFile.value = null
    await loadOthers()
    alert('瑙嗛鎻愪氦鎴愬姛')
  } catch (e) {
    alert(e?.message || '瑙嗛鎻愪氦澶辫触锛岃绋嶅悗閲嶈瘯')
  } finally {
    videoUploading.value = false
  }
}

function formatActivityTime(value) {
  if (!value) return '未设置'
  return String(value).replace('T', ' ')
}

function signedAddress(item) {
  return [item.city, item.district, item.place, item.address].filter(Boolean).join(' 路 ') || '未设置地点'
}

function signStatusLabel(item) {
  return SIGN_STATUS_LABEL[item.signStatus] || '未知'
}

function activityStatusLabel(item) {
  const status = normalizeActivityStatus(item.activityStatus ?? item.status)
  return ACTIVITY_STATUS_LABEL[status] || '未知'
}

async function checkinSignedActivity(item) {
  try {
    await api.checkinActivity(item.activityId)
    await loadOthers()
    alert('签到成功')
  } catch (e) {
    alert(e?.message || '签到失败，请稍后重试')
  }
}

async function cancelSignedActivity(item) {
  try {
    await api.cancelSignActivity(item.activityId)
    await loadOthers()
    alert('已退出该活动报名')
  } catch (e) {
    alert(e?.message || '退出失败，请稍后重试')
  }
}

function canCancelSignedActivity(item) {
  if (!(item.signStatus === '0' || item.signStatus === '1')) return false
  const activityStatus = normalizeActivityStatus(item.activityStatus ?? item.status)
  return activityStatus !== ACTIVITY_STATUS.CANCELED && activityStatus !== ACTIVITY_STATUS.ENDED
}

onMounted(async () => {
  if (role !== 'ADMIN') {
    await loadProfile()
    await loadOthers()
  }
})

onBeforeUnmount(() => {
  clearSelectedAvatar()
})
</script>

<template>
  <div v-if="role !== 'ADMIN'" class="page-grid profile-page">
    <section>
      <div class="card">
        <div class="section-head"><h3>涓汉璧勬枡</h3></div>

        <div class="avatar-upload-block">
          <div class="avatar-preview">
            <img
              v-if="avatarPreviewUrl || profile.avatar"
              :src="avatarPreviewUrl || profile.avatar"
              alt="澶村儚棰勮"
              @error="handleAvatarError"
            />
            <span v-else>鏆傛棤澶村儚</span>
          </div>

          <input
            ref="avatarFileInputRef"
            class="hidden-file-input"
            type="file"
            accept="image/*"
            @change="onSelectAvatar"
          />

          <div class="inline">
            <button type="button" class="btn-soft" @click="triggerAvatarSelect">
              <AppIcon name="upload" :size="15" />
              閫夋嫨鏈湴澶村儚
            </button>
            <button v-if="selectedAvatarFile" type="button" @click="clearSelectedAvatar">鍙栨秷閫夋嫨</button>
          </div>
          <p class="muted">仅支持本地图片，保存资料时自动上传。</p>
        </div>

        <div class="gender-row">
          <span class="field-title">鎬у埆</span>
          <label class="radio-option">
            <input v-model="profile.gender" type="radio" value="男" />
            <span>男</span>
          </label>
          <label class="radio-option">
            <input v-model="profile.gender" type="radio" value="女" />
            <span>女</span>
          </label>
        </div>

        <div class="form-grid">
          <input v-model="profile.skateStyle" placeholder="婊戞澘椋庢牸锛堣寮?纰楁睜绛夛級" />
          <input
            v-model="profile.phone"
            maxlength="11"
            inputmode="numeric"
            placeholder="鎵嬫満鍙凤紙11浣嶏級"
          />
        </div>

        <textarea v-model="profile.bio" placeholder="个人简介" />
        <button class="btn-primary" :disabled="savingProfile" @click="saveProfile">
          {{ savingProfile ? '淇濆瓨涓?..' : '淇濆瓨璧勬枡' }}
        </button>
      </div>

      <div class="card">
        <div class="section-head"><h3>淇敼瀵嗙爜</h3></div>
        <div class="form-grid">
          <input v-model="passwordForm.oldPassword" type="password" placeholder="旧密码" />
          <input v-model="passwordForm.newPassword" type="password" placeholder="新密码" />
        </div>
        <button @click="savePassword">鏇存柊瀵嗙爜</button>
      </div>

      <div class="card">
        <div class="section-head"><h3>涓婁紶鏁欏瑙嗛</h3></div>
        <div class="form-grid">
          <input v-model="videoForm.title" placeholder="瑙嗛鏍囬锛堝繀濉級" />
          <input v-model="videoForm.url" placeholder="视频地址 URL（可选，不上传本地视频时填写）" />
        </div>

        <div class="upload-row">
          <input
            ref="videoFileInputRef"
            class="hidden-file-input"
            type="file"
            accept="video/*"
            @change="onSelectVideo"
          />
          <button type="button" class="btn-soft" @click="triggerVideoSelect">
            <AppIcon name="upload" :size="15" />
            閫夋嫨鏈湴瑙嗛
          </button>
          <span class="file-name" v-if="selectedVideoFile">{{ selectedVideoFile.name }}</span>
          <button v-if="selectedVideoFile" type="button" @click="clearVideoSelection">鍙栨秷</button>
        </div>
        <p class="muted">支持本地视频上传（推荐 mp4），最大 300MB。</p>

        <div class="form-grid">
          <input v-model="videoForm.cover" placeholder="灏侀潰 URL锛堝彲閫夛級" />
          <div class="upload-row">
            <input
              ref="coverFileInputRef"
              class="hidden-file-input"
              type="file"
              accept="image/*"
              @change="onSelectCover"
            />
            <button type="button" class="btn-soft" @click="triggerCoverSelect">
              <AppIcon name="upload" :size="15" />
              閫夋嫨鏈湴灏侀潰
            </button>
            <span class="file-name" v-if="selectedCoverFile">{{ selectedCoverFile.name }}</span>
            <button v-if="selectedCoverFile" type="button" @click="clearCoverSelection">鍙栨秷</button>
          </div>
        </div>

        <textarea v-model="videoForm.intro" placeholder="视频简介" />
        <button class="btn-primary" :disabled="videoUploading" @click="createVideo">
          {{ videoUploading ? '涓婁紶骞舵彁浜や腑...' : '鎻愪氦瑙嗛' }}
        </button>
      </div>

      <div class="card">
        <div class="section-head"><h3>我已报名活动</h3></div>
        <div v-if="!signedActivities.length" class="empty-tip">暂未报名活动，快去同城约板看看吧。</div>
        <div v-for="item in signedActivities" :key="item.signId || item.activityId" class="signed-item">
          <div class="signed-top">
            <strong>{{ item.title }}</strong>
            <span class="tag">{{ item.isCheckin === '1' ? '已签到' : '未签到' }}</span>
          </div>
          <p class="muted">活动时间：{{ formatActivityTime(item.activityTime) }}</p>
          <p class="muted">活动地点：{{ signedAddress(item) }}</p>
          <p class="muted">活动状态：{{ activityStatusLabel(item) }}</p>
          <p class="muted">报名状态：{{ signStatusLabel(item) }}</p>
          <p class="muted">报名时间：{{ formatActivityTime(item.signTime) }}</p>
          <div class="inline">
            <button class="btn-primary" :disabled="item.isCheckin === '1' || item.signStatus !== '1'" @click="checkinSignedActivity(item)">
              {{ item.isCheckin === '1' ? '已签到' : '去签到' }}
            </button>
            <button class="btn-soft" :disabled="!canCancelSignedActivity(item)" @click="cancelSignedActivity(item)">
              退出报名
            </button>
          </div>
        </div>
      </div>
    </section>

    <aside>
      <div class="card">
        <div class="section-head"><h3>鎴戠殑姒傝</h3></div>
        <div class="stats-box">
          <div><span>鎴戠殑甯栧瓙</span><strong>{{ dashboard.postCount || 0 }}</strong></div>
          <div><span>鎴戠殑娲诲姩</span><strong>{{ dashboard.activityCount || 0 }}</strong></div>
          <div><span>鏈娑堟伅</span><strong>{{ dashboard.unreadMsgCount || 0 }}</strong></div>
        </div>
      </div>

      <div class="card">
        <div class="section-head"><h3>鎴戠殑娑堟伅</h3></div>
        <div v-for="m in messages" :key="m.msgId" class="list-item">
          <p>{{ m.content }}</p>
          <p class="muted">{{ m.createTime?.replace('T', ' ') }}</p>
          <button v-if="m.isRead === '0'" class="btn-soft" @click="markRead(m.msgId)">鏍囪宸茶</button>
        </div>
      </div>

      <div class="card">
        <div class="section-head"><h3>鏁欏瑙嗛</h3></div>
        <div v-for="v in videos" :key="v.videoId" class="list-item">
          <strong>{{ v.title }}</strong>
          <p class="muted">{{ v.intro }}</p>
        </div>
      </div>
    </aside>
  </div>

  <div v-else class="card">
    <p>管理员请前往后台管理页维护信息。</p>
  </div>
</template>

<style scoped>
.profile-page {
  align-items: start;
}

.form-grid {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.avatar-upload-block {
  margin-bottom: 12px;
}

.avatar-preview {
  width: 92px;
  height: 92px;
  border-radius: 2px;
  border: 2px solid var(--line-strong);
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
  display: grid;
  place-items: center;
  overflow: hidden;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 12px;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hidden-file-input {
  display: none;
}

.gender-row {
  display: flex;
  align-items: center;
  gap: 14px;
  border: 2px solid var(--line-strong);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  margin-bottom: 10px;
}

.field-title {
  color: var(--text-soft);
  font-size: 14px;
}

.radio-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-2);
  cursor: pointer;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.file-name {
  font-size: 13px;
  color: var(--text-soft);
}

.stats-box {
  display: grid;
  gap: 8px;
}

.stats-box div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 1px dashed rgba(166, 255, 60, 0.55);
  padding-bottom: 9px;
}

.stats-box div:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.stats-box span {
  color: var(--text-soft);
}

.stats-box strong {
  font-size: 22px;
}

.empty-tip {
  color: var(--text-muted);
  border: 2px dashed var(--line-strong);
  border-radius: 2px;
  padding: 12px;
}

.signed-item {
  border: 2px solid var(--line-strong);
  border-radius: 2px;
  padding: 12px;
  margin-top: 10px;
  background: linear-gradient(180deg, #ffffff 0%, #edf4ff 100%);
}

.signed-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
</style>


