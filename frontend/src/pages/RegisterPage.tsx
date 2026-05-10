import {useAuth} from "../auth/useAuth.ts";
import {Link, useNavigate} from "react-router-dom";
import {useLang} from "../lang";
import {useEffect, useState, type SubmitEvent} from "react";
import {registerUser} from "../api";
import { inputCls, labelCls, submitButtonCls } from '../styles'

const MIN_PASSWORD_LENGTH = 6

/**
 * Registration page.
 *
 * Renders username + password + confirm-password fields. On submit:
 * 1. Validates client-side (non-empty fields, password length, passwords match).
 * 2. Calls `registerUser()` against `POST /api/auth/register`.
 * 3. On success, stores the returned JWT via `AuthProvider.loginWithToken`
 *    and navigates to `/` (user is immediately logged in).
 * 4. On error, displays the backend message.
 */
export function RegisterPage() {
  const { isLoggedIn, loginWithToken } = useAuth()
  const navigate = useNavigate()
  const { L } = useLang()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (isLoggedIn) {
      navigate('/', { replace: true })
    }
  }, [isLoggedIn, navigate])

  const handleSubmit = async (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError(null)

    if (!username.trim() || !password.trim() || !confirmPassword.trim()) {
      setError(L.fillAllFields)
      return
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(L.passwordTooShort)
      return
    }
    if (password !== confirmPassword) {
      setError(L.passwordsMustMatch)
      return
    }

    setSubmitting(true)
    try {
      const res = await registerUser(username.trim(), password)
      loginWithToken(res.token, res.userId, res.username, res.role)
      navigate('/', { replace: true })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Registration failed.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page-enter flex items-center justify-center px-4 py-20 min-h-[calc(100vh-56px)]">
      <div className="bg-surface rounded-[20px] p-9 w-full max-w-95 shadow-card-hover border border-border">
        <h1 className="m-0 mb-7 text-[1.4rem] font-extrabold text-text-primary text-center">
          {L.register}
        </h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4.5">
          <div>
            <label className={labelCls}>{L.username}</label>
            <input
              type="text"
              value={username}
              onChange={e => {
                setUsername(e.target.value);
                setError(null)
              }}
              className={inputCls}
              placeholder={L.username}
              autoComplete="username"
            />
          </div>
          <div>
            <label className={labelCls}>{L.password}</label>
            <input
              type="password"
              value={password}
              onChange={e => { setPassword(e.target.value); setError(null) }}
              className={inputCls}
              placeholder={L.password}
              autoComplete="new-password"
            />
          </div>
          <div>
            <label className={labelCls}>{L.confirmPassword}</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={e => { setConfirmPassword(e.target.value); setError(null) }}
              className={inputCls}
              placeholder={L.confirmPassword}
              autoComplete="new-password"
            />
          </div>
          {error && (
            <div className="bg-error-bg rounded-lg px-3 py-2.25 text-[0.8rem] text-red">
              {error}
            </div>
          )}
          <button
            type="submit"
            disabled={submitting}
            className={submitButtonCls}
          >
            {L.register}
          </button>
          <p className="text-center text-[0.8rem] text-text-sub mt-2">
            {L.alreadyHaveAccount}{' '}
            <Link to="/login" className="text-accent font-bold no-underline hover:underline">
              {L.login}
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}