import { NavLink } from 'react-router-dom'
import {
  useActiveWarehouseId,
  loadWarehouses,
  warehouseDesignation,
  warehousePlace,
} from '../services/warehouse.js'
import { useEffect, useState } from 'react'

const LINKS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/orders', label: 'Orders' },
  { to: '/routes', label: 'Routes' },
  { to: '/vehicles', label: 'Vehicles' },
  { to: '/drivers', label: 'Drivers' },
]

export default function Sidebar() {
  const warehouseId = useActiveWarehouseId()
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

  return (
    <aside className="sidebar">
      <div className="brand">
        Linehaul
        <span>Management System</span>
      </div>
      {current ? (
        <div className="sidebar-warehouse" data-testid="sidebar-warehouse">
          <span>Working in</span>
          {/* the place first: it is the name the orders and routes below are written with */}
          <strong>{warehousePlace(current)}</strong>
          {warehouseDesignation(current) !== warehousePlace(current) ? (
            <span className="sidebar-designation">{warehouseDesignation(current)}</span>
          ) : null}
        </div>
      ) : null}
      <nav>
        {LINKS.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
          >
            {link.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
