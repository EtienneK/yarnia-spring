/**
 * Yarnia's audio engine: music + sound effects, all synthesized at runtime
 * with the Web Audio API. No audio assets.
 *
 * Two tunes (chiptune lobby loop, mellow in-game loop) and a set of small
 * SFX stingers. One global on/off switch, persisted by the caller.
 */

type TuneName = "lobby" | "game" | "victory";

interface Tune {
  bpm: number;
  volume: number;
  leadType: OscillatorType;
  leadVol: number;
  lead: string[]; // 8th notes; "." rest, "-" tie
  bass: string[];
  kickSteps: number[]; // steps within a bar (8 steps/bar)
  hatSteps: number[];
  lowpass: number;
}

const FREQ: Record<string, number> = {};
{
  const names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  for (let midi = 36; midi <= 96; midi++) {
    const name = names[midi % 12] + (Math.floor(midi / 12) - 1);
    FREQ[name] = 440 * Math.pow(2, (midi - 69) / 12);
  }
}

// Upbeat lobby loop: 116 BPM, A minor, Am-F-C-G, call-and-answer.
// prettier-ignore
const LOBBY: Tune = {
  bpm: 116, volume: 0.14, leadType: "square", leadVol: 0.32, lowpass: 5200,
  kickSteps: [0, 4], hatSteps: [1, 3, 5, 7],
  lead: [
    "A4", ".", "C5", "E5", "A5", ".", "G5", "E5",   "F5", ".", "A5", "F5", "E5", ".", "C5", "D5",
    "E5", ".", "G5", "E5", "C5", ".", "E5", "D5",   "B4", "D5", "B4", ".", "G4", ".", "B4", "D5",
    "A4", ".", "C5", "E5", "A5", ".", "G5", "E5",   "F5", ".", "A5", "C6", "A5", ".", "F5", "E5",
    "G5", ".", "E5", "C5", "D5", ".", "E5", "F5",   "E5", "D5", "B4", "D5", "A4", ".", ".", ".",
  ],
  bass: [
    "A2", ".", "A2", "A2", ".", "A2", ".", "A2",    "F2", ".", "F2", "F2", ".", "F2", ".", "F2",
    "C3", ".", "C3", "C3", ".", "C3", ".", "C3",    "G2", ".", "G2", "G2", ".", "G2", ".", "B2",
    "A2", ".", "A2", "A2", ".", "A2", ".", "A2",    "F2", ".", "F2", "F2", ".", "F2", ".", "F2",
    "C3", ".", "C3", "C3", ".", "C3", ".", "C3",    "G2", ".", "G2", "B2", ".", "D3", ".", ".",
  ],
};

// Mellow in-game loop: 92 BPM, sparse triangle lead, quiet - stays out of the
// way while people write. A minor with a lift to C/D.
// prettier-ignore
const GAME: Tune = {
  bpm: 92, volume: 0.09, leadType: "triangle", leadVol: 0.4, lowpass: 3200,
  kickSteps: [0], hatSteps: [4],
  lead: [
    "E4", "-", ".", ".", "G4", "-", ".", ".",       "A4", "-", "-", ".", "G4", ".", "E4", "-",
    "D4", "-", ".", ".", "E4", "-", ".", ".",       "C4", "-", "-", "-", ".", ".", ".", ".",
    "E4", "-", ".", ".", "G4", "-", ".", ".",       "A4", "-", "-", ".", "C5", ".", "B4", "-",
    "A4", "-", ".", ".", "G4", "-", ".", ".",       "E4", "-", "-", "-", ".", ".", ".", ".",
  ],
  bass: [
    "A2", ".", ".", ".", ".", ".", "A2", ".",       "F2", ".", ".", ".", ".", ".", "F2", ".",
    "C3", ".", ".", ".", ".", ".", "C3", ".",       "G2", ".", ".", ".", ".", ".", "G2", ".",
    "A2", ".", ".", ".", ".", ".", "A2", ".",       "F2", ".", ".", ".", ".", "F2", ".", ".",
    "D3", ".", ".", ".", ".", ".", "D3", ".",       "E2", ".", ".", ".", ".", ".", "E2", ".",
  ],
};

