import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'

// Minimal CSS reset.
const resetStyle = document.createElement('style')
resetStyle.textContent = `
  * { box-sizing: border-box; }
  html, body, #root { height: 100%; }
  body {
    margin: 0;
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    color: #111;
    background: #fff;
  }
  button { font-family: inherit; }
  input { font-family: inherit; }
`
document.head.appendChild(resetStyle)

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
