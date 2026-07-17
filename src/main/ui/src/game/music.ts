/**
 * Lobby music: a small chiptune loop synthesized with the Web Audio API.
 * No audio assets - the tune is sequenced and generated at runtime.
 *
 * 116 BPM, A minor, Am-F-C-G, 8 bars. Square lead, triangle bass,
 * noise hats and a sine-drop kick, all through a gentle lowpass.
 */

const BPM = 116;
const STEPS_PER_BAR = 8; // 8th notes
const BARS = 8;
const STEP = 60 / BPM / 2; // seconds per 8th
const LOOP_STEPS = STEPS_PER_BAR * BARS;

const FREQ: Record<string, number> = {};
{
  const names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  for (let midi = 36; midi <= 96; midi++) {
    const name = names[midi % 12] + (Math.floor(midi / 12) - 1);
    FREQ[name] = 440 * Math.pow(2, (midi - 69) / 12);
  }
}

// "." = rest, "-" = tie (let previous note keep ringing)
// Melody: a bouncy call-and-answer over Am | F | C | G, twice with a varied answer.
// prettier-ignore
const LEAD = [
  // Am              F
  "A4", ".", "C5", "E5", "A5", ".", "G5", "E5",   "F5", ".", "A5", "F5", "E5", ".", "C5", "D5",
  // C               G
  "E5", ".", "G5", "E5", "C5", ".", "E5", "D5",   "B4", "D5", "B4", ".", "G4", ".", "B4", "D5",
  // Am              F
  "A4", ".", "C5", "E5", "A5", ".", "G5", "E5",   "F5", ".", "A5", "C6", "A5", ".", "F5", "E5",
  // C               G                              (answer resolves home)
  "G5", ".", "E5", "C5", "D5", ".", "E5", "F5",   "E5", "D5", "B4", "D5", "A4", ".", ".", ".",
];
// prettier-ignore
const BASS = [
  "A2", ".", "A2", "A2", ".", "A2", ".", "A2",    "F2", ".", "F2", "F2", ".", "F2", ".", "F2",
  "C3", ".", "C3", "C3", ".", "C3", ".", "C3",    "G2", ".", "G2", "G2", ".", "G2", ".", "B2",
  "A2", ".", "A2", "A2", ".", "A2", ".", "A2",    "F2", ".", "F2", "F2", ".", "F2", ".", "F2",
  "C3", ".", "C3", "C3", ".", "C3", ".", "C3",    "G2", ".", "G2", "B2", ".", "D3", ".", ".",
];

export class LobbyMusic {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private nextStep = 0;
  private nextTime = 0;
  private resumeListener: (() => void) | null = null;

  get playing(): boolean {
    return this.timer !== null;
  }

  start(): void {
    if (this.timer) return;
    if (!this.ctx) {
      this.ctx = new AudioContext();
      const lowpass = this.ctx.createBiquadFilter();
      lowpass.type = "lowpass";
      lowpass.frequency.value = 5200;
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.14;
      this.master.connect(lowpass).connect(this.ctx.destination);
    }
    // Browsers may keep a context suspended until a user gesture.
    if (this.ctx.state === "suspended") {
      void this.ctx.resume();
      if (!this.resumeListener) {
        this.resumeListener = () => void this.ctx?.resume();
        document.addEventListener("pointerdown", this.resumeListener, { once: true });
      }
    }
    this.nextStep = 0;
    this.nextTime = this.ctx.currentTime + 0.1;
    this.timer = setInterval(() => this.schedule(), 100);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.resumeListener) {
      document.removeEventListener("pointerdown", this.resumeListener);
      this.resumeListener = null;
    }
    void this.ctx?.close();
    this.ctx = null;
    this.master = null;
  }

  private schedule(): void {
    if (!this.ctx || !this.master) return;
    // Schedule everything due in the next 250ms.
    while (this.nextTime < this.ctx.currentTime + 0.25) {
      this.playStep(this.nextStep % LOOP_STEPS, this.nextTime);
      this.nextStep++;
      this.nextTime += STEP;
    }
  }

  private playStep(step: number, t: number): void {
    const lead = LEAD[step];
    if (lead !== "." && lead !== "-") {
      // Tied notes ring longer.
      let len = 1;
      while (LEAD[(step + len) % LOOP_STEPS] === "-") len++;
      this.note("square", FREQ[lead], t, STEP * len * 0.9, 0.32);
    }
    const bass = BASS[step];
    if (bass !== "." && bass !== "-") {
      this.note("triangle", FREQ[bass], t, STEP * 0.85, 0.5);
    }
    const inBar = step % STEPS_PER_BAR;
    if (inBar === 0 || inBar === 4) this.kick(t);
    if (inBar % 2 === 1) this.hat(t);
  }

  private note(type: OscillatorType, freq: number, t: number, len: number, vol: number): void {
    if (!this.ctx || !this.master) return;
    const osc = this.ctx.createOscillator();
    osc.type = type;
    osc.frequency.value = freq;
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(vol, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + len);
    osc.connect(gain).connect(this.master);
    osc.start(t);
    osc.stop(t + len + 0.02);
  }

  private kick(t: number): void {
    if (!this.ctx || !this.master) return;
    const osc = this.ctx.createOscillator();
    osc.type = "sine";
    osc.frequency.setValueAtTime(140, t);
    osc.frequency.exponentialRampToValueAtTime(45, t + 0.1);
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.6, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.12);
    osc.connect(gain).connect(this.master);
    osc.start(t);
    osc.stop(t + 0.13);
  }

  private hat(t: number): void {
    if (!this.ctx || !this.master) return;
    const len = 0.04;
    const buffer = this.ctx.createBuffer(1, this.ctx.sampleRate * len, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
    const src = this.ctx.createBufferSource();
    src.buffer = buffer;
    const highpass = this.ctx.createBiquadFilter();
    highpass.type = "highpass";
    highpass.frequency.value = 6500;
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.12, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + len);
    src.connect(highpass).connect(gain).connect(this.master);
    src.start(t);
  }
}
