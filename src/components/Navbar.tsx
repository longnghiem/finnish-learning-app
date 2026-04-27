import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.ts'
import { useTheme } from '../theme/useTheme'
import { useLang } from '../lang'

const pillClassName = (active: boolean) =>
  `rounded-lg px-4 py-[7px] text-[0.8rem] font-bold text-nav-text border cursor-pointer font-[inherit] transition-[background,border-color] duration-150 ${
    active
      ? 'bg-[rgba(255,189,89,0.22)] border-amber'
      : 'bg-[rgba(255,255,255,0.08)] border-[rgba(255,255,255,0.12)]'
  }`

export function Navbar() {
  const { isLoggedIn, logout } = useAuth()
  const { dark, toggleDark } = useTheme()
  const { lang, L, toggleLang } = useLang()
  const navigate = useNavigate()
  const location = useLocation()

  const isAdmin = location.pathname === '/admin'

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <nav className="flex items-center justify-between bg-nav-bg px-7 py-3.5 shadow-[0_2px_12px_rgb(0_0_0/0.2)] sticky top-0 z-20">
      <Link to="/" className="text-[1.2rem] font-extrabold text-nav-text no-underline tracking-[-0.3px]">
        {L.appName}
      </Link>

      <div className="flex items-center gap-2">
        {/* Language toggle */}
        <button onClick={toggleLang} className={pillClassName(false)} title="Toggle language">
          {lang === 'en' ? '🇫🇮 Suomi' : '🇬🇧 English'}
        </button>

        {/* Dark mode toggle */}
        <button onClick={toggleDark} className={pillClassName(false)} title="Toggle dark mode">
          {dark ? '☀️' : '🌙'}
        </button>

        {/* Admin link - manage cards */}
        {isLoggedIn && (
          <Link to="/admin" className={`${pillClassName(isAdmin)} no-underline inline-block`}>
            {L.admin}
          </Link>
        )}

        {/* Login / Logout */}
        {isLoggedIn ? (
          <button
            onClick={handleLogout}
            className="rounded-lg bg-red text-white px-4 py-1.75 text-[0.8rem] font-bold border-none cursor-pointer font-[inherit] transition-colors duration-150 hover:bg-red-hover"
          >
            {L.logout}
          </button>
        ) : (
          <Link
            to="/login"
            className="rounded-lg bg-amber text-nav-btn-text px-4 py-1.75 text-[0.8rem] font-bold no-underline inline-block transition-colors duration-150 hover:bg-amber-hover"
          >
            {L.login}
          </Link>
        )}
      </div>
    </nav>
  )
}