// Victory theme for the results screen: 132 BPM, C major, four-on-the-floor,
// opens with a rising fanfare so it doubles as the game-over stinger.
// C-G-Am-F | C-F-G-C.
// prettier-ignore
const VICTORY: Tune = {
  bpm: 132, volume: 0.15, leadType: "square", leadVol: 0.3, lowpass: 5600,
  kickSteps: [0, 2, 4, 6], hatSteps: [1, 3, 5, 7],
  lead: [
    // C (rising fanfare)   G
    "C5", ".", "E5", ".", "G5", ".", "C6", "-",   "B5", ".", "G5", ".", "D5", ".", "G5", ".",
    // Am                   F
    "A5", ".", "C6", ".", "A5", ".", "E5", ".",   "F5", ".", "A5", ".", "C6", "-", ".", ".",
    // C                    F
    "E5", ".", "G5", ".", "C6", ".", "G5", ".",   "A5", ".", "F5", ".", "A5", ".", "C6", ".",
    // G                    C (resolve, breathe, loop)
    "B5", ".", "D6", ".", "B5", ".", "G5", "A5",  "C6", "-", "-", "-", ".", "G5", "E5", "C5",
  ],
  bass: [
    "C3", ".", "C3", ".", "G2", ".", "C3", ".",   "G2", ".", "G2", ".", "D3", ".", "G2", ".",
    "A2", ".", "A2", ".", "E3", ".", "A2", ".",   "F2", ".", "F2", ".", "C3", ".", "F2", ".",
    "C3", ".", "C3", ".", "G2", ".", "C3", ".",   "F2", ".", "F2", ".", "C3", ".", "F2", ".",
    "G2", ".", "G2", ".", "D3", ".", "G2", ".",   "C3", ".", "G2", ".", "C3", ".", "C3", ".",
  ],
};

const TUNES: Record<TuneName, Tune> = { lobby: LOBBY, game: GAME, victory: VICTORY };

class SoundEngine {
  private ctx: AudioContext | null = null;
  private musicGain: GainNode | null = null;
  private sfxGain: GainNode | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private tune: Tune | null = null;
  private wantedTune: TuneName | null = null;
  private nextStep = 0;
  private nextTime = 0;
  private enabled = true;
  private resumeListener: (() => void) | null = null;

  setEnabled(on: boolean): void {
    this.enabled = on;
    if (!on) {
      this.stopSequencer();
      void this.ctx?.close();
      this.ctx = null;
      this.musicGain = null;
      this.sfxGain = null;
    } else if (this.wantedTune) {
      this.playMusic(this.wantedTune);
    }
  }

  playMusic(name: TuneName): void {
    this.wantedTune = name;
    if (!this.enabled) return;
    if (this.timer && this.tune === TUNES[name]) return;
    this.stopSequencer();
    this.ensureContext();
    if (!this.ctx || !this.musicGain) return;
    this.tune = TUNES[name];
    this.musicGain.gain.value = this.tune.volume;
    this.nextStep = 0;
    this.nextTime = this.ctx.currentTime + 0.1;
    this.timer = setInterval(() => this.schedule(), 100);
  }

  stopMusic(): void {
    this.wantedTune = null;
    this.stopSequencer();
  }

  // ------------------------------------------------------------------- sfx

  /** Small rising blip - a player joined the lobby. */
  join(): void {
    this.chirp(520, 780, 0.09, "triangle", 0.25);
  }

  /** Confirmation - my submission went in. */
  submit(): void {
    this.arpeggio(["C5", "E5"], 0.07, "triangle", 0.3);
  }

  /** Soft click - vote cast/changed. */
  vote(): void {
    this.chirp(880, 990, 0.05, "square", 0.12);
  }

  /** Countdown tick for the last seconds of a phase. */
  tick(): void {
    this.chirp(1000, 1000, 0.03, "square", 0.08);
  }

  /** Soft pop - a chat message arrived. */
  chat(): void {
    this.chirp(620, 500, 0.06, "sine", 0.16);
  }

  /** A new phase began. */
  phase(): void {
    this.arpeggio(["A4", "E5"], 0.09, "sine", 0.3);
  }

  /** The votes are in - reveal stinger. */
  reveal(): void {
    this.arpeggio(["C5", "E5", "G5", "C6"], 0.08, "triangle", 0.3);
  }

