export default function HorseAvatar({ horse, photo, size = 'small' }) {
  const hasImage = photo?.status === 'ACTIVE'
    && photo.contentType?.startsWith('image/')
    && photo.secureUrl
  const initial = [...(horse?.name?.trim() || '?')][0].toLocaleUpperCase('vi-VN')

  if (size === 'large') {
    return (
      <div className="profile-media">
        {hasImage
          ? <img src={photo.secureUrl} alt={horse.name} />
          : <div className="horse-monogram">{initial}</div>}
      </div>
    )
  }

  return (
    <div className="horse-avatar-tile">
      {hasImage
        ? <img src={photo.secureUrl} alt={horse.name} />
        : <span>{initial}</span>}
    </div>
  )
}
