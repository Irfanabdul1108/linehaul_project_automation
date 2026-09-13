import StatusBadge from './StatusBadge.jsx'
import ProgressBar from './ProgressBar.jsx'

const MATCH_COPY = {
  EXACT: 'Exact lane match',
  DESTINATION: 'Same destination',
  STOP: 'Destination on the way',
}

function Check({ ok, children }) {
  return (
    <li className={ok ? 'tick ok' : 'tick no'}>
      <span aria-hidden>{ok ? '✓' : '✕'}</span>
      {children}
    </li>
  )
}

function Fact({ label, children }) {
  return (
    <div className="kv">
      <span className="k">{label}</span>
      <span>{children}</span>
    </div>
  )
}

/**
 * One recommended route: the lane it runs, why it fits, the numbers that were checked, and what is
 * still missing. Everything on this card came from the API - the capacity bar and the ETA are the
 * values the backend validated, not something the browser recalculated.
 */
export default function RecommendationCard({
  candidate,
  recommended,
  selected,
  onSelect,
  onAssign,
  busy,
  showDetails,
  warehouseName,
}) {
  const percent = Math.min(100, candidate.fillPercentAfter || 0)
  const over = candidate.fillPercentAfter > 100

  return (
    <article
      className={`rec-card${recommended ? ' best' : ''}${selected ? ' selected' : ''}`}
      data-testid={`rec-${candidate.routeId}`}
    >
      <header className="rec-head">
        <div className="rec-rank">
          <span className="rec-rank-no">#{candidate.rank}</span>
          {recommended ? <span className="rec-star">⭐ RECOMMENDED</span> : null}
        </div>
        <div className="rec-title">
          <strong>{candidate.routeId}</strong>
          <span className="rec-lane">
            {candidate.stopSequence?.length
              ? candidate.stopSequence.map((stop, index) => (
                  <span
                    key={`${stop}-${index}`}
                    className={`lane-stop${index === candidate.pickupIndex ? ' load-here' : ''}${
                      index === candidate.dropoffIndex ? ' drop-here' : ''
                    }`}
                  >
                    {stop}
                  </span>
                ))
              : `${candidate.origin} → ${candidate.destination}`}
          </span>
        </div>
        <div className="rec-score">
          <span className={`score-pill score-${candidate.score >= 80 ? 'high' : candidate.score >= 55 ? 'mid' : 'low'}`}>
            {candidate.score}
            <small>/100 fit</small>
          </span>
          <span className="rec-warehouse">
            {candidate.crossWarehouse
              ? `cross-warehouse · ${candidate.warehouseName || candidate.warehouseId}`
              : `${warehouseName || 'this warehouse'} route`}
          </span>
        </div>
      </header>

      <div className="rec-body">
        <div className="rec-facts">
          <Fact label="Match">
            <span className={`badge badge-${candidate.matchType === 'EXACT' ? 'green' : 'blue'}`}>
              {MATCH_COPY[candidate.matchType] || candidate.matchType}
            </span>
          </Fact>
          <Fact label="Load">
            {candidate.currentWeight.toLocaleString()} → <strong>{candidate.loadAfter.toLocaleString()}</strong> of{' '}
            {candidate.maxCapacity.toLocaleString()} kg
          </Fact>
          <Fact label="Capacity left">{candidate.availableCapacity.toLocaleString()} kg</Fact>
          <Fact label="Departure">{candidate.departureTime || '-'}</Fact>
          <Fact label="ETA for this order">
            {candidate.etaKnown ? (
              <>
                {candidate.orderEta} <span className="muted">({candidate.travelTime} on board)</span>
              </>
            ) : (
              <span className="muted">no ETA on this route</span>
            )}
          </Fact>
          <Fact label="Truck">
            {candidate.truckId
              ? `${candidate.truckId}${candidate.truckType ? ` - ${candidate.truckType}` : ''}${
                  candidate.truckCapacity ? ` (${candidate.truckCapacity.toLocaleString()} kg)` : ''
                }`
              : 'not assigned yet'}
          </Fact>
          <Fact label="Driver">
            {candidate.driverId
              ? `${candidate.driverId}${candidate.driverName ? ` - ${candidate.driverName}` : ''}`
              : 'not assigned yet'}
          </Fact>
          <Fact label="Route status">
            <StatusBadge status={candidate.status} />
          </Fact>
        </div>

        <div className="rec-side">
          <div>
            <ProgressBar percent={percent} />
            <div className="progress-label">
              <span>After loading this order</span>
              <span className={over ? 'text-red' : ''}>{candidate.fillPercentAfter}%</span>
            </div>
          </div>

          <ul className="rec-checks">
            <Check ok>destination matched ({candidate.origin} → {candidate.destination})</Check>
            <Check ok>valid stop: loads at {candidate.pickupLabel}</Check>
            <Check ok>capacity available: {candidate.availableCapacity.toLocaleString()} kg free</Check>
            <Check ok={!!candidate.truckCapacity}>
              {candidate.truckCapacity
                ? `vehicle capacity ok: ${candidate.truckId} takes ${candidate.truckCapacity.toLocaleString()} kg`
                : 'no truck on this route yet - its limit is checked when one is picked'}
            </Check>
            <Check ok={candidate.etaKnown}>
              {candidate.etaKnown
                ? `ETA ${candidate.orderEta} (${candidate.travelTime} on board)`
                : 'no ETA on this route, so it could not be compared'}
            </Check>
          </ul>

          {showDetails ? (
            <div className="rec-details">
              <h4>Why this score</h4>
              <table className="mini-table">
                <tbody>
                  {(candidate.scoreBreakdown || []).map((factor) => (
                    <tr key={factor.label}>
                      <td>{factor.label}</td>
                      <td className={`num${factor.points < 0 ? ' text-red' : ''}`}>
                        {factor.points > 0 ? '+' : ''}
                        {factor.points}
                      </td>
                      <td className="muted">{factor.detail}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </div>
      </div>

      {(candidate.warnings || []).length > 0 ? (
        <ul className="rec-warnings">
          {candidate.warnings.map((warning) => (
            <li key={warning}>⚠ {warning}</li>
          ))}
        </ul>
      ) : null}

      {candidate.aiReason ? (
        <div className="rec-ai">
          <span className="ai-tag">Gemini</span>
          {candidate.aiReason}
        </div>
      ) : null}

      <footer className="rec-foot">
        <button type="button" className="btn btn-sm" onClick={() => onSelect(candidate)} disabled={selected}>
          {selected ? 'Selected' : 'Select this route'}
        </button>
        <button
          type="button"
          className={`btn btn-sm ${recommended ? 'btn-green' : 'btn-primary'}`}
          onClick={() => onAssign(candidate)}
          disabled={busy}
          data-testid={`assign-${candidate.routeId}`}
        >
          {candidate.crossWarehouse ? 'Add order to this route' : 'Assign order to this route'}
        </button>
      </footer>
    </article>
  )
}
