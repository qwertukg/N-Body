# Gravity simulator based on Particle Mesh Algorithm 

Solving the Poisson equation using the Fast Fourier Transform (FFT).

### `config.json` parameters

- `count` — number of particles in the simulation
- `screenW`, `screenH` — screen width and height for visualization
- `gridSize` — size of the 3D grid used for solving the Poisson equation via FFT
- `worldSize` — physical size of the simulated world
- `g` — gravitational constant
- `dt` — simulation time step
- `minRadius`, `maxRadius` — minimum and maximum particle radius
- `massFrom`, `massUntil` — range of possible particle masses
- `isFullScreen` — run in fullscreen mode
- `isDropOutOfBounds` — whether to remove particles that go out of bounds
- `params` — object for additional parameters (can be empty)

### Control

#### Create figures

- `1` — generate disk
- `2` — Möbius strip
- `3` — sphere
- `4` — cube
- `5` — cylinder
- `6` — cone
- `7` — torus
- `8` — hemisphere
- `9` — double cone
- `0` — ridged cylinder
- `Q` — pyramid
- `W` — sinusoidal torus
- `E` — random clusters
- `R` — noisy sphere
- `T` — random orbits
- `Y` — orbital disk

#### Additional control

- `X` - with/without center BlackHole (with BlackHole it will be in cam focus, without cam looks at the sim center)
- `Z` - with/without orbital velocity of particles 
- `Space` - set random orbit size for a current figure
- `Space` - set random orbit size for a current figure
- `Up` - increase time speed
- `Down` - decrease time speed
- `Right` - focus on next particle (with cam focus only)
- `Left` - focus on previous particle
- `Esc` - exit

