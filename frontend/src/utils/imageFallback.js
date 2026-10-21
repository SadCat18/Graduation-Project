export function localImageFallback(event, fallback) {
  const image = event.currentTarget
  if (image.dataset.fallbackUsed === 'true' || image.getAttribute('src') === fallback) {
    image.hidden = true
    return
  }
  image.dataset.fallbackUsed = 'true'
  image.src = fallback
}
