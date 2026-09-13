import { useEffect, useState } from 'react'
import { getDrivers, getRoutes, assignDriver } from '../services/api.js'
import StatusBadge from '../components/StatusBadge.jsx'
import AssignModal from '../components/AssignModal.jsx'
import CreateResourceModal from '../components/CreateResourceModal.jsx'

const EDITABLE = ['DRAFT', 'READY', 'BLOCKED']

export default function Drivers({ notify }) {
  const [drivers, setDrivers] = useState([])
  const [routes, setRoutes] = useState([])
  const [error, setError] = useState('')
  const [picker, setPicker] = useState(null) 
  const [showCreate, setShowCreate] = useState(false)

  async function load() {
    try {
      setError('')
      const [driverData, routeData] = await Promise.all([getDrivers(), getRoutes()])
      setDrivers(driverData)
      setRoutes(routeData)
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => {
    load()
  }, [])

  async function confirm(routeId) {
    try {
      await assignDriver(routeId, picker.driverId)
      notify('Driver assigned.', 'success')
      setPicker(null)
      load()
    } catch (err) {
      notify(err.message, 'error')
    }
  }

  const assignableRoutes = routes.filter((route) =>
    EDITABLE.includes(String(route.status).toUpperCase())
  )

  return (
    <>
      <div className="page-head">
        <div>
          <h2 className="page-title">Drivers</h2>
          <p className="page-sub">Only AVAILABLE drivers can be assigned to a route.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Add Driver
        </button>
      </div>

      {error ? <div className="error-text">{error}</div> : null}

      <div className="panel">
        {drivers.length === 0 ? (
          <div className="empty">No drivers yet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Driver ID</th>
                <th>Name</th>
                <th>Status</th>
                <th>Assigned Route</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {drivers.map((driver) => (
                <tr key={driver.driverId}>
                  <td>{driver.driverId}</td>
                  <td>{driver.name}</td>
                  <td>
                    <StatusBadge status={driver.status} />
                  </td>
                  <td>{driver.routeId || '-'}</td>
                  <td>
                    {!driver.routeId && driver.status === 'AVAILABLE' ? (
                      <button className="btn btn-sm" onClick={() => setPicker(driver)}>
                        Assign to Route
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {picker ? (
        <AssignModal
          title={`Assign ${picker.name} to a route`}
          label="Route"
          emptyText="No route can accept a driver right now."
          options={assignableRoutes.map((route) => ({
            value: route.routeId,
            label: `${route.routeId} - ${route.origin} \u2192 ${route.destination}`,
          }))}
          onConfirm={confirm}
          onClose={() => setPicker(null)}
        />
      ) : null}

      {showCreate ? (
        <CreateResourceModal
          kind="driver"
          notify={notify}
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false)
            load()
          }}
        />
      ) : null}
    </>
  )
}
