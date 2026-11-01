// The seed data has exactly three users. There's no auth in this demo, so picking a user is just choosing an id.
const USER_IDS = [1, 2, 3]

export default function UserSelector({ userId, onChange }) {
  return (
    <label className="user-selector">
      User{' '}
      <select value={userId} onChange={(e) => onChange(Number(e.target.value))}>
        {USER_IDS.map((id) => (
          <option key={id} value={id}>
            User {id}
          </option>
        ))}
      </select>
    </label>
  )
}
