import { useState } from 'react'
import UserSelector from './components/UserSelector.jsx'
import PortfolioTable from './components/PortfolioTable.jsx'
import OrderForm from './components/OrderForm.jsx'
import AskBox from './components/AskBox.jsx'

export default function App() {
  const [userId, setUserId] = useState(1)
  // Bumped after a FILLED order so PortfolioTable refetches; plain lifted state, no state library needed
  const [refreshKey, setRefreshKey] = useState(0)

  return (
    <main>
      <header>
        <h1>FolioTrack</h1>
        <UserSelector userId={userId} onChange={setUserId} />
      </header>
      <PortfolioTable userId={userId} refreshKey={refreshKey} />
      <div className="columns">
        {/* key={userId} resets each form's state when the user changes */}
        <OrderForm key={`order-${userId}`} userId={userId} onFilled={() => setRefreshKey((k) => k + 1)} />
        <AskBox key={`ask-${userId}`} userId={userId} />
      </div>
    </main>
  )
}
