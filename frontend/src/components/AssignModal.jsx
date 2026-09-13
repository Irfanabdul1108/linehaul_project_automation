import { useState } from 'react'
import Modal from './Modal.jsx'

export default function AssignModal({ title, label, options, emptyText, onConfirm, onClose }) {
  const [value, setValue] = useState(options[0]?.value || '')
  const available = options.length > 0

  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            disabled={!value}
            onClick={() => onConfirm(value)}
          >
            Assign
          </button>
        </>
      }
    >
      {!available ? (
        <div className="error-text">{emptyText}</div>
      ) : (
        <div className="field">
          <label>{label}</label>
          <select value={value} onChange={(event) => setValue(event.target.value)}>
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}
    </Modal>
  )
}
