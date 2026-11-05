import Phaser from 'phaser'

class BootScene extends Phaser.Scene {
  constructor() {
    super('BootScene')
  }

  create() {
    this.add
      .text(
        this.scale.width / 2,
        this.scale.height / 2,
        'VIỆT SỬ KÝ\nPhaser đã sẵn sàng',
        {
          align: 'center',
          color: '#f4ead3',
          fontFamily: 'sans-serif',
          fontSize: '24px',
        },
      )
      .setOrigin(0.5)
  }
}

export default BootScene