  /** Game over fanfare. */
  fanfare(): void {
    this.arpeggio(["C5", "E5", "G5", "C6", "E6", "G6"], 0.11, "square", 0.22);
    // closing chord
    const t = 0.68;
    for (const n of ["C5", "E5", "G5", "C6"]) {
      this.tone(FREQ[n], t, 0.9, "triangle", 0.16);
    }
  }

  // -------------------------------------------------------------- internals

  private ensureContext(): void {
    if (this.ctx) return;
    this.ctx = new AudioContext();
    const music = this.ctx.createGain();
    const musicFilter = this.ctx.createBiquadFilter();
    musicFilter.type = "lowpass";
    musicFilter.frequency.value = 5200;
    music.connect(musicFilter).connect(this.ctx.destination);
    this.musicGain = music;
    const sfx = this.ctx.createGain();
    sfx.gain.value = 0.5;
    sfx.connect(this.ctx.destination);
    this.sfxGain = sfx;

    if (this.ctx.state === "suspended") {
      void this.ctx.resume();
      if (!this.resumeListener) {
        this.resumeListener = () => void this.ctx?.resume();
        document.addEventListener("pointerdown", this.resumeListener, { once: true });
      }
    }
  }

  private stopSequencer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.tune = null;
  }

  private schedule(): void {
    if (!this.ctx || !this.tune) return;
    const step = 60 / this.tune.bpm / 2;
    const loop = this.tune.lead.length;
    while (this.nextTime < this.ctx.currentTime + 0.25) {
      this.playStep(this.nextStep % loop, this.nextTime, step);
      this.nextStep++;
      this.nextTime += step;
    }
  }

  private playStep(step: number, t: number, stepLen: number): void {
    const tune = this.tune;
    if (!tune || !this.musicGain) return;
    const lead = tune.lead[step];
    if (lead !== "." && lead !== "-") {
      let len = 1;
      while (tune.lead[(step + len) % tune.lead.length] === "-") len++;
      this.tone(FREQ[lead], 0, stepLen * len * 0.9, tune.leadType, tune.leadVol, this.musicGain, t);
    }
    const bass = tune.bass[step];
    if (bass !== "." && bass !== "-") {
      this.tone(FREQ[bass], 0, stepLen * 0.85, "triangle", 0.5, this.musicGain, t);
    }
    const inBar = step % 8;
    if (tune.kickSteps.includes(inBar)) this.kick(t);
    if (tune.hatSteps.includes(inBar)) this.hat(t);
  }

  /** delay/absolute: pass either delay-from-now (delay) or an absolute time (at). */
  private tone(
    freq: number,
    delay: number,
    len: number,
    type: OscillatorType,
    vol: number,
    dest?: GainNode | null,
    at?: number,
  ): void {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    const out = dest ?? this.sfxGain;
    if (!out) return;
    const t = at ?? this.ctx.currentTime + delay;
    const osc = this.ctx.createOscillator();
    osc.type = type;
    osc.frequency.value = freq;
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(vol, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + len);
    osc.connect(gain).connect(out);
    osc.start(t);
    osc.stop(t + len + 0.02);
  }

  private chirp(from: number, to: number, len: number, type: OscillatorType, vol: number): void {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx || !this.sfxGain) return;
    const t = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    osc.type = type;
    osc.frequency.setValueAtTime(from, t);
    osc.frequency.exponentialRampToValueAtTime(Math.max(to, 1), t + len);
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(vol, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + len);
    osc.connect(gain).connect(this.sfxGain);
    osc.start(t);
    osc.stop(t + len + 0.02);
  }

  private arpeggio(notes: string[], noteLen: number, type: OscillatorType, vol: number): void {
    notes.forEach((n, i) => this.tone(FREQ[n], i * noteLen, noteLen * 2.2, type, vol));
  }

  private kick(t: number): void {
    if (!this.ctx || !this.musicGain) return;
    const osc = this.ctx.createOscillator();
    osc.type = "sine";
    osc.frequency.setValueAtTime(140, t);
    osc.frequency.exponentialRampToValueAtTime(45, t + 0.1);
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.6, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.12);
    osc.connect(gain).connect(this.musicGain);
    osc.start(t);
    osc.stop(t + 0.13);
  }

  private hat(t: number): void {
    if (!this.ctx || !this.musicGain) return;
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
    src.connect(highpass).connect(gain).connect(this.musicGain);
    src.start(t);
  }
}

/** The one shared engine. */
export const sound = new SoundEngine();
