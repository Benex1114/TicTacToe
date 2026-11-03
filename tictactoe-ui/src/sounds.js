/**
 * Sound effects, made in the browser with the Web Audio API (no audio files to download).
 *
 * Each sound is built from two simple pieces:
 *   tone()    a short note that fades out (optionally sliding in pitch)
 *   scratch() a burst of filtered noise that sounds like a pen stroke
 *
 * Browsers only allow sound after the person has tapped or clicked something,
 * so the audio engine starts on the first tap anywhere on the page.
 *
 * Usage: playSound("x"), playSound("win"), setMuted(true) ...
 */

const MUTED_KEY = "tictactoe-muted";

let ctx = null;
let master = null;
let noise = null;
let muted = false;
let unlocked = false; // true after the first tap, when the browser allows sound

try {
  muted = localStorage.getItem(MUTED_KEY) === "1";
} catch {
  // storage not available: sound starts on
}

export const isMuted = () => muted;

export function setMuted(value) {
  muted = value;
  try {
    localStorage.setItem(MUTED_KEY, value ? "1" : "0");
  } catch {
    // not remembered, but still works for this visit
  }
}

// Create the audio engine (or wake it up). Safe to call many times.
function audio() {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  if (!AudioContextClass) return null; // very old browser: no sound, everything else works
  if (!ctx) {
    ctx = new AudioContextClass();
    master = ctx.createGain();
    master.gain.value = 0.55;
    master.connect(ctx.destination);
    // One second of white noise, reused by every pen-stroke sound
    noise = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
    const data = noise.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  }
  if (ctx.state === "suspended") ctx.resume();
  return ctx;
}

// Start the engine on the first tap, key press or click (browsers block sound before that)
if (typeof window !== "undefined") {
  const unlock = () => {
    unlocked = true;
    audio();
    window.removeEventListener("pointerdown", unlock);
    window.removeEventListener("keydown", unlock);
  };
  window.addEventListener("pointerdown", unlock);
  window.addEventListener("keydown", unlock);
}

// A note: starts at `from` Hz (sliding to `to` Hz), fades out over `length` seconds
function tone(at, { from, to = from, length, type = "sine", volume = 0.3 }) {
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(from, at);
  if (to !== from) osc.frequency.exponentialRampToValueAtTime(to, at + length);
  gain.gain.setValueAtTime(0.0001, at);
  gain.gain.exponentialRampToValueAtTime(volume, at + 0.01); // quick start, no click
  gain.gain.exponentialRampToValueAtTime(0.0001, at + length); // smooth fade out
  osc.connect(gain).connect(master);
  osc.start(at);
  osc.stop(at + length + 0.05);
}

// A pen stroke: noise through a band-pass filter whose pitch slides, so it "moves" like a stroke
function scratch(at, { length, from, to, volume = 0.18 }) {
  const source = ctx.createBufferSource();
  source.buffer = noise;
  const filter = ctx.createBiquadFilter();
  filter.type = "bandpass";
  filter.Q.value = 1.2;
  filter.frequency.setValueAtTime(from, at);
  filter.frequency.exponentialRampToValueAtTime(to, at + length);
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.0001, at);
  gain.gain.exponentialRampToValueAtTime(volume, at + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, at + length);
  source.connect(filter).connect(gain).connect(master);
  source.start(at, Math.random() * 0.5); // a different slice of noise each time
  source.stop(at + length + 0.05);
}

// Note names to frequencies, for the short tunes
const NOTE = { G3: 196, C4: 261.63, Eb4: 311.13, E4: 329.63, G4: 392, A4: 440, C5: 523.25, E5: 659.25, G5: 783.99, A5: 880, C6: 1046.5, D6: 1174.66, E6: 1318.5 };

const SOUNDS = {
  // X: two quick strokes, timed with the two lines being drawn on the board
  x: (t) => {
    scratch(t, { length: 0.14, from: 2600, to: 1500 });
    scratch(t + 0.13, { length: 0.14, from: 2400, to: 1400 });
    tone(t, { from: 740, to: 600, length: 0.12, type: "triangle", volume: 0.12 });
  },
  // O: one longer, rounder stroke, timed with the circle
  o: (t) => {
    scratch(t, { length: 0.3, from: 1100, to: 2000, volume: 0.15 });
    tone(t, { from: 440, to: 520, length: 0.22, volume: 0.14 });
  },
  // You won (or someone won on a shared screen): a rising chime
  win: (t) => {
    [NOTE.C5, NOTE.E5, NOTE.G5, NOTE.C6].forEach((f, i) => tone(t + i * 0.09, { from: f, length: 0.5, type: "triangle", volume: 0.22 }));
    tone(t + 0.27, { from: NOTE.E6, length: 0.7, volume: 0.06 }); // a little sparkle on top
  },
  // The computer or your friend won: a soft falling phrase
  lose: (t) => {
    [NOTE.G4, NOTE.Eb4, NOTE.C4].forEach((f, i) => tone(t + i * 0.16, { from: f, length: 0.4, volume: 0.22 }));
  },
  // A draw: two even notes
  draw: (t) => {
    tone(t, { from: NOTE.E4, length: 0.3, type: "triangle", volume: 0.18 });
    tone(t + 0.15, { from: NOTE.E4, length: 0.35, type: "triangle", volume: 0.15 });
  },
  // Your friend joined the room
  join: (t) => {
    tone(t, { from: NOTE.A5, length: 0.25, volume: 0.2 });
    tone(t + 0.12, { from: NOTE.E6, length: 0.4, volume: 0.18 });
  },
  // Your friend wants a rematch
  nudge: (t) => {
    tone(t, { from: NOTE.D6, length: 0.18, volume: 0.15 });
    tone(t + 0.1, { from: NOTE.D6, length: 0.25, volume: 0.12 });
  },
  // Your friend left
  left: (t) => {
    tone(t, { from: NOTE.C4, length: 0.3, volume: 0.2 });
    tone(t + 0.18, { from: NOTE.G3, length: 0.45, volume: 0.2 });
  },
  // Menu taps: a very short, quiet tick
  tap: (t) => {
    tone(t, { from: 1400, to: 900, length: 0.05, type: "triangle", volume: 0.08 });
  },
};

export function playSound(name, delaySeconds = 0) {
  if (muted || !SOUNDS[name]) return;
  try {
    // Before the first tap, stay quiet (sounds would otherwise pile up and all play at once later).
    // Right after it the engine may still be starting; sounds scheduled now play as soon as it's ready.
    if (!unlocked || !audio()) return;
    SOUNDS[name](ctx.currentTime + 0.01 + delaySeconds);
  } catch {
    // sound is a nice-to-have: never let it break the game
  }
}
