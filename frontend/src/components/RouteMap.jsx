export default function RouteMap({ origin, destination, hours, eta }) {
  return (
    <div className="map">
      <div className="node">{origin}</div>
      <div className="line">
        <span>
          {hours} h{eta ? ` - ETA ${eta}` : ''}
        </span>
      </div>
      <div className="node">{destination}</div>
    </div>
  )
}
