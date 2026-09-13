import { useEffect, useState } from 'react'
import { getVehicles, getRoutes, assignTruck } from '../services/api.js'
import StatusBadge from '../components/StatusBadge.jsx'
import AssignModal from '../components/AssignModal.jsx'
import CreateResourceModal from '../components/CreateResourceModal.jsx'

const EDITABLE = ['DRAFT', 'READY', 'BLOCKED']

export default function Vehicles({ notify }) {
  const [vehicles, setVehicles] = useState([])
  const [routes, setRoutes] = useState([])
  const [error, setError] = useState('')
  const [picker, setPicker] = useState(null) 
  const [showCreate, setShowCreate] = useState(false)

  async function load() {
    try {
      setError('')
      const [vehicleData, routeData] = await Promise.all([getVehicles(), getRoutes()])
      setVehicles(vehicleData)
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
      await assignTruck(routeId, picker.truckId)
      notify('Truck assigned.', 'success')
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
          <h2 className="page-title">Vehicles</h2>
          <p className="page-sub">Only AVAILABLE trucks can be assigned to a route.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Add Truck
        </button>
      </div>

      {error ? <div className="error-text">{error}</div> : null}

      <div className="panel">
        {vehicles.length === 0 ? (
          <div className="empty">No trucks yet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Truck ID</th>
                <th>Type</th>
                <th>Capacity</th>
                <th>Status</th>
                <th>Assigned Route</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {vehicles.map((vehicle) => (
                <tr key={vehicle.truckId}>
                  <td>{vehicle.truckId}</td>
                  <td>{vehicle.type}</td>
                  <td>{vehicle.capacity.toLocaleString()} kg</td>
                  <td>
                    <StatusBadge status={vehicle.status} />
                  </td>
                  <td>{vehicle.routeId || '-'}</td>
                  <td>
                    {!vehicle.routeId && vehicle.status === 'AVAILABLE' ? (
                      <button className="btn btn-sm" onClick={() => setPicker(vehicle)}>
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
          title={`Assign ${picker.truckId} to a route`}
          label="Route"
          emptyText="No route can accept a truck right now."
          options={assignableRoutes.map((route) => ({
            value: route.routeId,
            label: `${route.routeId} - ${route.origin} \u2192 ${route.destination} (${route.currentWeight.toLocaleString()} kg loaded)`,
          }))}
          onConfirm={confirm}
          onClose={() => setPicker(null)}
        />
      ) : null}

      {showCreate ? (
        <CreateResourceModal
          kind="vehicle"
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
