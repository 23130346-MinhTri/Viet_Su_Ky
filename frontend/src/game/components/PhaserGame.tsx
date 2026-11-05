import { useEffect, useRef } from 'react'
import Phaser from 'phaser'
import gameConfig from '../config/gameConfig'

function PhaserGame() {
  const parentRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const parent = parentRef.current
    if (!parent) {
      return
    }

    const game = new Phaser.Game({
      ...gameConfig,
      parent,
    })

    return () => {
      game.destroy(true)
    }
  }, [])

  return (
    <div
      ref={parentRef}
      className="phaser-container"
      aria-label="Khung game demo Phaser"
    />
  )
}

export default PhaserGame
