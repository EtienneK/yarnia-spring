import { useEffect, useRef, useState } from 'react'
import Hero from '../components/Hero.tsx'
import { PARTY_CODE_LENGTH } from './rules.ts'

export interface PartyMatchInfo {
  partyId: string;
  playerId: string;
  joinCode: string;
  joinToken: string;
}

export function Menu({
  onReady,
  onBack,
}: {
  onReady: (info: PartyMatchInfo) => void;
  onBack: () => void;
}) {
  const [joinCode, setJoinCode] = useState('')
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const [error, setError] = useState('')
  const [hostPublic, setHostPublic] = useState(false)
  const [searching, setSearching] = useState(false)
  const searchingRef = useRef(false)

  useEffect(() => {
    return () => {
      searchingRef.current = false
    }
  }, [])

  const createParty = async () => {
    setStatus('loading')
    setError('')
    try {
        const response = await fetch('/api/party/create', {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ publicGame: hostPublic }),
        });

        if (!response.ok) {
            throw new Error('Unknown error: ' + response.status);
        }

        const body = await response.json();
        onReady(body);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setStatus('error')
    }
  }

  // Matchmaking: poll for an open public game until one appears or we cancel.
  const joinPublic = async () => {
    setError('')
    setStatus('idle')
    setSearching(true)
    searchingRef.current = true
    while (searchingRef.current) {
      try {
        const response = await fetch('/api/party/join-public', { method: 'POST' })
        if (response.ok) {
          searchingRef.current = false
          setSearching(false)
          onReady(await response.json())
          return
        }
        if (response.status !== 404) {
          throw new Error('Unknown error: ' + response.status)
        }
      } catch (err) {
        searchingRef.current = false
        setSearching(false)
        setError(err instanceof Error ? err.message : String(err))
        setStatus('error')
        return
      }
      await new Promise((resolve) => setTimeout(resolve, 3000))
    }
  }

  const cancelSearch = () => {
    searchingRef.current = false
    setSearching(false)
  }

  const joinParty = async () => {
    if (!joinCode.trim()) return
    setStatus('loading')
    setError('')
    try {
        const response = await fetch('/api/party/join', {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                joinCode: joinCode.trim().toUpperCase(),
                playerName: localStorage.getItem('preferredName') ?? undefined
            }),
        });

        if (!response.ok) {
            if (response.status === 404) {
                throw new Error('Game not found.')
            }
            throw new Error('Unknown error: ' + response.status);
        }

        const body = await response.json();
        onReady({ ...body, joinCode: joinCode.trim().toUpperCase() });
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setStatus('error')
    }
  }

  return (
    <Hero>
      <h1 className="text-5xl font-bold mb-10">Yarnia</h1>

      <label className="label cursor-pointer justify-center gap-3 mb-2">
        <input
          type="checkbox"
          className="toggle toggle-primary"
          checked={hostPublic}
          onChange={(e) => setHostPublic(e.target.checked)}
          disabled={searching}
        />
        <span>Public game — anyone can join</span>
      </label>
      <button
        className="btn btn-primary btn-block mb-5"
        onClick={() => void createParty()}
        disabled={status === 'loading' || searching}
      >
        {status === 'loading' ? 'Loading...' : hostPublic ? 'Host Public Game' : 'Host Private Game'}
      </button>

      <div className="flex items-center gap-3 mb-5">
        <div className="flex-1 h-px bg-gray-500" />
        <span className="text-gray-500 text-sm">OR</span>
        <div className="flex-1 h-px bg-gray-500" />
      </div>

      {!searching && (
        <button
          className="btn btn-accent btn-block mb-5"
          onClick={() => void joinPublic()}
          disabled={status === 'loading'}
        >
          Join a Public Game
        </button>
      )}
      {searching && (
        <div className="mb-5">
          <p className="text-gray-500 mb-3">
            <span className="loading loading-spinner loading-sm mr-2"></span>
            Searching for an open game...
          </p>
          <button className="btn btn-ghost btn-sm" onClick={cancelSearch}>
            Cancel
          </button>
        </div>
      )}

      <div className="flex items-center gap-3 mb-5">
        <div className="flex-1 h-px bg-gray-500" />
        <span className="text-gray-500 text-sm">OR JOIN WITH CODE</span>
        <div className="flex-1 h-px bg-gray-500" />
      </div>

      <input
        type="text"
        placeholder="Party code"
        value={joinCode}
        onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
        className="input w-full mb-5 font-mono text-xl tracking-widest text-center"
        maxLength={PARTY_CODE_LENGTH}
      />
      <button
        className="btn btn-primary btn-block mb-10"
        onClick={() => void joinParty()}
        disabled={status === 'loading' || searching || joinCode.trim().length !== PARTY_CODE_LENGTH}
      >
        {status === 'loading' ? 'Loading...' : 'Join Party'}
      </button>

      {status === 'error' && (
        <div role="alert" className="alert alert-error mb-5">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="h-6 w-6 shrink-0 stroke-current"
            fill="none"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2"
              d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          <span>{error}</span>
        </div>
      )}

      <div className="flex items-start w-full">
        <button className="btn btn-ghost back-link" onClick={onBack}>
          &larr; Back
        </button>
      </div>
    </Hero>
  )
}
