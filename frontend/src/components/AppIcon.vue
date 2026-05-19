<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, default: 'circle' },
  size: { type: [Number, String], default: 18 },
  stroke: { type: Number, default: 1.8 }
})

const iconMap = {
  circle: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0'],
  skateboard: [
    'M4 11c0-2 2-3 4-3h8c2 0 4 1 4 3s-2 3-4 3H8c-2 0-4-1-4-3z',
    'M6 16a1.5 1.5 0 1 0 0.01 0',
    'M18 16a1.5 1.5 0 1 0 0.01 0'
  ],
  home: ['M3 10.5L12 3l9 7.5', 'M5 9.5V21h14V9.5'],
  community: ['M4 18v-9a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9', 'M8 13h8', 'M8 17h5'],
  activity: ['M5 5h14v14H5z', 'M8 3v4', 'M16 3v4', 'M8 11h8', 'M8 15h5'],
  user: ['M12 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'],
  admin: ['M12 3l8 4v5c0 4.5-3 7.5-8 9c-5-1.5-8-4.5-8-9V7l8-4z', 'M9.5 12l1.8 1.8L15 10.2'],
  login: ['M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4', 'M10 17l5-5l-5-5', 'M15 12H3'],
  logout: ['M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4', 'M14 17l5-5l-5-5', 'M19 12H9'],
  plus: ['M12 5v14', 'M5 12h14'],
  search: ['M11 19a8 8 0 1 1 0-16a8 8 0 0 1 0 16z', 'M20 20l-3.5-3.5'],
  refresh: ['M20 7v5h-5', 'M4 17v-5h5', 'M6.5 9a7 7 0 0 1 11.5-2', 'M17.5 15a7 7 0 0 1-11.5 2'],
  location: ['M12 21s7-6 7-11a7 7 0 1 0-14 0c0 5 7 11 7 11z', 'M12 12a2.5 2.5 0 1 0 0-5a2.5 2.5 0 0 0 0 5z'],
  map: ['M3 6l6-2l6 2l6-2v14l-6 2l-6-2l-6 2V6z', 'M9 4v14', 'M15 6v14'],
  comment: ['M4 5h16v10H9l-5 4V5z'],
  like: ['M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z'],
  collect: ['M6 4h12v16l-6-3l-6 3V4z'],
  notice: ['M12 3a5 5 0 0 0-5 5v3c0 .8-.3 1.5-.9 2.1L4 15h16l-2.1-1.9A3 3 0 0 1 17 11V8a5 5 0 0 0-5-5z', 'M10 19a2 2 0 0 0 4 0'],
  news: ['M5 5h14v14H5z', 'M8 9h8', 'M8 13h8', 'M8 17h5'],
  banner: ['M4 6h16v12H4z', 'M4 10h16', 'M9 14l2-2l3 3'],
  stats: ['M5 19V9', 'M12 19V5', 'M19 19v-7'],
  users: ['M9 11a3 3 0 1 0 0-6a3 3 0 0 0 0 6z', 'M17 12a2.5 2.5 0 1 0 0-5', 'M3 20a6 6 0 0 1 12 0', 'M14 20a5 5 0 0 1 7 0'],
  posts: ['M5 4h14v16H5z', 'M8 8h8', 'M8 12h8', 'M8 16h5'],
  comments: ['M4 6h16v10H9l-5 4V6z', 'M8 10h8'],
  activities: ['M6 4h12v4H6z', 'M5 8h14v12H5z', 'M8 12h8', 'M8 16h5'],
  content: ['M4 5h16v14H4z', 'M8 9h8', 'M8 13h6'],
  image: ['M4 5h16v14H4z', 'M8.5 11a1.5 1.5 0 1 0 .01 0', 'M20 15l-4-4l-6 6l-2-2l-4 4'],
  upload: ['M12 16V6', 'M8 10l4-4l4 4', 'M4 18h16']
}

const paths = computed(() => iconMap[props.name] || iconMap.circle)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      v-for="(d, idx) in paths"
      :key="`${props.name}-${idx}`"
      :d="d"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      :stroke-width="stroke"
    />
  </svg>
</template>
