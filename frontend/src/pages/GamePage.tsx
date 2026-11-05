import PhaserGame from '../game/components/PhaserGame'

function GamePage() {
  return (
    <section className="game-page">
      <div>
        <p className="eyebrow">Tích hợp kỹ thuật</p>
        <h1>Game demo</h1>
        <p className="muted">
          Scene kiểm tra vòng đời Phaser trong React; chưa có gameplay hoặc nội
          dung lịch sử.
        </p>
      </div>
      <PhaserGame />
    </section>
  )
}

export default GamePage
