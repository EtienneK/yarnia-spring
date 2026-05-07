import { useState, useEffect, type Dispatch, type SetStateAction } from 'react'

function getStorageValue<S>(key: string, initialState: S): S {
  const saved = localStorage.getItem(key)
  if (!saved) {
    return initialState
  }
  try {
    const initial = JSON.parse(saved) as S
    return initial
  } catch (e) {
    console.error(e)
    return initialState
  }
}

export function useLocalStorageState<S> (key: string, initialState: S): [S, Dispatch<SetStateAction<S>>, () => void] {
  const [value, setValue] = useState(() => {
    return getStorageValue(key, initialState)
  })

  useEffect(() => {
    // storing input name
    localStorage.setItem(key, JSON.stringify(value))
  }, [key, value])

  return [value, setValue, () => localStorage.removeItem(key)]
};
