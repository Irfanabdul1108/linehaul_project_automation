import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { loadWarehouses, useActiveWarehouseId, warehousePlace } from '../services/warehouse.js'
import WarehouseChip from './WarehouseChip.jsx'

/** Page titles. The subtitle names the depot by its PLACE, e.g. "All freight orders in Bengaluru". */
const TITLES = {
  '/': ['Dashboard', (place) => `Overview of the linehaul operation${place ? ` in ${place}` : ''}`],
  '/orders': ['Orders', (place) => `All freight orders${place ? ` in ${place}` : ' of this warehouse'}`],
  '/routes': ['Routes', (place) => `Build routes${place ? ` out of ${place}` : ''}, assign orders, trucks and drivers`],
  '/vehicles': ['Vehicles', (place) => `Trucks${place ? ` in ${place}` : ' of this warehouse'}, ready for assignment`],
  '/drivers': ['Drivers', (place) => `Drivers${place ? ` in ${place}` : ' of this warehouse'}, ready for assignment`],
}

export default function Header({ onChangeWarehouse }) {
  const { pathname } = useLocation()
  const warehouseId = useActiveWarehouseId()
  const [place, setPlace] = useState('')

  useEffect(() => {
    let alive = true
    loadWarehouses()
      .then((rows) => {
        if (alive) setPlace(warehousePlace(rows.find((row) => row.warehouseId === warehouseId)))
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [warehouseId])

  const [title, subtitle] = TITLES[pathname] || ['Linehaul', () => '']

  return (
    <header className="header">
      <div>
        <h1>{title}</h1>
        <div className="sub">{typeof subtitle === 'function' ? subtitle(place) : subtitle}</div>
      </div>
      <div className="header-right">
        <WarehouseChip warehouseId={warehouseId} onChangeWarehouse={onChangeWarehouse} />
      </div>
    </header>
  )
}
