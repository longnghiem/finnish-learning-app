import {API_BASE_URL} from "./config.ts";
import type {ErrorResponse} from "../types";

/**
 * Mirrors class AuthResponse in backend.
 * The token is a JWT issued by backend. Token must be attached to subsequent requests
 * as 'Authorization: Bearer <token>'
 */
export interface AuthResponse {
  token: string
  userId: number
  username: string
  role: string
}

/**
 * Calls 'POST /api/auth/register'
 * On success the backend returns 201 with an [AuthResponse] -
 * the user is immediately logged in.
 */
export async function registerUser(
  username: string,
  password: string,
): Promise<AuthResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/auth/register`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    }
  )

  if (!response.ok) {
    const err: ErrorResponse = await response.json()
    throw new Error(err.message)
  }

  return response.json()
}

export async function loginUser(
  username: string,
  password: string,
): Promise<AuthResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/auth/login`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    }
  )

  if (!response.ok) {
    const err: ErrorResponse = await response.json()
    throw new Error(err.message)
  }

  return response.json()
}