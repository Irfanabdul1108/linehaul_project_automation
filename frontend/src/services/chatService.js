import { getActiveWarehouseId, scopedUrl } from './warehouse.js'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const OFFLINE = "Sorry, I couldn't connect to the Linehaul server. Please try again."
const DATA_PROBLEM = "Sorry, I couldn't retrieve the current Linehaul data. Please try again."

export async function askAssistant(message) {
  let response
  const depot = getActiveWarehouseId()
  const url = depot ? scopedUrl('/api/chat') : `${API_BASE_URL}/api/chat`
  const payload = depot ? { message, warehouseId: depot } : { message }

  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  } catch {
    return OFFLINE
  }

  if (!response.ok) return DATA_PROBLEM

  try {
    const data = await response.json()
    return data?.answer || DATA_PROBLEM
  } catch {
    return DATA_PROBLEM
  }
}

export default askAssistant
