<template>
  <svg class="skateboard-sketch" viewBox="0 0 224 96" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
    <path
      class="sketch-deck"
      d="M18 33 C 47 44, 72 47, 104 47 L 124 47 C 158 47, 182 44, 206 33 C 209 32, 211 36, 208 38 C 184 50, 157 53, 124 53 L 103 53 C 70 53, 44 50, 16 38 C 13 37, 15 32, 18 33Z"
    />
    <path class="sketch-deck-top" d="M18 33 C 47 44, 72 47, 104 47 L 124 47 C 158 47, 182 44, 206 33" />
    <g class="sketch-truck-group">
      <path class="sketch-truck" d="M69 53 L69 58" />
      <path class="sketch-axle" d="M59 58 L79 58" />
      <circle class="sketch-wheel" cx="69" cy="66" r="8" />
      <circle class="sketch-wheel-inner" cx="69" cy="66" r="4" />
      <circle class="sketch-wheel-dot" cx="69" cy="66" r="1.6" />
    </g>
    <g class="sketch-truck-group">
      <path class="sketch-truck" d="M146 53 L146 58" />
      <path class="sketch-axle" d="M136 58 L156 58" />
      <circle class="sketch-wheel" cx="146" cy="66" r="8" />
      <circle class="sketch-wheel-inner" cx="146" cy="66" r="4" />
      <circle class="sketch-wheel-dot" cx="146" cy="66" r="1.6" />
    </g>
  </svg>
</template>

<style scoped>
.skateboard-sketch {
  display: block;
  overflow: visible;
}

.sketch-deck {
  fill: var(--sketch-deck-fill, rgba(255, 255, 255, 0.96));
  stroke: var(--sketch-line, rgba(17, 24, 39, 0.92));
  stroke-width: 2.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.sketch-deck-top {
  fill: none;
  stroke: var(--sketch-line, rgba(17, 24, 39, 0.92));
  stroke-width: 2.4;
  stroke-linecap: round;
}

.sketch-truck,
.sketch-axle {
  fill: none;
  stroke: var(--sketch-line, rgba(17, 24, 39, 0.92));
  stroke-width: 2;
  stroke-linecap: round;
}

.sketch-truck-group {
  transform-box: fill-box;
  transform-origin: center;
}

.sketch-wheel {
  fill: var(--sketch-wheel-fill, rgba(255, 255, 255, 0.96));
  stroke: var(--sketch-line, rgba(17, 24, 39, 0.92));
  stroke-width: 2.4;
}

.sketch-wheel-inner {
  fill: none;
  stroke: var(--sketch-line, rgba(17, 24, 39, 0.92));
  stroke-width: 1.8;
}

.sketch-wheel-dot {
  fill: var(--sketch-line, rgba(17, 24, 39, 0.92));
}
</style>
