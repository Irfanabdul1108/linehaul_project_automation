import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'

vi.mock('../services/chatService.js', () => ({
  askAssistant: vi.fn(),
  default: vi.fn(),
}))

import { askAssistant } from '../services/chatService.js'
import ChatAssistant from '../components/ChatAssistant.jsx'

beforeEach(() => {
  vi.clearAllMocks()
  askAssistant.mockResolvedValue('There are currently 5 routes.')
})

describe('Chat assistant window', () => {
  it('starts closed and opens with the floating button', async () => {
    render(<ChatAssistant />)

    const button = screen.getByText('💬 Ask Linehaul')
    expect(screen.queryByText('Linehaul Assistant')).not.toBeInTheDocument()

    fireEvent.click(button)

    expect(screen.getByText('Linehaul Assistant')).toBeInTheDocument()
    expect(screen.queryByText('💬 Ask Linehaul')).not.toBeInTheDocument()
    expect(screen.getByText(/I am the Linehaul Assistant/)).toBeInTheDocument()
  })

  it('shows the suggested questions and sends one when it is clicked', async () => {
    render(<ChatAssistant />)
    fireEvent.click(screen.getByText('💬 Ask Linehaul'))

    for (const question of [
      'How many routes are there?',
      'How many drivers are available?',
      'How many vehicles are available?',
      'How many READY orders are there?',
      'Show blocked routes.',
    ]) {
      expect(screen.getByText(question)).toBeInTheDocument()
    }

    fireEvent.click(screen.getByText('How many routes are there?'))

    await waitFor(() => expect(askAssistant).toHaveBeenCalledWith('How many routes are there?'))
    expect(await screen.findByText('There are currently 5 routes.')).toBeInTheDocument()
  })

  it('sends a typed question with the Send button and with Enter', async () => {
    render(<ChatAssistant />)
    fireEvent.click(screen.getByText('💬 Ask Linehaul'))

    const input = screen.getByLabelText('Your question')
    expect(screen.getByText('Send')).toBeDisabled()

    fireEvent.change(input, { target: { value: 'How many trucks are free?' } })
    fireEvent.click(screen.getByText('Send'))

    await waitFor(() => expect(askAssistant).toHaveBeenCalledWith('How many trucks are free?'))
    expect(input).toHaveValue('')

    askAssistant.mockResolvedValue('There is currently 1 available driver.')
    fireEvent.change(input, { target: { value: 'How many drivers are available?' } })
    fireEvent.submit(input.closest('form'))

    expect(await screen.findByText('There is currently 1 available driver.')).toBeInTheDocument()
    expect(askAssistant).toHaveBeenCalledTimes(2)
  })

  it('shows the loading text while the backend is answering', async () => {
    let finish
    askAssistant.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    render(<ChatAssistant />)
    fireEvent.click(screen.getByText('💬 Ask Linehaul'))

    fireEvent.click(screen.getByText('Show blocked routes.'))

    expect(await screen.findByText('Searching Linehaul data...')).toBeInTheDocument()

    finish('Blocked routes (1):\n• LH-1030 - Dallas to Chicago')

    expect(await screen.findByText(/Blocked routes \(1\)/)).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByText('Searching Linehaul data...')).not.toBeInTheDocument()
    )
  })

  it('closes with the X button and keeps the conversation for the session', async () => {
    render(<ChatAssistant />)
    fireEvent.click(screen.getByText('💬 Ask Linehaul'))
    fireEvent.click(screen.getByText('How many routes are there?'))
    await screen.findByText('There are currently 5 routes.')

    fireEvent.click(screen.getByLabelText('Close'))
    expect(screen.queryByText('Linehaul Assistant')).not.toBeInTheDocument()

    fireEvent.click(screen.getByText('💬 Ask Linehaul'))
    expect(screen.getByText('There are currently 5 routes.')).toBeInTheDocument()
    expect(screen.getByText('How many routes are there?')).toBeInTheDocument()
  })

  it('shows the friendly message that the service returns when the backend is down', async () => {
    askAssistant.mockResolvedValue("Sorry, I couldn't connect to the Linehaul server. Please try again.")
    render(<ChatAssistant />)
    fireEvent.click(screen.getByText('💬 Ask Linehaul'))
    fireEvent.click(screen.getByText('How many routes are there?'))

    expect(
      await screen.findByText("Sorry, I couldn't connect to the Linehaul server. Please try again.")
    ).toBeInTheDocument()
  })
})

describe('chatService', () => {
  let askReal
  const originalFetch = global.fetch

  beforeEach(async () => {
    const actual = await vi.importActual('../services/chatService.js')
    askReal = actual.askAssistant
  })

  afterEach(() => {
    global.fetch = originalFetch
  })

  it('posts the message to /api/chat and returns the answer', async () => {
    global.fetch = vi.fn(async () => ({ ok: true, json: async () => ({ answer: 'There are currently 10 routes.' }) }))

    const answer = await askReal('How many routes are there?')

    expect(global.fetch).toHaveBeenCalledWith('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'How many routes are there?' }),
    })
    expect(answer).toBe('There are currently 10 routes.')
  })

  it('explains that the server cannot be reached', async () => {
    global.fetch = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })

    expect(await askReal('How many routes are there?')).toBe(
      "Sorry, I couldn't connect to the Linehaul server. Please try again."
    )
  })

  it('explains that the data could not be read', async () => {
    global.fetch = vi.fn(async () => ({ ok: false, json: async () => ({}) }))

    expect(await askReal('How many routes are there?')).toBe(
      "Sorry, I couldn't retrieve the current Linehaul data. Please try again."
    )
  })
})
