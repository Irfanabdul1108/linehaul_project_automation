export default function ProgressBar({ percent }) {
  const value = Math.max(0, percent || 0)
  const width = Math.min(100, value)
  const tone = value > 100 ? 'over' : value >= 80 ? 'warn' : ''

  return (
    <div>
      <div className="progress">
        <span className={tone} style={{ width: `${width}%` }} />
      </div>
    </div>
  )
}
