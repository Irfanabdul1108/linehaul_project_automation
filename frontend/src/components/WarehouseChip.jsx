import { useEffect, useState } from 'react'
import { loadWarehouses, warehouseDesignation, warehousePlace } from '../services/warehouse.js'

/**
 * Which depot is on screen, and the way back to the picker.
 *
 * The place ("Bengaluru") is what is read at a glance, because that is the name every lane, route and
 * order on the screens below uses. The designation ("Warehouse A") is kept next to it, quietly, for
 * the one job it has: telling the operator which entry of the switcher they are in.
 */
export default function WarehouseChip({ warehouseId, onChangeWarehouse }) {
  const [warehouses, setWarehouses] = useState([])

  useEffect(() => {
    let alive = true
    loadWarehouses()
      .then((rows) => {
        if (alive) setWarehouses(rows)
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [])

  const current = warehouses.find((row) => row.warehouseId === warehouseId)
  const place = warehousePlace(current) || warehouseId || 'no warehouse'
  const designation = warehouseDesignation(current)

  return (
    <div className="wh-chip">
      <div className="wh-chip-text">
        <span className="wh-chip-label">Warehouse</span>
        <strong data-testid="chip-place">{place}</strong>
        {designation && designation !== place ? (
          <span className="wh-chip-designation">{designation}</span>
        ) : null}
      </div>
      <button type="button" className="btn btn-sm" onClick={onChangeWarehouse} data-testid="change-warehouse">
        Switch
      </button>
    </div>
  )
}
