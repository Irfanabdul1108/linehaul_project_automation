import { useState } from 'react'
import Modal from './Modal.jsx'
import { createVehicle, createDriver } from '../services/api.js'

const TRUCK = { truckId: '', type: 'Truck', capacity: 10000, status: 'AVAILABLE' }
const PERSON = { driverId: '', name: '', status: 'AVAILABLE' }

export default function CreateResourceModal({ kind, onClose, onCreated, notify }) {
  const isTruck = kind === 'vehicle'
  const [form, setForm] = useState(isTruck ? TRUCK : PERSON)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  async function submit(event) {
    event.preventDefault()
    setError('')
    setSaving(true)
    try {
      const payload = isTruck ? { ...form, capacity: Number(form.capacity) || 0 } : form
      const created = isTruck ? await createVehicle(payload) : await createDriver(payload)
      notify(isTruck ? `Truck ${created.truckId} added.` : `Driver ${created.driverId} added.`, 'success')
      onCreated(created)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={isTruck ? 'Add Truck' : 'Add Driver'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" form="resource-form" disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </button>
        </>
      }
    >
      {error ? <div className="error-text">{error}</div> : null}
      <form id="resource-form" onSubmit={submit}>
        <div className="form-grid">
          <div className="field">
            <label>{isTruck ? 'Truck ID' : 'Driver ID'}</label>
            <input
              value={isTruck ? form.truckId : form.driverId}
              onChange={set(isTruck ? 'truckId' : 'driverId')}
              placeholder={isTruck ? 'T-600' : 'D-200'}
              required
            />
          </div>
          {isTruck ? (
            <>
              <div className="field">
                <label>Type</label>
                <select value={form.type} onChange={set('type')}>
                  <option>Truck</option>
                  <option>Tractor</option>
                  <option>Van</option>
                </select>
              </div>
              <div className="field">
                <label>Capacity (kg)</label>
                <input type="number" min="1" value={form.capacity} onChange={set('capacity')} required />
              </div>
            </>
          ) : (
            <div className="field">
              <label>Name</label>
              <input value={form.name} onChange={set('name')} placeholder="John Smith" required />
            </div>
          )}
          <div className="field">
            <label>Status</label>
            <select value={form.status} onChange={set('status')}>
              <option value="AVAILABLE">Available</option>
              <option value="MAINTENANCE">Maintenance</option>
            </select>
          </div>
        </div>
      </form>
    </Modal>
  )
}
