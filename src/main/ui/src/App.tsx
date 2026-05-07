import { Menu } from './game/Menu.tsx'
import { Game } from './game/Game.tsx'
import { useLocalStorageState } from './utils/hooks.ts'
import Hero from './components/Hero.tsx'

type Route =
  | { page: 'home' }
  | { page: 'menu' }
  | { page: 'game'; matchInfo: unknown };

function Home({ onClick }: { onClick: () => void }) {
  return (
    <Hero>
      <h1 className="text-5xl font-bold">Yarnia</h1>
      <p className="py-6">Multiplayer storytelling with friends or AI!</p>
      <button onClick={onClick} className="btn btn-primary btn-lg">
        Play Now!
      </button>
    </Hero>
  )
}

export default function App() {
  const [route, setRoute] = useLocalStorageState<Route>('route', {
    page: 'home',
  })

  const goHome = () => setRoute({ page: 'home' })
  const goMenu = () => setRoute({ page: 'menu' })
  const goGame = (matchInfo: unknown) => setRoute({ page: 'game', matchInfo })

  if (route.page === 'home') {
    return <Home onClick={goMenu} />
  }

  if (route.page === 'menu') {
    const props = {
      onReady: (info: unknown) => goGame(info),
      onBack: goHome,
    }

    return <Menu {...props} />
  }

  if (route.page === 'game') {
    const props = {
      matchInfo: route.matchInfo as never,
      onLeave: goHome,
    }

    return <Game {...props} />
  }

  return <h1>404 Not Found</h1>
}
