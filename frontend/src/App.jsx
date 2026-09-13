import { useEffect, useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Sidebar from './components/Sidebar.jsx'
import Header from './components/Header.jsx'
import Toasts, { useToasts } from './components/Toasts.jsx'
import ChatAssistant from './components/ChatAssistant.jsx'
import SelectWarehouse from './pages/SelectWarehouse.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Orders from './pages/Orders.jsx'
import RoutesPage from './pages/Routes.jsx'
import Vehicles from './pages/Vehicles.jsx'
import Drivers from './pages/Drivers.jsx'
import { loadWarehouses, setActiveWarehouse, useActiveWarehouseId } from './services/warehouse.js'

export default function App() {
  const { toasts, notify } = useToasts()
  const warehouseId = useActiveWarehouseId()
  const [checking, setChecking] = useState(true)

  // A warehouse remembered from an earlier visit is only kept while it still exists in the database.
  useEffect(() => {
    let alive = true
    loadWarehouses({ force: true })
      .then((rows) => {
        if (!alive) return
        if (warehouseId && !rows.some((row) => row.warehouseId === warehouseId)) {
          setActiveWarehouse('')
        }
      })
      .catch(() => {
        // No API yet: the picker shows its own error, so nothing else is needed here.
      })
      .finally(() => {
        if (alive) setChecking(false)
      })
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (checking && !warehouseId) {
    return (
      <div className="warehouse-gate">
        <div className="warehouse-gate-inner">
          <div className="empty">Looking for the warehouses in your Linehaul database...</div>
        </div>
      </div>
    )
  }

  if (!warehouseId) {
    return (
      <>
        <SelectWarehouse notify={notify} />
        <Toasts toasts={toasts} />
      </>
    )
  }

  return (
    <div className="app">
      <Sidebar />
      <div className="main">
        <Header onChangeWarehouse={() => setActiveWarehouse('')} />
        <div className="page">
          <Routes>
            <Route path="/" element={<Dashboard notify={notify} />} />
            <Route path="/orders" element={<Orders notify={notify} />} />
            <Route path="/routes" element={<RoutesPage notify={notify} />} />
            <Route path="/vehicles" element={<Vehicles notify={notify} />} />
            <Route path="/drivers" element={<Drivers notify={notify} />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </div>
      <ChatAssistant />
      <Toasts toasts={toasts} />
    </div>
  )
}
