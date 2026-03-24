import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { init as initPlausible } from '@plausible-analytics/tracker'
import './index.css'
import App from './App.tsx'

// Initialise Plausible Analytics — autoCapturePageviews (default: true)
// listens to History API changes, so SPA route transitions are tracked
// automatically without additional wiring.
initPlausible({ domain: 'jargoyle.com' })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
