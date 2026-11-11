export const horsePortrait = (horse) => `/horses/portrait-${[...(horse?.code || horse?.horseId || horse?.id || horse?.name || '')].reduce((sum, char) => sum + char.charCodeAt(0), 0) % 2 + 1}.jpg`

export default function HorseAvatar({ horse, photo, size = 'small' }) {
  const hasImage = photo?.status === 'ACTIVE'
    && photo.contentType?.startsWith('image/')
    && photo.secureUrl
  const imageUrl = hasImage ? photo.secureUrl : horsePortrait(horse)
  const alt = hasImage ? horse.name : 'Ảnh minh họa ngựa'

  if (size === 'large') {
    return (
      <div className="profile-media">
        <img src={imageUrl} alt={alt} />
      </div>
    )
  }

  return (
    <div className="horse-avatar-tile">
      <img src={imageUrl} alt={alt} />
    </div>
  )
}
